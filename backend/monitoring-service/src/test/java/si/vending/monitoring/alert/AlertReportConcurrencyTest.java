package si.vending.monitoring.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.alert.support.AlertApiTest;
import si.vending.monitoring.core.support.Http;

/**
 * #25 invariant: at most one ACTIVE alert per machine and type, also under concurrent reports
 * (docs/01 section 6.2). Several machines per test make a lost race likely to show.
 */
class AlertReportConcurrencyTest extends AlertApiTest {

  private static final int ROUNDS = 8;

  private List<Http.Response> inParallel(List<Callable<Http.Response>> calls) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(calls.size());
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Http.Response>> futures = new ArrayList<>();
      for (Callable<Http.Response> call : calls) {
        futures.add(
            pool.submit(() -> {
              start.await();
              return call.call();
            }));
      }
      start.countDown();
      List<Http.Response> responses = new ArrayList<>();
      for (Future<Http.Response> future : futures) {
        responses.add(future.get(30, TimeUnit.SECONDS));
      }
      return responses;
    } finally {
      pool.shutdownNow();
    }
  }

  private long active(String machine) {
    return alertsOf(machine).stream().filter(a -> "ACTIVE".equals(a.get("status"))).count();
  }

  @Test
  void twoConcurrentReportsWithDifferentVersionsLeaveOneActiveAlert() throws Exception {
    for (int round = 0; round < ROUNDS; round++) {
      String machine = newMachine();
      String product = newProduct();

      List<Http.Response> responses =
          inParallel(
              List.of(
                  () -> report(machine, 1, slot("A1", product, 2, 3)),
                  () -> report(machine, 2, slot("B2", product, 1, 3))));

      assertThat(responses).extracting(Http.Response::status).containsExactlyInAnyOrder(201, 200);
      assertThat(responses.stream().map(r -> r.json().get("outcome")))
          .containsAnyOf("OPENED")
          .hasSize(2);
      List<Map<String, Object>> alerts = alertsOf(machine);
      assertThat(alerts).hasSize(1);
      assertThat(active(machine)).isEqualTo(1);
    }
  }

  @Test
  void identicalConcurrentReportsOpenExactlyOneAlert() throws Exception {
    for (int round = 0; round < ROUNDS; round++) {
      String machine = newMachine();
      String product = newProduct();
      List<Callable<Http.Response>> calls = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        calls.add(() -> report(machine, 1, slot("A1", product, 2, 3)));
      }

      List<Http.Response> responses = inParallel(calls);

      assertThat(responses.stream().filter(r -> r.status() == 201)).hasSize(1);
      assertThat(responses.stream().filter(r -> r.status() == 200)).hasSize(3);
      assertThat(
              responses.stream().filter(r -> r.status() == 200).map(r -> r.json().get("outcome")))
          .containsOnly("IGNORED");
      assertThat(alertsOf(machine)).hasSize(1);
    }
  }

  @Test
  void concurrentEmptyAndNonEmptyReportsNeverLeaveTwoActiveAlerts() throws Exception {
    for (int round = 0; round < ROUNDS; round++) {
      String machine = newMachine();
      String product = newProduct();
      openLowStock(machine, 1, slot("A1", product, 2, 3));

      inParallel(
          List.of(
              () -> report(machine, 2),
              () -> report(machine, 3, slot("B2", product, 1, 3)),
              () -> report(machine, 4, slot("C3", product, 0, 3))));

      assertThat(active(machine)).isLessThanOrEqualTo(1);
      // version 4 was the newest report: whatever the order, the final picture must be its slot
      // list (an active alert for C3) unless it was ignored as stale, which cannot happen to the
      // highest version.
      assertThat(active(machine)).isEqualTo(1);
      assertThat(
              alertsOf(machine).stream()
                  .filter(a -> "ACTIVE".equals(a.get("status")))
                  .findFirst()
                  .orElseThrow()
                  .get("slots"))
          .isEqualTo(
              List.of(
                  Map.of(
                      "slotCode",
                      "C3",
                      "productId",
                      product,
                      "quantity",
                      0,
                      "lowStockThreshold",
                      3)));
    }
  }
}
