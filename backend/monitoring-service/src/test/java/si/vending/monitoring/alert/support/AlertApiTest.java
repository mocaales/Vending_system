package si.vending.monitoring.alert.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/**
 * Base for the alert tests (issue #25). Contract: openapi.yaml {@code /v1/alerts}, {@code
 * /v1/alerts/{id}/acknowledgement}, {@code /v1/alerts/{id}/resolution}; docs/03 section 5; docs/01
 * section 6.2.
 *
 * <p>Callers (openapi {@code x-callers}):
 *
 * <ul>
 *   <li>{@code POST /v1/alerts}: {@code inventory} only, i.e. {@code X-Service-Token} = {@link
 *       #INVENTORY_TOKEN} without any identity header.
 *   <li>{@code GET /v1/alerts}: an operator (gateway token + {@code X-Operator-Id}) or frontend-api
 *       (gateway token alone).
 *   <li>The two {@code PUT} routes: operator only.
 * </ul>
 *
 * <p><b>Database contract the tests rely on</b> (Flyway migration {@code V4} of the implementer).
 * TEMPERATURE_HIGH, DOOR_OPEN and OFFLINE alerts cannot be created through the API in #25 (the
 * evaluator comes later), so {@link #seedAlert} inserts them with JDBC into table {@code alert}
 * with exactly these columns (text/varchar for the enums, {@code timestamptz} for instants, {@code
 * uuid} for ids); all other columns must be nullable or have a default (notification state {@code
 * PENDING} / 0 attempts):
 *
 * <pre>
 * alert(id uuid, machine_id uuid, type text, status text, opened_at timestamptz,
 *       acknowledged_at timestamptz, acknowledged_by uuid,
 *       resolved_at timestamptz, resolved_by uuid, resolution text)
 * </pre>
 *
 * A partial unique index must reject a second row with {@code status = 'ACTIVE'} for the same
 * {@code (machine_id, type)} (see {@code AlertSchemaTest}).
 *
 * <p>Instants are compared truncated to milliseconds; fixtures are relative to {@code now}.
 */
public abstract class AlertApiTest extends MonitoringApiTest {

  public static final String OTHER_OPERATOR_ID = "9c1d7a52-3e48-4b0f-a6d2-51f8e0b7c344";

  /** Required fields of every alert object (openapi {@code AlertOut.required}). */
  protected static final List<String> ALERT_FIELDS =
      List.of(
          "id",
          "machineId",
          "type",
          "status",
          "openedAt",
          "acknowledgedAt",
          "acknowledgedBy",
          "resolvedAt",
          "resolvedBy",
          "resolution",
          "resolutionNote",
          "notification");

  @Autowired protected JdbcTemplate jdbc;

  protected static String[] inventory() {
    return new String[] {"X-Service-Token", INVENTORY_TOKEN};
  }

  /** frontend-api: the gateway token without an identity header. */
  protected static String[] frontendApi() {
    return new String[] {"X-Service-Token", GATEWAY_TOKEN};
  }

  protected static String[] otherOperator() {
    return new String[] {"X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", OTHER_OPERATOR_ID};
  }

  protected static String slot(String code, String productId, int quantity, int threshold) {
    return """
    {"slotCode":"%s","productId":"%s","quantity":%d,"lowStockThreshold":%d}\
    """
        .formatted(code, productId, quantity, threshold);
  }

  protected static String newProduct() {
    return UUID.randomUUID().toString();
  }

  protected static String reportJson(String machineId, long version, String... slots) {
    return """
    {"machineId":"%s","type":"LOW_STOCK","version":%d,"slots":[%s]}\
    """
        .formatted(machineId, version, String.join(",", slots));
  }

  /** POST /v1/alerts as Inventory. */
  protected Http.Response report(String machineId, long version, String... slots) {
    return post("/v1/alerts", reportJson(machineId, version, slots), inventory());
  }

  /** Opens a LOW_STOCK alert (version given) and returns the alert object; fails fast otherwise. */
  protected Map<String, Object> openLowStock(String machineId, long version, String... slots) {
    Http.Response response = report(machineId, version, slots);
    if (response.status() != 201) {
      throw new AssertionError(
          "fixture POST /v1/alerts expected 201 but got "
              + response.status()
              + ": "
              + response.body());
    }
    return alertOf(response);
  }

  @SuppressWarnings("unchecked")
  protected static Map<String, Object> alertOf(Http.Response response) {
    return (Map<String, Object>) response.json().get("alert");
  }

  protected Http.Response put(String path, String json, String... headers) {
    if (json == null) {
      return Http.send(port, "PUT", path, null, headers);
    }
    String[] all = new String[headers.length + 2];
    all[0] = "Content-Type";
    all[1] = "application/json";
    System.arraycopy(headers, 0, all, 2, headers.length);
    return Http.send(port, "PUT", path, json, all);
  }

  protected Http.Response acknowledge(Object alertId, String... headers) {
    return put("/v1/alerts/" + alertId + "/acknowledgement", null, headers);
  }

  protected Http.Response acknowledge(Object alertId) {
    return acknowledge(alertId, operator());
  }

  protected Http.Response resolve(Object alertId, String json, String... headers) {
    return put("/v1/alerts/" + alertId + "/resolution", json, headers);
  }

  protected Http.Response resolve(Object alertId, String json) {
    return resolve(alertId, json, operator());
  }

  protected Http.Response listAlerts(String query) {
    return get("/v1/alerts" + (query.isEmpty() ? "" : "?" + query), operator());
  }

  /** All alerts of one machine, newest first (the list is the only read route). */
  @SuppressWarnings("unchecked")
  protected List<Map<String, Object>> alertsOf(String machineId) {
    Http.Response response = listAlerts("machineId=" + machineId + "&limit=200");
    assertThat(response.status()).isEqualTo(200);
    return (List<Map<String, Object>>) (List<?>) response.items();
  }

  protected Map<String, Object> find(String machineId, Object alertId) {
    return alertsOf(machineId).stream()
        .filter(a -> alertId.equals(a.get("id")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("alert " + alertId + " not listed"));
  }

  /** Inserts a row into {@code alert} (see the class comment); returns the new id. */
  protected String seedAlert(
      String machineId,
      String type,
      String status,
      Instant openedAt,
      Instant acknowledgedAt,
      String acknowledgedBy,
      Instant resolvedAt,
      String resolvedBy,
      String resolution) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO alert (id, machine_id, type, status, opened_at, acknowledged_at,"
            + " acknowledged_by, resolved_at, resolved_by, resolution)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        UUID.fromString(machineId),
        type,
        status,
        Timestamp.from(openedAt),
        acknowledgedAt == null ? null : Timestamp.from(acknowledgedAt),
        acknowledgedBy == null ? null : UUID.fromString(acknowledgedBy),
        resolvedAt == null ? null : Timestamp.from(resolvedAt),
        resolvedBy == null ? null : UUID.fromString(resolvedBy),
        resolution);
    return id.toString();
  }

  /** An active, unacknowledged alert of the given type opened a minute ago. */
  protected String seedActive(String machineId, String type) {
    return seedAlert(
        machineId,
        type,
        "ACTIVE",
        ago(java.time.Duration.ofMinutes(1)),
        null,
        null,
        null,
        null,
        null);
  }

  protected static Instant instant(Object json) {
    assertThat(json).isInstanceOf(String.class);
    return Instant.parse((String) json).truncatedTo(ChronoUnit.MILLIS);
  }
}
