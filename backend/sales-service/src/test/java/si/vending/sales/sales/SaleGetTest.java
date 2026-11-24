package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.SalesReadTest;

/**
 * GET /v1/sales/{saleId} (issue #27): the SaleOut with inventory {state, reason?}; unknown id 404
 * SALE_NOT_FOUND; a malformed id 400 BAD_REQUEST (docs/03 section 2); the static path {@code stats}
 * is never a sale id.
 */
class SaleGetTest extends SalesReadTest {

  @Test
  @SuppressWarnings("unchecked")
  void returnsTheSaleWithItsReconciliationState() {
    Fx sale =
        insert(
            UUID.randomUUID(),
            UUID.randomUUID(),
            3,
            "1.20",
            "EUR",
            now().minusSeconds(60),
            "APPLIED",
            null);

    Http.Response response = asOperator("/v1/sales/" + sale.saleId());

    assertThat(response.status()).isEqualTo(200);
    assertSaleOut(response.json(), sale);
    assertThat(((Map<String, Object>) response.json().get("inventory")).get("state"))
        .isEqualTo("APPLIED");
  }

  @Test
  @SuppressWarnings("unchecked")
  void aSaleNeedingReconciliationCarriesTheReason() {
    Fx sale =
        insert(
            UUID.randomUUID(), UUID.randomUUID(), now().minusSeconds(60), "NEEDS_RECONCILIATION");

    Http.Response response = asOperator("/v1/sales/" + sale.saleId());
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    Map<String, Object> inventory = (Map<String, Object>) response.json().get("inventory");

    assertThat(inventory.get("state")).isEqualTo("NEEDS_RECONCILIATION");
    assertThat(inventory.get("reason")).isEqualTo("INSUFFICIENT_STOCK");
  }

  @Test
  void everyStateIsReadable() {
    for (String state : new String[] {"PENDING", "APPLIED", "NEEDS_RECONCILIATION", "RESOLVED"}) {
      Fx sale = insert(UUID.randomUUID(), UUID.randomUUID(), now().minusSeconds(60), state);

      Http.Response response = asOperator("/v1/sales/" + sale.saleId());

      assertThat(response.status()).as(state).isEqualTo(200);
      assertSaleOut(response.json(), sale);
    }
  }

  @Test
  void aSaleRecordedByPostIsReadBackWithTheSameContent() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    Http.Response created = postSale(body);
    assertThat(created.status()).isEqualTo(201);

    Http.Response read = asOperator("/v1/sales/" + body.get("saleId"));

    assertThat(read.status()).isEqualTo(200);
    Map<String, Object> out = read.json();
    for (String field :
        new String[] {
          "saleId",
          "machineId",
          "slotId",
          "productId",
          "quantity",
          "unitPricePaid",
          "currency",
          "paymentMethod"
        }) {
      assertThat(out.get(field)).as(field).isEqualTo(body.get(field));
    }
    assertThat(parseInstant(out.get("occurredAt"))).isEqualTo(parseInstant(body.get("occurredAt")));
    assertThat(out.get("inventory")).isEqualTo(created.json().get("inventory"));
    // reading never calls a dependency beyond what the POST itself did
    int callsAfterPost = PRODUCT.calls().size() + INVENTORY.calls().size();
    asOperator("/v1/sales/" + body.get("saleId"));
    assertThat(PRODUCT.calls().size() + INVENTORY.calls().size()).isEqualTo(callsAfterPost);
  }

  private static Instant parseInstant(Object value) {
    return Instant.parse((String) value);
  }

  @Test
  void unknownSaleIs404SaleNotFound() {
    assertError(asOperator("/v1/sales/" + UUID.randomUUID()), 404, "SALE_NOT_FOUND");
  }

  @Test
  void malformedSaleIdIs400() {
    for (String bad : new String[] {"not-a-uuid", "123", "00000000-0000-0000-0000-00000000000g"}) {
      assertError(asOperator("/v1/sales/" + bad), 400, "BAD_REQUEST");
    }
  }

  @Test
  void statsIsARouteNotASaleId() {
    Http.Response response = asOperator("/v1/sales/stats");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().keySet()).containsExactlyInAnyOrder("totals", "topProducts");
  }
}
