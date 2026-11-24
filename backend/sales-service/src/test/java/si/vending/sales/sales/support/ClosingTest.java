package si.vending.sales.sales.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import si.vending.sales.core.support.Http;

/**
 * Base for the daily closing tests (issue #55: {@code /v1/sales/closings}).
 *
 * <p>Shared contract: {@code ClosingIn}, {@code ClosingUpdate}, {@code ClosingOut}, {@code
 * ClosingList} and {@code ClosingsDeleted} of {@code backend/sales-service/openapi.yaml}. Sales
 * does not call Machine; a closing of an unknown machine is an empty DRAFT.
 *
 * <p>Isolation: the database is shared by the whole JVM and a closing may only be created for
 * yesterday (UTC), so every test class works on the same date. Closings of one machine use a random
 * {@code machineId} and never collide. The "all machines" closing is one global pair (yesterday,
 * null): the tests that create it first and last remove every DRAFT of yesterday with {@code DELETE
 * /v1/sales/closings?date=} ({@link #dropDrafts}), and never confirm an all-machines closing (a
 * confirmed one could not be removed again). Expected all-machine totals are computed from table
 * {@code sales} with SQL ({@link #oracle}), so sales that other test classes left on yesterday do
 * not matter. Fixture times are in milliseconds.
 */
public abstract class ClosingTest extends SalesReadTest {

  protected static LocalDate yesterday() {
    return LocalDate.now(ZoneOffset.UTC).minusDays(1);
  }

  protected static Instant dayStart(LocalDate date) {
    return date.atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  protected static Instant dayEnd(LocalDate date) {
    return dayStart(date.plusDays(1));
  }

  @BeforeEach
  void cleanBefore() {
    dropDrafts();
  }

  @AfterEach
  void cleanAfter() {
    dropDrafts();
  }

  /** Best effort: removes every DRAFT closing of yesterday (all scopes). */
  protected void dropDrafts() {
    deleteDate(yesterday().toString(), operator());
  }

  // --- requests

  protected static Map<String, Object> in(LocalDate date, UUID machineId, String note) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("date", date.toString());
    if (machineId != null) {
      body.put("machineId", machineId.toString());
    }
    if (note != null) {
      body.put("note", note);
    }
    return body;
  }

  protected Http.Response createClosing(Map<String, Object> body, String... caller) {
    return post("/v1/sales/closings", json(body), caller);
  }

  /** POST for yesterday and the machine ({@code null}: all machines), as operator. */
  protected Http.Response create(UUID machineId) {
    return createClosing(in(yesterday(), machineId, null), operator());
  }

  /** Creates a closing of yesterday and returns its body; fails the test unless it is a 201. */
  protected Map<String, Object> created(UUID machineId) {
    Http.Response response = create(machineId);
    assertThat(response.status()).as(response.body()).isEqualTo(201);
    return response.json();
  }

  protected Http.Response putClosing(Object id, Map<String, Object> body, String... caller) {
    return putRaw("/v1/sales/closings/" + id, json(body), caller);
  }

  protected Http.Response putRaw(String path, String raw, String... caller) {
    String[] all = new String[caller.length + 2];
    all[0] = "Content-Type";
    all[1] = "application/json";
    System.arraycopy(caller, 0, all, 2, caller.length);
    return Http.send(port, "PUT", path, raw, all);
  }

  protected static Map<String, Object> update(String status, String note) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (note != null) {
      body.put("note", note);
    }
    body.put("status", status);
    return body;
  }

  protected Http.Response confirm(Object id, String note) {
    return putClosing(id, update("CONFIRMED", note), operator());
  }

  protected Http.Response recompute(Object id, String note) {
    return putClosing(id, update("DRAFT", note), operator());
  }

  protected Http.Response deleteClosing(Object id, String... caller) {
    return Http.send(port, "DELETE", "/v1/sales/closings/" + id, null, caller);
  }

  protected Http.Response deleteDate(String date, String... caller) {
    return Http.send(
        port, "DELETE", "/v1/sales/closings" + (date == null ? "" : "?date=" + date), null, caller);
  }

  protected Http.Response getClosing(Object id) {
    return asOperator("/v1/sales/closings/" + id);
  }

  // --- fixtures

  protected Fx saleAt(UUID machineId, Instant at) {
    return insert(machineId, UUID.randomUUID(), 1, "2.50", "EUR", at, "APPLIED", null);
  }

  protected Fx saleOnYesterday(UUID machineId, int quantity, String price, String currency) {
    return insert(
        machineId,
        UUID.randomUUID(),
        quantity,
        price,
        currency,
        dayStart(yesterday()).plusSeconds(3600),
        "APPLIED",
        null);
  }

  protected Fx needsReconciliationOnYesterday(UUID machineId) {
    return insert(
        machineId,
        UUID.randomUUID(),
        dayStart(yesterday()).plusSeconds(7200),
        "NEEDS_RECONCILIATION");
  }

  // --- response helpers

  @SuppressWarnings("unchecked")
  protected static List<Map<String, Object>> totalsOf(Map<String, Object> closing) {
    return (List<Map<String, Object>>) closing.get("totals");
  }

  protected static Map<String, Object> eur(Map<String, Object> closing) {
    return totalsOf(closing).stream()
        .filter(t -> "EUR".equals(t.get("currency")))
        .findFirst()
        .orElse(null);
  }

  /**
   * Totals per currency computed from table sales: currency to [salesCount, quantity, turnover].
   */
  protected Map<String, List<Object>> oracle(LocalDate date) {
    Map<String, List<Object>> expected = new LinkedHashMap<>();
    jdbc.queryForList(
            "SELECT currency, count(*) AS c, sum(quantity) AS q,"
                + " sum(quantity * unit_price_paid) AS t FROM sales"
                + " WHERE occurred_at >= ? AND occurred_at < ? GROUP BY currency",
            OffsetDateTime.ofInstant(dayStart(date), ZoneOffset.UTC),
            OffsetDateTime.ofInstant(dayEnd(date), ZoneOffset.UTC))
        .forEach(row ->
            expected.put(
                ((String) row.get("currency")).trim(),
                List.of(
                    ((Number) row.get("c")).intValue(),
                    ((Number) row.get("q")).intValue(),
                    ((BigDecimal) row.get("t")).setScale(2).toPlainString())));
    return expected;
  }

  protected int oracleOpen(LocalDate date) {
    Integer open =
        jdbc.queryForObject(
            "SELECT count(*) FROM sales WHERE inventory_state = 'NEEDS_RECONCILIATION'"
                + " AND occurred_at >= ? AND occurred_at < ?",
            Integer.class,
            OffsetDateTime.ofInstant(dayStart(date), ZoneOffset.UTC),
            OffsetDateTime.ofInstant(dayEnd(date), ZoneOffset.UTC));
    return open == null ? -1 : open;
  }

  protected static void assertClosingShape(Map<String, Object> out) {
    assertThat(out.keySet())
        .contains(
            "id",
            "date",
            "status",
            "note",
            "totals",
            "openReconciliations",
            "createdAt",
            "createdBy",
            "updatedAt")
        .isSubsetOf(
            "id",
            "date",
            "machineId",
            "status",
            "note",
            "totals",
            "openReconciliations",
            "createdAt",
            "createdBy",
            "updatedAt",
            "confirmedAt",
            "confirmedBy");
    UUID.fromString((String) out.get("id"));
    assertThat(out.get("openReconciliations")).isInstanceOf(Integer.class);
    for (Map<String, Object> total : totalsOf(out)) {
      assertThat(total.keySet())
          .containsExactlyInAnyOrder("currency", "salesCount", "quantity", "turnover");
      assertThat(total.get("turnover")).asString().matches("-?\\d+\\.\\d{2}");
    }
  }
}
