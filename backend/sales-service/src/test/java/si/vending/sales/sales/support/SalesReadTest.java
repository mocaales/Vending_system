package si.vending.sales.sales.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.vending.sales.core.support.Http;

/**
 * Base for the read tests (issue #27: list, get, statistics). Fixtures are inserted straight into
 * table {@code sales} (the immutability trigger allows inserts), so every inventory state and any
 * {@code occurredAt} can be set.
 *
 * <p>Isolation: the database is shared by every test class of the JVM. List tests isolate with a
 * random {@code machineId} or {@code productId}; tests of the global statistics use a private time
 * window far in the future ({@link #window()}) that no other test writes to.
 *
 * <p>Shared contract: {@code SaleOut}, {@code SaleList} and {@code SalesStatsOut} of {@code
 * backend/sales-service/openapi.yaml}. Money is a decimal string with two decimals ("7.50").
 */
public abstract class SalesReadTest extends SalesApiTest {

  public static final String PRODUCT_TOKEN = "product-secret-token";
  public static final String OPERATOR_ID = "2f0e3c1a-7b9d-4c55-8a1e-6d4b9f0c3e21";

  private static final AtomicInteger WINDOWS = new AtomicInteger();

  @DynamicPropertySource
  static void productCaller(DynamicPropertyRegistry registry) {
    registry.add("core.service-tokens.product", () -> PRODUCT_TOKEN);
  }

  @Autowired protected JdbcTemplate jdbc;

  /** A stored sale as inserted by {@link #insert}. */
  public record Fx(
      UUID saleId,
      UUID machineId,
      UUID productId,
      int quantity,
      String price,
      String currency,
      Instant occurredAt,
      String state,
      String reason) {}

  /** A private two-day interval in 2030..2060 for tests of the statistics over all machines. */
  public record Window(Instant from, Instant to) {
    public Instant at(long hours) {
      return from.plus(hours, ChronoUnit.HOURS);
    }
  }

  protected static Window window() {
    Instant from =
        Instant.parse("2030-01-01T00:00:00Z").plus(3L * WINDOWS.incrementAndGet(), ChronoUnit.DAYS);
    return new Window(from, from.plus(2, ChronoUnit.DAYS));
  }

  protected static Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MILLIS);
  }

  protected Fx insert(
      UUID machineId,
      UUID productId,
      int quantity,
      String price,
      String currency,
      Instant occurredAt,
      String state,
      String reason) {
    UUID saleId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO sales (sale_id, machine_id, slot_code, product_id, quantity, unit_price_paid,"
            + " currency, payment_method, occurred_at, inventory_state, inventory_reason)"
            + " VALUES (?, ?, 'A1', ?, ?, ?::numeric, ?, 'CARD', ?, ?, ?)",
        saleId,
        machineId,
        productId,
        quantity,
        price,
        currency,
        OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC),
        state,
        reason);
    return new Fx(
        saleId, machineId, productId, quantity, price, currency, occurredAt, state, reason);
  }

  protected Fx insert(UUID machineId, UUID productId, Instant occurredAt) {
    return insert(machineId, productId, 1, "2.50", "EUR", occurredAt, "APPLIED", null);
  }

  protected Fx insert(UUID machineId, UUID productId, Instant occurredAt, String state) {
    return insert(
        machineId,
        productId,
        1,
        "2.50",
        "EUR",
        occurredAt,
        state,
        "NEEDS_RECONCILIATION".equals(state) ? "INSUFFICIENT_STOCK" : null);
  }

  // --- callers

  /** Gateway token plus an operator id: the operator through the gateway. */
  protected static String[] operator() {
    return new String[] {"X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", OPERATOR_ID};
  }

  /** Gateway token without an identity header: the Frontend API module of the gateway. */
  protected static String[] frontendApi() {
    return new String[] {"X-Service-Token", GATEWAY_TOKEN};
  }

  protected static String[] product() {
    return new String[] {"X-Service-Token", PRODUCT_TOKEN};
  }

  protected static String[] inventory() {
    return new String[] {"X-Service-Token", INVENTORY_TOKEN};
  }

  protected static String[] machine() {
    return new String[] {
      "X-Service-Token", GATEWAY_TOKEN, "X-Machine-Id", UUID.randomUUID().toString()
    };
  }

  protected Http.Response asOperator(String path) {
    return get(path, operator());
  }

  // --- response helpers

  @SuppressWarnings("unchecked")
  protected static List<Map<String, Object>> items(Http.Response response) {
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    return (List<Map<String, Object>>) response.json().get("items");
  }

  protected static List<String> saleIds(Http.Response response) {
    List<String> ids = new ArrayList<>();
    for (Map<String, Object> item : items(response)) {
      ids.add((String) item.get("saleId"));
    }
    return ids;
  }

  protected static List<String> ids(Fx... fixtures) {
    return Arrays.stream(fixtures).map(f -> f.saleId().toString()).toList();
  }

  protected static String iso(Instant instant) {
    return instant.toString();
  }

  /** The list of one machine, as operator, with extra query parameters (may be empty). */
  protected Http.Response listOf(UUID machineId, String extraQuery) {
    return asOperator("/v1/sales?machineId=" + machineId + extraQuery);
  }

  @SuppressWarnings("unchecked")
  protected static void assertSaleOut(Map<String, Object> out, Fx fixture) {
    assertThat(out.keySet())
        .containsExactlyInAnyOrder(
            out.containsKey("reconciliation")
                ? new String[] {
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
                  "reconciliation"
                }
                : new String[] {
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
                  "inventory"
                });
    assertThat(out.get("saleId")).isEqualTo(fixture.saleId().toString());
    assertThat(out.get("machineId")).isEqualTo(fixture.machineId().toString());
    assertThat(out.get("productId")).isEqualTo(fixture.productId().toString());
    assertThat(out.get("slotId")).isEqualTo("A1");
    assertThat(out.get("quantity")).isEqualTo(fixture.quantity());
    assertThat(out.get("unitPricePaid")).isEqualTo(fixture.price());
    assertThat(out.get("currency")).isEqualTo(fixture.currency());
    assertThat(out.get("paymentMethod")).isEqualTo("CARD");
    assertThat(Instant.parse((String) out.get("occurredAt"))).isEqualTo(fixture.occurredAt());
    assertThat(Instant.parse((String) out.get("receivedAt"))).isNotNull();
    Map<String, Object> inventory = (Map<String, Object>) out.get("inventory");
    assertThat(inventory.get("state")).isEqualTo(fixture.state());
    assertThat(inventory.get("reason")).isEqualTo(fixture.reason());
    assertThat(out.get("reconciliation")).isNull();
  }
}
