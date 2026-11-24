package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.ClosingTest;

/**
 * #55: parallel requests keep the invariants. One closing per (date, machineId) also for the
 * all-machines scope (machineId null), a draft is deleted once, a confirmation is applied once and
 * the identical repeat is the same answer.
 */
class ClosingConcurrencyTest extends ClosingTest {

  /** Runs the calls at the same moment and returns their responses in order. */
  private static List<Http.Response> parallel(List<Callable<Http.Response>> calls)
      throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(calls.size());
    try {
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
        responses.add(future.get());
      }
      return responses;
    } finally {
      pool.shutdownNow();
    }
  }

  private static List<Integer> statuses(List<Http.Response> responses) {
    return responses.stream().map(Http.Response::status).toList();
  }

  @Test
  void twoParallelPostsForTheSameMachineGiveOneCreatedAndOneConflict() throws Exception {
    for (int round = 0; round < 3; round++) {
      UUID machine = UUID.randomUUID();
      saleOnYesterday(machine, 1, "2.50", "EUR");

      List<Http.Response> responses =
          parallel(List.of(() -> create(machine), () -> create(machine)));

      assertThat(statuses(responses)).containsExactlyInAnyOrder(201, 409);
      Http.Response winner = responses.get(0).status() == 201 ? responses.get(0) : responses.get(1);
      Http.Response loser = responses.get(0).status() == 201 ? responses.get(1) : responses.get(0);
      assertError(loser, 409, "CLOSING_EXISTS");
      assertThat(asOperator("/v1/sales/closings?machineId=" + machine).json().get("total"))
          .isEqualTo(1);
      assertThat(getClosing(winner.json().get("id")).json()).isEqualTo(winner.json());
    }
  }

  @Test
  void manyParallelPostsForTheSameMachineCreateExactlyOne() throws Exception {
    UUID machine = UUID.randomUUID();
    List<Callable<Http.Response>> calls = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      calls.add(() -> create(machine));
    }

    List<Integer> statuses = statuses(parallel(calls));

    assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
    assertThat(statuses).filteredOn(s -> s == 409).hasSize(5);
    assertThat(asOperator("/v1/sales/closings?machineId=" + machine).json().get("total"))
        .isEqualTo(1);
  }

  @Test
  void twoParallelAllMachinesPostsGiveOneCreatedAndOneConflict() throws Exception {
    for (int round = 0; round < 3; round++) {
      dropDrafts();

      List<Http.Response> responses = parallel(List.of(() -> create(null), () -> create(null)));

      assertThat(statuses(responses)).containsExactlyInAnyOrder(201, 409);
      String day = yesterday().toString();
      Http.Response list =
          asOperator("/v1/sales/closings?from=" + day + "&to=" + day + "&limit=200");
      long allMachines = items(list).stream().filter(c -> c.get("machineId") == null).count();
      assertThat(allMachines).isEqualTo(1);
    }
  }

  @Test
  void twoParallelDeletesOfADraftGiveOne204AndOne404() throws Exception {
    Map<String, Object> created = created(UUID.randomUUID());

    List<Http.Response> responses =
        parallel(
            List.of(
                () -> deleteClosing(created.get("id"), operator()),
                () -> deleteClosing(created.get("id"), operator())));

    assertThat(statuses(responses)).containsExactlyInAnyOrder(204, 404);
    assertError(getClosing(created.get("id")), 404, "CLOSING_NOT_FOUND");
  }

  @Test
  void parallelIdenticalConfirmationsAreAllSuccessfulAndIdentical() throws Exception {
    UUID machine = UUID.randomUUID();
    saleOnYesterday(machine, 1, "2.50", "EUR");
    Map<String, Object> created = created(machine);

    List<Http.Response> responses =
        parallel(
            List.of(
                () -> confirm(created.get("id"), "done"),
                () -> confirm(created.get("id"), "done"),
                () -> confirm(created.get("id"), "done")));

    assertThat(statuses(responses)).containsOnly(200);
    Map<String, Object> stored = getClosing(created.get("id")).json();
    assertThat(stored.get("status")).isEqualTo("CONFIRMED");
    for (Http.Response response : responses) {
      assertThat(response.json()).isEqualTo(stored);
    }
  }

  @Test
  void aConfirmationAndADeleteNeverBothSucceed() throws Exception {
    for (int round = 0; round < 3; round++) {
      Map<String, Object> created = created(UUID.randomUUID());

      List<Http.Response> responses =
          parallel(
              List.of(
                  () -> confirm(created.get("id"), "done"),
                  () -> deleteClosing(created.get("id"), operator())));

      Http.Response confirmed = responses.get(0);
      Http.Response deleted = responses.get(1);
      Http.Response stored = getClosing(created.get("id"));
      if (deleted.status() == 204) {
        assertThat(confirmed.status()).isEqualTo(404);
        assertThat(stored.status()).isEqualTo(404);
      } else {
        assertThat(confirmed.status()).isEqualTo(200);
        assertError(deleted, 409, "CLOSING_CONFIRMED");
        assertThat(stored.json().get("status")).isEqualTo("CONFIRMED");
      }
    }
  }
}
