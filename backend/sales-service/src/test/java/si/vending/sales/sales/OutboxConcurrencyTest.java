package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import si.vending.sales.sales.support.OutboxTest;

/**
 * #32: two workers (two instances, or a slow pass overlapping the next timer tick) never deliver
 * the same entry twice; the entry is claimed with row locks (FOR UPDATE SKIP LOCKED expected).
 */
class OutboxConcurrencyTest extends OutboxTest {

  @Test
  void twoParallelPassesDeliverEachEntryOnce() throws Exception {
    List<String> saleIds = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      saleIds.add(accept(knownSlot()));
    }
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> passes = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        passes.add(
            pool.submit(() -> {
              start.await();
              runOnce();
              return null;
            }));
      }
      start.countDown();
      for (Future<?> pass : passes) {
        pass.get(60, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    for (String saleId : saleIds) {
      assertThat(INVENTORY.decreaseCallsFor(saleId)).as("deliveries of " + saleId).hasSize(1);
      assertThat(stateOf(saleId)).isEqualTo("APPLIED");
      assertThat(isDone(saleId)).isTrue();
    }
    assertThat(INVENTORY.decreaseCalls()).hasSize(saleIds.size());
  }
}
