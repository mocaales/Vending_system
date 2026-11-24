package si.vending.sales.sales;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL of the {@code sale_outbox} table; callers own the transaction boundaries. */
@Repository
public class OutboxRepository {

  /** An entry claimed by a worker, with what the decrease request needs. */
  public record Claimed(
      UUID saleId, String traceId, UUID machineId, String slotCode, UUID productId, int quantity) {}

  private final JdbcTemplate jdbc;

  public OutboxRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(UUID saleId, String traceId) {
    jdbc.update("INSERT INTO sale_outbox (sale_id, trace_id) VALUES (?, ?)", saleId, traceId);
  }

  /**
   * Locks up to {@code limit} due open entries (rows locked by another worker are skipped) and
   * pushes their {@code next_attempt_at} to now + lease, so that they stay hidden after the claim
   * transaction commits. Must run in a transaction.
   */
  public List<Claimed> claimDue(int limit, long leaseMillis) {
    List<Claimed> claimed =
        jdbc.query(
            """
            SELECT o.sale_id, o.trace_id, s.machine_id, s.slot_code, s.product_id, s.quantity
            FROM sale_outbox o JOIN sales s ON s.sale_id = o.sale_id
            WHERE o.done_at IS NULL AND o.next_attempt_at <= now()
            ORDER BY o.next_attempt_at, o.sale_id
            LIMIT ?
            FOR UPDATE OF o SKIP LOCKED
            """,
            (rs, i) ->
                new Claimed(
                    rs.getObject("sale_id", UUID.class),
                    rs.getString("trace_id"),
                    rs.getObject("machine_id", UUID.class),
                    rs.getString("slot_code"),
                    rs.getObject("product_id", UUID.class),
                    rs.getInt("quantity")),
            limit);
    for (Claimed c : claimed) {
      jdbc.update(
          "UPDATE sale_outbox SET next_attempt_at = now() + make_interval(secs => ?) "
              + "WHERE sale_id = ?",
          leaseMillis / 1000.0,
          c.saleId());
    }
    return claimed;
  }

  /**
   * Closes the entry and, only if this call closed it, records the Inventory answer on a sale that
   * is still {@code PENDING}; a final state set meanwhile (by an operator or an earlier answer) is
   * never overwritten. Must run in a transaction.
   *
   * @return true if the sale state was updated
   */
  public boolean complete(UUID saleId, String state, String reason) {
    int closed =
        jdbc.update(
            "UPDATE sale_outbox SET done_at = now() WHERE sale_id = ? AND done_at IS NULL", saleId);
    if (closed != 1) {
      return false;
    }
    return jdbc.update(
            "UPDATE sales SET inventory_state = ?, inventory_reason = ? "
                + "WHERE sale_id = ? AND inventory_state = 'PENDING'",
            state,
            reason,
            saleId)
        == 1;
  }

  /**
   * Ends the claim lease of entries that were claimed but not delivered in this pass: they are due
   * again at once, their attempts unchanged.
   */
  public void release(List<UUID> saleIds) {
    if (saleIds.isEmpty()) {
      return;
    }
    jdbc.batchUpdate(
        "UPDATE sale_outbox SET next_attempt_at = now() WHERE sale_id = ? AND done_at IS NULL",
        saleIds.stream().map(id -> new Object[] {id}).toList());
  }

  /** Counts a failed delivery and schedules the next one. */
  public void reschedule(UUID saleId, Instant nextAttemptAt) {
    jdbc.update(
        "UPDATE sale_outbox SET attempts = attempts + 1, next_attempt_at = ? "
            + "WHERE sale_id = ? AND done_at IS NULL",
        Timestamp.from(nextAttemptAt),
        saleId);
  }

  /** Attempts so far (after the claim, before the result is recorded). */
  public int attempts(UUID saleId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT attempts FROM sale_outbox WHERE sale_id = ?", Integer.class, saleId);
    return n == null ? 0 : n;
  }
}
