package si.vending.sales.sales;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import si.vending.sales.sales.InventoryDecreaseClient.Outcome;
import si.vending.sales.sales.OutboxRepository.Claimed;

/**
 * Delivers the stock decrease of accepted sales to Inventory (docs/01 s. 5.1).
 *
 * <p>A pass has three steps, each its own short transaction and none holding a lock during the HTTP
 * call: (1) claim due entries with {@code FOR UPDATE SKIP LOCKED} and move their {@code
 * next_attempt_at} behind a lease, so a parallel pass cannot pick them again; (2) call Inventory;
 * (3) record the result (sale state and entry done, or attempts + 1 and a backoff). If the process
 * dies in between, the entry becomes due again when the lease ends; Inventory answers a repeated
 * {@code saleId} with the stored result, so redelivery is harmless.
 *
 * <p>Each entry is handled on its own: an unexpected failure of one entry reschedules it with the
 * backoff and the pass goes on. A connectivity failure ({@link Outcome.Retry}) stops the pass,
 * because the remaining entries would fail the same way; their lease is released so that they are
 * due again at once with unchanged attempts. When the failures of one entry reach {@code
 * outbox.alert-attempts}, one ERROR line names Inventory, the sale and the attempts; other failures
 * are WARN. Logs carry the status or exception class only, never a body.
 */
@Component("outboxWorker")
public class OutboxWorker {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxWorker.class);

  private final OutboxRepository outbox;
  private final InventoryDecreaseClient inventory;
  private final OutboxProperties properties;
  private final TransactionTemplate tx;

  public OutboxWorker(
      OutboxRepository outbox,
      InventoryDecreaseClient inventory,
      OutboxProperties properties,
      TransactionTemplate tx) {
    this.outbox = outbox;
    this.inventory = inventory;
    this.properties = properties;
    this.tx = tx;
  }

  public void runOnce() {
    List<Claimed> claimed =
        tx.execute(s -> outbox.claimDue(properties.batchSize(), properties.lease().toMillis()));
    if (claimed == null) {
      return;
    }
    for (int i = 0; i < claimed.size(); i++) {
      Claimed entry = claimed.get(i);
      boolean unreachable;
      try {
        unreachable = deliver(entry);
      } catch (RuntimeException e) {
        retryLater(entry, e.getClass().getSimpleName());
        unreachable = false;
      }
      if (unreachable) {
        releaseRest(claimed.subList(i + 1, claimed.size()));
        return;
      }
    }
  }

  /** One delivery and its result; true when Inventory was not reachable. */
  private boolean deliver(Claimed entry) {
    String traceId = entry.traceId() != null ? entry.traceId() : UUID.randomUUID().toString();
    Outcome outcome = inventory.decrease(entry, traceId);
    switch (outcome) {
      case Outcome.Answered a ->
          tx.executeWithoutResult(s -> outbox.complete(entry.saleId(), a.state(), a.reason()));
      case Outcome.Rejected r ->
          tx.executeWithoutResult(
              s -> outbox.complete(entry.saleId(), "NEEDS_RECONCILIATION", null));
      case Outcome.Unreadable u -> retryLater(entry, u.detail());
      case Outcome.Retry r -> retryLater(entry, r.detail());
    }
    return outcome instanceof Outcome.Retry;
  }

  /** Counts the failure and schedules the next attempt; never throws. */
  private void retryLater(Claimed entry, String detail) {
    try {
      Integer failures =
          tx.execute(s -> {
            int n = outbox.attempts(entry.saleId()) + 1;
            outbox.reschedule(entry.saleId(), Instant.now().plus(backoff(n)));
            return n;
          });
      int n = failures == null ? 0 : failures;
      if (n == properties.alertAttempts()) {
        LOG.error(
            "Inventory decrease for sale {} still failing after {} attempts ({})",
            entry.saleId(),
            n,
            detail);
      } else {
        LOG.warn(
            "Inventory decrease for sale {} failed ({}), attempt {}", entry.saleId(), detail, n);
      }
    } catch (RuntimeException e) {
      // the claim lease makes the entry due again
      LOG.warn(
          "Could not reschedule the outbox entry of sale {}: {}",
          entry.saleId(),
          e.getClass().getSimpleName());
    }
  }

  /** Makes the claimed but undelivered entries due again (the lease would otherwise hide them). */
  private void releaseRest(List<Claimed> rest) {
    if (rest.isEmpty()) {
      return;
    }
    try {
      tx.executeWithoutResult(s -> outbox.release(rest.stream().map(Claimed::saleId).toList()));
    } catch (RuntimeException e) {
      // the claim lease makes them due again
      LOG.warn("Could not release outbox entries: {}", e.getClass().getSimpleName());
    }
  }

  /** {@code initial * 2^(failures-1)}, capped. */
  Duration backoff(int failures) {
    Duration max = properties.maxBackoff();
    Duration delay = properties.initialBackoff();
    for (int i = 1; i < failures && delay.compareTo(max) < 0; i++) {
      delay = delay.multipliedBy(2);
    }
    return delay.compareTo(max) > 0 ? max : delay;
  }
}
