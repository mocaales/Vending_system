package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.DependencyStub.Reply;
import si.vending.sales.sales.support.ReconciliationTest;

/**
 * #35 interplay: the outbox worker (#32) never changes a RESOLVED sale; Product's archive query
 * (GET /v1/sales?productId=...&reconciliation=NEEDS_RECONCILIATION&limit=1) stops matching once the
 * sale is resolved; concurrent different bodies produce exactly one winner.
 */
class ReconciliationInteractionTest extends ReconciliationTest {

  /** Guard: may already pass before the endpoint exists (state is seeded directly). */
  @Test
  void aLateOutboxResultNeverChangesAResolvedSale() {
    Fx sale = insert(UUID.randomUUID(), UUID.randomUUID(), now().minusSeconds(60), "RESOLVED");
    jdbc.update(
        "INSERT INTO sale_outbox (sale_id, attempts, next_attempt_at) VALUES (?, 0, now())",
        sale.saleId());
    INVENTORY.scriptDecrease(Reply.applied());

    runOnce();

    assertThat(stateOf(sale.saleId().toString())).isEqualTo("RESOLVED");
  }

  @Test
  void aLateOutboxResultAfterTheOperatorResolvedItKeepsResolvedAndTheRecord() {
    Fx sale = open();
    jdbc.update(
        "INSERT INTO sale_outbox (sale_id, attempts, next_attempt_at) VALUES (?, 0, now())",
        sale.saleId());
    Map<String, Object> first = resolve(sale, adjusted(), operator()).json();
    assertThat(first.get("reconciliation")).isNotNull();
    INVENTORY.scriptDecrease(Reply.applied());

    runOnce();

    assertThat(asOperator("/v1/sales/" + sale.saleId()).json()).isEqualTo(first);
  }

  @Test
  void productArchiveQueryFindsTheOpenSaleAndNotTheResolvedOne() {
    Fx sale = open();
    String query =
        "/v1/sales?productId=" + sale.productId() + "&reconciliation=NEEDS_RECONCILIATION&limit=1";
    assertThat(saleIds(get(query, product()))).containsExactly(sale.saleId().toString());

    assertThat(resolve(sale, adjusted(), operator()).status()).isEqualTo(200);

    assertThat(items(get(query, product()))).isEmpty();
    assertThat(get(query, product()).json().get("total")).isEqualTo(0);
  }

  @Test
  void concurrentDifferentBodiesGiveExactlyOneWinner() throws Exception {
    for (int round = 0; round < 3; round++) {
      Fx sale = open();
      Map<String, Object> a = body("ADJUSTED", "winner a");
      Map<String, Object> b = body("WRITTEN_OFF", "winner b");
      CountDownLatch start = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        List<Future<Http.Response>> futures = new ArrayList<>();
        for (Map<String, Object> in : List.of(a, b)) {
          futures.add(
              pool.submit(() -> {
                start.await();
                return resolve(sale, in, operator());
              }));
        }
        start.countDown();
        Http.Response r1 = futures.get(0).get();
        Http.Response r2 = futures.get(1).get();

        List<Integer> statuses = List.of(r1.status(), r2.status());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        Http.Response winner = r1.status() == 200 ? r1 : r2;
        Http.Response loser = r1.status() == 200 ? r2 : r1;
        assertError(loser, 409, "INVALID_STATE_TRANSITION");
        assertThat(asOperator("/v1/sales/" + sale.saleId()).json()).isEqualTo(winner.json());
      } finally {
        pool.shutdownNow();
      }
    }
  }

  @Test
  void concurrentSameBodiesAreAllSuccessfulAndIdentical() throws Exception {
    Fx sale = open();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Future<Http.Response>> futures = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        futures.add(
            pool.submit(() -> {
              start.await();
              return resolve(sale, adjusted(), operator());
            }));
      }
      start.countDown();
      Map<String, Object> first = null;
      for (Future<Http.Response> f : futures) {
        Http.Response r = f.get();
        assertThat(r.status()).as(r.body()).isEqualTo(200);
        first = first == null ? r.json() : first;
        assertThat(r.json()).isEqualTo(first);
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
