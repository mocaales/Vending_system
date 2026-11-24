package si.vending.sales.sales.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.vending.sales.core.support.AbstractApiTest;
import si.vending.sales.core.support.Http;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for the sale intake tests (issue #26). Starts Product and Inventory stubs and points the
 * service at them.
 *
 * <p>Assumed configuration contract: {@code product.base-url} and {@code inventory.base-url} hold
 * the base URLs of the two services (docker-compose sets them from {@code PRODUCT_URL} and {@code
 * INVENTORY_URL}). A machine sale arrives with the gateway {@code X-Service-Token} and the {@code
 * X-Machine-Id} the gateway derived from the machine key.
 */
public abstract class SalesApiTest extends AbstractApiTest {

  protected static final DependencyStub PRODUCT = DependencyStub.start("product", "/v1/products/");
  protected static final DependencyStub INVENTORY =
      DependencyStub.start("inventory", "/v1/inventory/");

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @DynamicPropertySource
  static void salesProperties(DynamicPropertyRegistry registry) {
    registry.add("product.base-url", PRODUCT::baseUrl);
    registry.add("inventory.base-url", INVENTORY::baseUrl);
  }

  @AfterEach
  void resetStubs() {
    PRODUCT.reset();
    INVENTORY.reset();
  }

  /** A known product and a slot that holds it; returns the pieces of a valid sale. */
  public record Setup(String machineId, String slot, String productId) {}

  protected static Setup knownSlot() {
    String machineId = UUID.randomUUID().toString();
    String productId = UUID.randomUUID().toString();
    registerProduct(productId);
    registerSlot(machineId, "A1", productId, 8);
    return new Setup(machineId, "A1", productId);
  }

  protected static void registerProduct(String productId) {
    PRODUCT.register(
        "/v1/products/" + productId,
        ("{\"id\":\"%s\",\"name\":\"Milk\",\"categoryId\":\"%s\",\"categoryName\":\"Dairy\","
                + "\"referencePrice\":\"2.50\",\"currency\":\"EUR\",\"weightGrams\":1000,"
                + "\"allergens\":[],\"imageUrl\":\"\",\"status\":\"ACTIVE\"}")
            .formatted(productId, UUID.randomUUID()));
  }

  /** Registers a slot; {@code productId == null} is an emptied slot (quantity 0). */
  protected static void registerSlot(
      String machineId, String slot, String productId, int quantity) {
    INVENTORY.register(
        "/v1/inventory/" + machineId + "/" + slot,
        ("{\"machineId\":\"%s\",\"slot\":\"%s\",\"productId\":%s,\"batchCode\":null,"
                + "\"expiryDate\":null,\"quantity\":%d,\"lowStockThreshold\":2}")
            .formatted(
                machineId, slot, productId == null ? "null" : "\"" + productId + "\"", quantity));
  }

  /** The SaleEvent of the contract with every field set; mutate the returned map per test. */
  protected static Map<String, Object> sale(Setup setup) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("saleId", UUID.randomUUID().toString());
    body.put("machineId", setup.machineId());
    body.put("slotId", setup.slot());
    body.put("productId", setup.productId());
    body.put("quantity", 1);
    body.put("unitPricePaid", "2.50");
    body.put("currency", "EUR");
    body.put("paymentMethod", "CARD");
    body.put("occurredAt", Instant.now().minusSeconds(30).toString());
    return body;
  }

  protected static String json(Map<String, Object> body) {
    return JSON.writeValueAsString(body);
  }

  /** Gateway headers of a machine request. */
  protected static String[] machineHeaders(String machineId) {
    return new String[] {"X-Service-Token", GATEWAY_TOKEN, "X-Machine-Id", machineId};
  }

  protected Http.Response postSale(Map<String, Object> body) {
    return post("/v1/sales", json(body), machineHeaders((String) body.get("machineId")));
  }

  /** Asserts a 422 VALIDATION_ERROR whose {@code details} names the field. */
  @SuppressWarnings("unchecked")
  protected static void assertInvalidField(Http.Response response, String field) {
    assertError(response, 422, "VALIDATION_ERROR");
    List<Map<String, Object>> details = (List<Map<String, Object>>) response.json().get("details");
    assertThat(details).isNotNull().isNotEmpty();
    assertThat(details).anyMatch(d -> String.valueOf(d.get("field")).startsWith(field));
    assertThat(details).allMatch(d -> d.containsKey("field") && d.containsKey("issue"));
  }
}
