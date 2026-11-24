package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.SalesApiTest;

/**
 * Input bounds of POST /v1/sales: occurredAt not before 2020-01-01T00:00:00Z (extreme years are a
 * 422, never a 500) and slotId limited to {@code ^[A-Za-z0-9_-]{1,8}$} (it is used in a path of the
 * Inventory call, so no dots, slashes, spaces or invisible characters).
 */
class SaleIntakeInputBoundsTest extends SalesApiTest {

  // --- occurredAt lower bound

  @Test
  void occurredAtBeforeTheLowerBoundIs422() {
    for (String value :
        new String[] {
          "2019-12-31T23:59:59Z",
          "2000-01-01T00:00:00Z",
          "0001-01-01T00:00:00Z",
          "-290000-01-01T00:00:00Z"
        }) {
      Setup setup = knownSlot();
      Map<String, Object> body = sale(setup);
      body.put("occurredAt", value);

      Http.Response response = postSale(body);

      assertThat(response.status()).as("occurredAt=" + value).isEqualTo(422);
      assertInvalidField(response, "occurredAt");
    }
  }

  @Test
  void occurredAtBeforeTheBoundStoresNothing() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    String valid = (String) body.get("occurredAt");
    body.put("occurredAt", "2019-06-01T00:00:00Z");
    assertThat(postSale(body).status()).isEqualTo(422);

    body.put("occurredAt", valid);

    assertThat(postSale(body).status()).as("first acceptance").isEqualTo(201);
  }

  @Test
  void occurredAtJustAfterTheBoundIsAccepted() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    body.put("occurredAt", "2020-01-02T00:00:00Z");

    assertThat(postSale(body).status()).isEqualTo(201);
  }

  // --- slotId pattern

  @Test
  void slotIdsOutsideThePatternAre422AndNeverReachInventory() {
    String[] bad = {"..", ".", "A/1", "A 1", "A​1", "A‮1", "A.1", "A%2F1", "A\n1"};
    for (String slot : bad) {
      Setup setup = knownSlot();
      Map<String, Object> body = sale(setup);
      body.put("slotId", slot);
      int before = INVENTORY.calls().size();

      Http.Response response = postSale(body);

      String label = "slotId=" + slot.codePoints().boxed().toList();
      assertThat(response.status()).as(label).isEqualTo(422);
      assertInvalidField(response, "slotId");
      assertThat(INVENTORY.calls().size()).as(label + ": Inventory not called").isEqualTo(before);
    }
  }

  @Test
  void slotIdsInsideThePatternAreAccepted() {
    for (String slot : new String[] {"A1", "B_12", "c-3", "12345678"}) {
      Setup setup = knownSlot();
      registerSlot(setup.machineId(), slot, setup.productId(), 5);
      Map<String, Object> body = sale(setup);
      body.put("slotId", slot);

      assertThat(postSale(body).status()).as("slotId=" + slot).isEqualTo(201);
    }
  }
}
