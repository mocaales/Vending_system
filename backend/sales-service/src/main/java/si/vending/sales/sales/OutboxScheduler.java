package si.vending.sales.sales;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Timer for the outbox worker; off when {@code outbox.scheduler-enabled=false}. */
@Component
@ConditionalOnProperty(
    name = "outbox.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxScheduler {

  private final OutboxWorker worker;

  public OutboxScheduler(OutboxWorker worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelayString = "${outbox.poll-interval:PT1S}")
  public void tick() {
    worker.runOnce();
  }
}
