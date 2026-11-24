package si.vending.sales.sales.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import si.vending.sales.core.support.Http;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for the reconciliation tests (issue #35, {@code PUT /v1/sales/{saleId}/reconciliation}).
 *
 * <p>Shared contract: {@code ReconciliationIn}, {@code ReconciliationRecord} and {@code SaleOut} of
 * {@code backend/sales-service/openapi.yaml}. The operator is the gateway token plus {@code
 * X-Operator-Id}; {@code resolvedBy} is that id. Fixtures are inserted straight into table {@code
 * sales}; the tests do not depend on how the implementation updates the row.
 */
public abstract class ReconciliationTest extends OutboxTest {

  protected static final String OTHER_OPERATOR_ID = "9b1d6a52-3c47-4e08-b7f1-0a5c2d8e4f66";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  protected static String[] otherOperator() {
    return new String[] {"X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", OTHER_OPERATOR_ID};
  }

  protected Http.Response put(String path, String json, String... headers) {
    String[] all = new String[headers.length + 2];
    all[0] = "Content-Type";
    all[1] = "application/json";
    System.arraycopy(headers, 0, all, 2, headers.length);
    return Http.send(port, "PUT", path, json, all);
  }

  protected static String path(Object saleId) {
    return "/v1/sales/" + saleId + "/reconciliation";
  }

  protected static Map<String, Object> body(String resolution, String note) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("resolution", resolution);
    body.put("note", note);
    return body;
  }

  protected static Map<String, Object> adjusted() {
    return body("ADJUSTED", "Stock corrected in Inventory by hand");
  }

  protected static String json(Object body) {
    return JSON.writeValueAsString(body);
  }

  protected Http.Response resolve(Fx sale, Map<String, Object> body, String... caller) {
    return put(path(sale.saleId()), json(body), caller);
  }

  /** A sale that needs reconciliation (reason INSUFFICIENT_STOCK) with a random machine/product. */
  protected Fx open() {
    return insert(
        UUID.randomUUID(), UUID.randomUUID(), now().minusSeconds(60), "NEEDS_RECONCILIATION");
  }

  @SuppressWarnings("unchecked")
  protected static Map<String, Object> reconciliationOf(Http.Response response) {
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    return (Map<String, Object>) response.json().get("reconciliation");
  }

  @SuppressWarnings("unchecked")
  protected static void assertResolved(Map<String, Object> out, Fx sale, Map<String, Object> in) {
    Map<String, Object> inventory = (Map<String, Object>) out.get("inventory");
    assertThat(inventory.get("state")).isEqualTo("RESOLVED");
    assertThat(inventory.get("reason")).isEqualTo(sale.reason());
    Map<String, Object> rec = (Map<String, Object>) out.get("reconciliation");
    assertThat(rec).isNotNull();
    assertThat(rec.keySet())
        .containsExactlyInAnyOrder("resolution", "note", "resolvedAt", "resolvedBy");
    assertThat(rec.get("resolution")).isEqualTo(in.get("resolution"));
    assertThat(rec.get("note")).isEqualTo(in.get("note"));
    assertThat(rec.get("resolvedBy")).isEqualTo(OPERATOR_ID);
    assertThat(Instant.parse((String) rec.get("resolvedAt")))
        .isBetween(Instant.now().minusSeconds(30), Instant.now().plusSeconds(5));
  }

  /** The sale facts of {@code SaleOut} equal the fixture (state and reconciliation aside). */
  protected static void assertFactsOf(Map<String, Object> out, Fx sale) {
    assertThat(out.get("saleId")).isEqualTo(sale.saleId().toString());
    assertThat(out.get("machineId")).isEqualTo(sale.machineId().toString());
    assertThat(out.get("productId")).isEqualTo(sale.productId().toString());
    assertThat(out.get("slotId")).isEqualTo("A1");
    assertThat(out.get("quantity")).isEqualTo(sale.quantity());
    assertThat(out.get("unitPricePaid")).isEqualTo(sale.price());
    assertThat(out.get("currency")).isEqualTo(sale.currency());
    assertThat(out.get("paymentMethod")).isEqualTo("CARD");
    assertThat(Instant.parse((String) out.get("occurredAt")).truncatedTo(ChronoUnit.MILLIS))
        .isEqualTo(sale.occurredAt());
  }
}
