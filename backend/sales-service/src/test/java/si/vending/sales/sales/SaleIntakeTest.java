package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.SalesApiTest;

/** #26: POST /v1/sales happy path, idempotency by saleId, response body per contract. */
class SaleIntakeTest extends SalesApiTest {

  private static final Set<String> SALE_OUT_FIELDS =
      Set.of(
          "saleId",
          "machineId",
          "slotId",
          "productId",
          "quantity",
          "unitPricePaid",
          "currency",
          "paymentMethod",
          "occurredAt",
          "receivedAt",
          "inventory",
          "reconciliation");

  @SuppressWarnings("unchecked")
  @Test
  void firstPostReturns201WithTheStoredSaleAndStatePending() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);

    Http.Response response = postSale(body);

    assertThat(response.status()).isEqualTo(201);
    Map<String, Object> out = response.json();
    assertThat(SALE_OUT_FIELDS).containsAll(out.keySet());
    assertThat(out.keySet())
        .contains("saleId", "machineId", "slotId", "productId", "quantity", "unitPricePaid")
        .contains("currency", "paymentMethod", "occurredAt", "receivedAt", "inventory");
    assertThat(out.get("saleId")).isEqualTo(body.get("saleId"));
    assertThat(out.get("machineId")).isEqualTo(body.get("machineId"));
    assertThat(out.get("slotId")).isEqualTo("A1");
    assertThat(out.get("productId")).isEqualTo(body.get("productId"));
    assertThat(out.get("quantity")).isEqualTo(1);
    assertThat(out.get("currency")).isEqualTo("EUR");
    assertThat(out.get("paymentMethod")).isEqualTo("CARD");
    assertThat(Instant.parse((String) out.get("occurredAt")))
        .isEqualTo(Instant.parse((String) body.get("occurredAt")));
    assertThat(Instant.parse((String) out.get("receivedAt")))
        .isBetween(Instant.now().minus(Duration.ofMinutes(1)), Instant.now().plusSeconds(5));
    Map<String, Object> inventory = (Map<String, Object>) out.get("inventory");
    assertThat(inventory.get("state")).isEqualTo("PENDING");
    assertThat(inventory.get("reason")).isNull();
    assertThat(out.get("reconciliation")).isNull();
  }

  @Test
  void moneyIsKeptExactlyAsADecimalString() {
    Setup setup = knownSlot();
    for (String price : new String[] {"2.50", "0.10", "1234567.89", "0.00", "7"}) {
      Map<String, Object> body = sale(setup);
      body.put("unitPricePaid", price);

      Http.Response response = postSale(body);

      assertThat(response.status()).as(price).isEqualTo(201);
      Object paid = response.json().get("unitPricePaid");
      assertThat(paid).as(price).isInstanceOf(String.class);
      assertThat(new java.math.BigDecimal((String) paid)).isEqualByComparingTo(price);
      if (price.contains(".")) {
        assertThat(paid).as("no rounding, no reformatting of " + price).isEqualTo(price);
      }
    }
  }

  @Test
  void identicalRepeatReturns200WithTheExistingResult() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    Http.Response first = postSale(body);
    assertThat(first.status()).isEqualTo(201);

    Http.Response again = postSale(body);

    assertThat(again.status()).isEqualTo(200);
    assertThat(again.json()).isEqualTo(first.json());
  }

  @Test
  void differentBodyWithTheSameSaleIdIs409AndLeavesTheStoredSaleUntouched() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    Http.Response first = postSale(body);
    assertThat(first.status()).isEqualTo(201);

    Map<String, Object> changed = new java.util.LinkedHashMap<>(body);
    changed.put("quantity", 2);
    assertError(postSale(changed), 409, "SALE_ID_CONFLICT");

    Map<String, Object> otherPrice = new java.util.LinkedHashMap<>(body);
    otherPrice.put("unitPricePaid", "2.51");
    assertError(postSale(otherPrice), 409, "SALE_ID_CONFLICT");

    Http.Response again = postSale(body);
    assertThat(again.status()).isEqualTo(200);
    assertThat(again.json()).isEqualTo(first.json());
  }

  @Test
  void aSaleWhoseSlotHoldsAnotherProductOrIsEmptyIsStillAcceptedForReconciliation() {
    // docs/01 5.1 and docs/03 7: PRODUCT_MISMATCH is found later by the stock decrease
    // (reconciliation), not at intake; intake only rejects unknown references.
    String machineId = java.util.UUID.randomUUID().toString();
    String sold = java.util.UUID.randomUUID().toString();
    String inSlot = java.util.UUID.randomUUID().toString();
    registerProduct(sold);
    registerProduct(inSlot);
    registerSlot(machineId, "B1", inSlot, 5);
    registerSlot(machineId, "B2", null, 0);

    Map<String, Object> mismatch = sale(new Setup(machineId, "B1", sold));
    Map<String, Object> empty = sale(new Setup(machineId, "B2", sold));

    assertThat(postSale(mismatch).status()).isEqualTo(201);
    assertThat(postSale(empty).status()).isEqualTo(201);
  }

  @Test
  void occurredAtJustInsideTheFiveMinuteWindowAndInThePastIsAccepted() {
    Setup setup = knownSlot();
    Map<String, Object> soon = sale(setup);
    soon.put("occurredAt", Instant.now().plusSeconds(4 * 60).toString());
    Map<String, Object> old = sale(setup);
    old.put("occurredAt", Instant.now().minus(Duration.ofDays(3)).toString());

    assertThat(postSale(soon).status()).isEqualTo(201);
    assertThat(postSale(old).status()).isEqualTo(201);
  }

  @Test
  void intakeAsksProductAndInventoryAboutTheSale() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);

    assertThat(postSale(body).status()).isEqualTo(201);

    assertThat(PRODUCT.callsTo("/v1/products/" + setup.productId())).isNotEmpty();
    assertThat(INVENTORY.callsTo("/v1/inventory/" + setup.machineId() + "/A1")).isNotEmpty();
    assertThat(INVENTORY.callsTo("/decrease"))
        .as("the stock decrease is the outbox's job (#32), never done during intake")
        .isEmpty();
  }
}
