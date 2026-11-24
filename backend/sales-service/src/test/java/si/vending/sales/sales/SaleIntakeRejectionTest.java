package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.SalesApiTest;

/** #26: 403 MACHINE_KEY_MISMATCH, 422 REFERENCE_NOT_FOUND / TIMESTAMP_OUT_OF_RANGE / validation. */
class SaleIntakeRejectionTest extends SalesApiTest {

  @Test
  void bodyMachineIdOtherThanTheAuthenticatedMachineIs403() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    String otherMachine = UUID.randomUUID().toString();

    Http.Response response = post("/v1/sales", json(body), machineHeaders(otherMachine));

    assertError(response, 403, "MACHINE_KEY_MISMATCH");
    assertThat(INVENTORY.callsTo(setup.machineId()))
        .as("rejected before any dependency is called")
        .isEmpty();
    // nothing stored: the same sale from the right machine is a first acceptance
    assertThat(postSale(body).status()).isEqualTo(201);
  }

  @Test
  void unknownProductIs422ReferenceNotFound() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    body.put("productId", UUID.randomUUID().toString());

    assertError(postSale(body), 422, "REFERENCE_NOT_FOUND");
  }

  @Test
  void unknownSlotIs422ReferenceNotFound() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    body.put("slotId", "Z9");

    assertError(postSale(body), 422, "REFERENCE_NOT_FOUND");
  }

  @Test
  void referenceNotFoundStoresNothing() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    body.put("slotId", "Z9");
    assertError(postSale(body), 422, "REFERENCE_NOT_FOUND");

    registerSlot(setup.machineId(), "Z9", setup.productId(), 3);

    assertThat(postSale(body).status()).as("first acceptance, not a repeat").isEqualTo(201);
  }

  @Test
  void occurredAtMoreThanFiveMinutesInTheFutureIs422() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    body.put("occurredAt", Instant.now().plusSeconds(10 * 60).toString());

    assertError(postSale(body), 422, "TIMESTAMP_OUT_OF_RANGE");
  }

  @Test
  void invalidFieldsAre422ValidationErrorWithDetails() {
    Setup setup = knownSlot();
    Object[][] cases = {
      {"quantity", 0},
      {"quantity", 101},
      {"quantity", "one"},
      {"unitPricePaid", "2.505"},
      {"unitPricePaid", "-1.00"},
      {"unitPricePaid", "abc"},
      {"currency", "eur"},
      {"currency", "EURO"},
      {"paymentMethod", "BITCOIN"},
      {"slotId", ""},
      {"slotId", "123456789"},
      {"saleId", "not-a-uuid"},
      {"machineId", "not-a-uuid"},
      {"productId", "not-a-uuid"},
      {"occurredAt", "yesterday"},
    };
    for (Object[] c : cases) {
      Map<String, Object> body = sale(setup);
      body.put((String) c[0], c[1]);

      Http.Response response = post("/v1/sales", json(body), machineHeaders(setup.machineId()));

      assertThat(response.status()).as(c[0] + "=" + c[1]).isEqualTo(422);
      assertInvalidField(response, (String) c[0]);
    }
  }

  @Test
  void everyFieldIsRequired() {
    Setup setup = knownSlot();
    for (String field :
        new String[] {
          "saleId",
          "machineId",
          "slotId",
          "productId",
          "quantity",
          "unitPricePaid",
          "currency",
          "paymentMethod",
          "occurredAt"
        }) {
      Map<String, Object> body = sale(setup);
      body.remove(field);

      Http.Response response = post("/v1/sales", json(body), machineHeaders(setup.machineId()));

      assertThat(response.status()).as("missing " + field).isEqualTo(422);
      assertInvalidField(response, field);
    }
  }

  @Test
  void unknownFieldsAreRejected() {
    Setup setup = knownSlot();
    Map<String, Object> body = new LinkedHashMap<>(sale(setup));
    body.put("discount", "1.00");

    Http.Response response = post("/v1/sales", json(body), machineHeaders(setup.machineId()));

    assertError(response, 422, "VALIDATION_ERROR");
  }

  @Test
  void unreadableJsonIs400() {
    Setup setup = knownSlot();

    Http.Response response = post("/v1/sales", "{not json", machineHeaders(setup.machineId()));

    assertError(response, 400, "BAD_REQUEST");
  }
}
