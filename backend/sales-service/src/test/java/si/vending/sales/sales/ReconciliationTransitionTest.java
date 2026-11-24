package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.ReconciliationTest;

/**
 * #35 acceptance 1 to 3 (docs/01 section 6.3): NEEDS_RECONCILIATION to RESOLVED is the only manual
 * transition and is final. Same body again: 200 unchanged; other body or any other state: 409
 * INVALID_STATE_TRANSITION; unknown sale 404 SALE_NOT_FOUND.
 */
class ReconciliationTransitionTest extends ReconciliationTest {

  @Test
  void resolvesTheSaleAndReturnsTheReconciliationRecord() {
    Fx sale = open();
    Map<String, Object> in = body("WRITTEN_OFF", "Product spoiled, written off");

    Http.Response response = resolve(sale, in, operator());

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    assertResolved(response.json(), sale, in);
    assertFactsOf(response.json(), sale);
  }

  @Test
  void bothResolutionsAreAccepted() {
    for (String resolution : new String[] {"ADJUSTED", "WRITTEN_OFF"}) {
      Fx sale = open();
      Map<String, Object> in = body(resolution, "note for " + resolution);
      assertResolved(resolve(sale, in, operator()).json(), sale, in);
    }
  }

  @Test
  void noteBoundariesOneAndFiveHundredCharactersAreAccepted() {
    for (String note : new String[] {"x", "y".repeat(500)}) {
      Fx sale = open();
      Http.Response response = resolve(sale, body("ADJUSTED", note), operator());
      assertThat(response.status()).as(response.body()).isEqualTo(200);
      assertThat(reconciliationOf(response).get("note")).isEqualTo(note);
    }
  }

  @Test
  void multilineNoteWithNewlineAndTabAndUnicodeIsKept() {
    Fx sale = open();
    String note = "line1\n\tline2 čšž €";
    Http.Response response = resolve(sale, body("ADJUSTED", note), operator());
    assertThat(reconciliationOf(response).get("note")).isEqualTo(note);
  }

  @Test
  void getByIdAndListReturnTheSameReconciliation() {
    Fx sale = open();
    Map<String, Object> in = adjusted();
    Map<String, Object> put = resolve(sale, in, operator()).json();

    Http.Response one = asOperator("/v1/sales/" + sale.saleId());
    assertThat(one.status()).isEqualTo(200);
    assertThat(one.json()).isEqualTo(put);

    Http.Response list = listOf(sale.machineId(), "");
    assertThat(items(list)).hasSize(1);
    assertThat(items(list).get(0)).isEqualTo(put);
    assertThat(stateOf(sale.saleId().toString())).isEqualTo("RESOLVED");
  }

  @Test
  void reconciliationFilterNoLongerListsTheResolvedSale() {
    Fx sale = open();
    assertThat(saleIds(listOf(sale.machineId(), "&reconciliation=NEEDS_RECONCILIATION")))
        .containsExactly(sale.saleId().toString());

    resolve(sale, adjusted(), operator());

    assertThat(saleIds(listOf(sale.machineId(), "&reconciliation=NEEDS_RECONCILIATION"))).isEmpty();
    assertThat(saleIds(listOf(sale.machineId(), "&reconciliation=RESOLVED")))
        .containsExactly(sale.saleId().toString());
  }

  @Test
  void sameBodyAgainIs200AndKeepsTheFirstResolvedAtAndResolvedBy() throws Exception {
    Fx sale = open();
    Map<String, Object> in = adjusted();
    Map<String, Object> first = resolve(sale, in, operator()).json();
    Thread.sleep(50);

    Http.Response again = resolve(sale, in, otherOperator());

    assertThat(again.status()).as(again.body()).isEqualTo(200);
    assertThat(again.json()).isEqualTo(first);
    assertThat(reconciliationOf(again).get("resolvedBy")).isEqualTo(OPERATOR_ID);
  }

  @Test
  void otherResolutionOrOtherNoteOnAResolvedSaleIs409AndChangesNothing() {
    Fx sale = open();
    Map<String, Object> first = resolve(sale, adjusted(), operator()).json();

    for (Map<String, Object> other :
        new Map[] {
          body("WRITTEN_OFF", (String) adjusted().get("note")), body("ADJUSTED", "another note")
        }) {
      assertError(resolve(sale, other, operator()), 409, "INVALID_STATE_TRANSITION");
    }
    assertThat(asOperator("/v1/sales/" + sale.saleId()).json()).isEqualTo(first);
  }

  @Test
  void pendingAndAppliedSalesAre409AndStayUnchanged() {
    for (String state : new String[] {"PENDING", "APPLIED"}) {
      Fx sale = insert(UUID.randomUUID(), UUID.randomUUID(), now().minusSeconds(60), state);
      assertError(resolve(sale, adjusted(), operator()), 409, "INVALID_STATE_TRANSITION");
      Map<String, Object> out = asOperator("/v1/sales/" + sale.saleId()).json();
      assertSaleOut(out, sale);
    }
  }

  @Test
  void unknownSaleIs404() {
    Http.Response response = put(path(UUID.randomUUID()), json(adjusted()), operator());
    assertError(response, 404, "SALE_NOT_FOUND");
  }

  @Test
  void malformedSaleIdIs400() {
    assertThat(put(path("not-a-uuid"), json(adjusted()), operator()).status()).isEqualTo(400);
  }

  @Test
  void resolvingDoesNotCallInventoryOrProductAndTouchesNoOutbox() {
    Fx sale = open();

    assertThat(resolve(sale, adjusted(), operator()).status()).isEqualTo(200);

    assertThat(INVENTORY.calls()).isEmpty();
    assertThat(PRODUCT.calls()).isEmpty();
    assertThat(outboxCount(sale.saleId().toString())).isZero();
  }
}
