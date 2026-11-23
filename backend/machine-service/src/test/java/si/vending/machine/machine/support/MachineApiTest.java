package si.vending.machine.machine.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.vending.machine.core.support.AbstractApiTest;
import si.vending.machine.core.support.Http;

/**
 * Base for the machine CRUD tests (issue #17). Starts the {@link InventoryStub} and points the
 * service at it.
 *
 * <p>Assumed configuration contract: property {@code inventory.base-url} holds the Inventory base
 * URL (docker-compose sets it from {@code INVENTORY_URL}). An operator request carries BOTH the
 * gateway {@code X-Service-Token} and the {@code X-Operator-Id} set by the gateway; other services
 * use only {@code X-Service-Token} as in the core tests.
 */
public abstract class MachineApiTest extends AbstractApiTest {

  public static final String MONITORING_TOKEN = "monitoring-secret-token";
  public static final String SALES_TOKEN = "sales-secret-token";

  protected static final InventoryStub INVENTORY = InventoryStub.start();

  @DynamicPropertySource
  static void machineProperties(DynamicPropertyRegistry registry) {
    registry.add("inventory.base-url", INVENTORY::baseUrl);
    registry.add("core.service-tokens.monitoring", () -> MONITORING_TOKEN);
    registry.add("core.service-tokens.sales", () -> SALES_TOKEN);
  }

  protected String[] operator() {
    return MachineFixtures.operator();
  }

  protected Http.Response createMachine(String json) {
    return MachineFixtures.create(port, json);
  }

  protected String newMachine(int slots) {
    return MachineFixtures.createMachine(port, "T" + MachineFixtures.tag(), slots);
  }

  protected Http.Response getMachine(String id) {
    return MachineFixtures.getMachine(port, id);
  }

  protected Http.Response put(String path, String json, String... headers) {
    return MachineFixtures.put(port, path, json, headers);
  }

  protected Http.Response delete(String path, String... headers) {
    return MachineFixtures.delete(port, path, headers);
  }

  protected Http.Response setStatus(String id, String status) {
    return MachineFixtures.setStatus(port, id, status);
  }

  protected Http.Response retire(String id) {
    return delete("/v1/machines/" + id, operator());
  }

  protected String statusOf(String id) {
    return (String) getMachine(id).json().get("status");
  }

  /** Asserts a rejected request where the contract leaves 400 vs 422 open. */
  protected static void assertRejected(Http.Response response) {
    assertThat(response.status()).isIn(400, 422);
    Map<String, Object> body = response.json();
    assertThat(body.get("code")).isIn("BAD_REQUEST", "VALIDATION_ERROR");
  }

  /** Asserts a 422 VALIDATION_ERROR whose {@code details} names the field. */
  protected static void assertInvalidField(Http.Response response, String field) {
    assertError(response, 422, "VALIDATION_ERROR");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> details = (List<Map<String, Object>>) response.json().get("details");
    assertThat(details).isNotNull().isNotEmpty();
    assertThat(details).anyMatch(d -> String.valueOf(d.get("field")).startsWith(field));
    assertThat(details).allMatch(d -> d.containsKey("field") && d.containsKey("issue"));
  }
}
