package si.vending.sales.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import si.vending.sales.sales.ClosingDtos.ClosingStatus;
import si.vending.sales.sales.SaleDtos.Totals;

/**
 * SQL of the closings. Every value is a bound parameter; the WHERE clauses are built only from
 * fixed fragments. Totals are computed in SQL with {@code numeric}.
 */
@Repository
public class ClosingRepository {

  /** A stored closing without its totals. */
  public record Row(
      UUID id,
      LocalDate date,
      UUID machineId,
      ClosingStatus status,
      String note,
      int openReconciliations,
      Instant createdAt,
      UUID createdBy,
      Instant updatedAt,
      Instant confirmedAt,
      UUID confirmedBy) {}

  /** Totals of the sales in a scope plus the count of those still needing reconciliation. */
  public record Snapshot(List<Totals> totals, int openReconciliations) {}

  /** Optional list filters, AND-ed; {@code from} and {@code to} are inclusive. */
  public record Filter(LocalDate from, LocalDate to, UUID machineId, ClosingStatus status) {}

  private static final String COLUMNS =
      "id, closing_date, machine_id, status, note, open_reconciliations, created_at, created_by,"
          + " updated_at, confirmed_at, confirmed_by";

  private final JdbcClient jdbc;

  public ClosingRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  /**
   * Totals per currency of the sales whose {@code occurred_at} lies in the UTC day, for the machine
   * or (null) all machines, from one statement so that totals and the open count agree.
   */
  public Snapshot snapshot(LocalDate date, UUID machineId) {
    Instant from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant to = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    int[] open = {0};
    List<Totals> totals =
        jdbc.sql(
                "SELECT currency, count(*) AS sales_count, sum(quantity) AS quantity,"
                    + " sum(quantity * unit_price_paid) AS turnover,"
                    + " count(*) FILTER (WHERE inventory_state = 'NEEDS_RECONCILIATION') AS open"
                    + " FROM sales WHERE occurred_at >= :from AND occurred_at < :to"
                    + (machineId == null ? "" : " AND machine_id = :machineId")
                    + " GROUP BY currency ORDER BY currency")
            .params(scope(from, to, machineId))
            .query((rs, i) -> {
              open[0] += rs.getInt("open");
              return new Totals(
                  rs.getString("currency").strip(),
                  rs.getLong("sales_count"),
                  rs.getLong("quantity"),
                  money(rs.getBigDecimal("turnover")));
            })
            .list();
    return new Snapshot(totals, open[0]);
  }

  private static Map<String, Object> scope(Instant from, Instant to, UUID machineId) {
    Map<String, Object> params = new HashMap<>();
    params.put("from", utc(from));
    params.put("to", utc(to));
    if (machineId != null) {
      params.put("machineId", machineId);
    }
    return params;
  }

  private static String money(BigDecimal value) {
    return value.setScale(2).toPlainString();
  }

  /** Inserts a DRAFT; returns false if the (date, machine) pair already has a closing. */
  public boolean insertDraft(
      UUID id, LocalDate date, UUID machineId, String note, int open, UUID by, Instant now) {
    return jdbc.sql(
                "INSERT INTO sales_closing (id, closing_date, machine_id, status, note,"
                    + " open_reconciliations, created_at, created_by, updated_at)"
                    + " VALUES (:id, :date, CAST(:machineId AS uuid), 'DRAFT', :note, :open,"
                    + " :now, :by, :now) ON CONFLICT DO NOTHING")
            .param("id", id)
            .param("date", date)
            .param("machineId", machineId)
            .param("note", note)
            .param("open", open)
            .param("now", utc(now))
            .param("by", by)
            .update()
        == 1;
  }

  public void insertTotals(UUID closingId, List<Totals> totals) {
    for (Totals t : totals) {
      jdbc.sql(
              "INSERT INTO sales_closing_total (closing_id, currency, sales_count, quantity,"
                  + " turnover) VALUES (:id, :currency, :count, :quantity, :turnover)")
          .param("id", closingId)
          .param("currency", t.currency())
          .param("count", t.salesCount())
          .param("quantity", t.quantity())
          .param("turnover", new BigDecimal(t.turnover()))
          .update();
    }
  }

  public void deleteTotals(UUID closingId) {
    jdbc.sql("DELETE FROM sales_closing_total WHERE closing_id = :id")
        .param("id", closingId)
        .update();
  }

  public Optional<Row> find(UUID id) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM sales_closing WHERE id = :id")
        .param("id", id)
        .query(ClosingRepository::row)
        .optional();
  }

  /** Locks the row until the end of the transaction; concurrent writers of it wait. */
  public Optional<Row> findForUpdate(UUID id) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM sales_closing WHERE id = :id FOR UPDATE")
        .param("id", id)
        .query(ClosingRepository::row)
        .optional();
  }

  public List<Totals> totalsOf(UUID closingId) {
    return totalsOf(List.of(closingId)).getOrDefault(closingId, List.of());
  }

  public Map<UUID, List<Totals>> totalsOf(List<UUID> closingIds) {
    Map<UUID, List<Totals>> result = new HashMap<>();
    if (closingIds.isEmpty()) {
      return result;
    }
    jdbc.sql(
            "SELECT closing_id, currency, sales_count, quantity, turnover FROM sales_closing_total"
                + " WHERE closing_id IN (:ids) ORDER BY currency")
        .param("ids", closingIds)
        .query((rs, i) -> {
          result
              .computeIfAbsent(rs.getObject("closing_id", UUID.class), k -> new ArrayList<>())
              .add(
                  new Totals(
                      rs.getString("currency").strip(),
                      rs.getLong("sales_count"),
                      rs.getLong("quantity"),
                      money(rs.getBigDecimal("turnover"))));
          return null;
        })
        .list();
    return result;
  }

  /** Replaces note and open count of a DRAFT; the status stays. */
  public void updateDraft(UUID id, String note, int open, Instant now) {
    jdbc.sql(
            "UPDATE sales_closing SET note = :note, open_reconciliations = :open,"
                + " updated_at = :now WHERE id = :id")
        .param("id", id)
        .param("note", note)
        .param("open", open)
        .param("now", utc(now))
        .update();
  }

  public void confirm(UUID id, String note, int open, UUID by, Instant now) {
    jdbc.sql(
            "UPDATE sales_closing SET status = 'CONFIRMED', note = :note,"
                + " open_reconciliations = :open, updated_at = :now, confirmed_at = :now,"
                + " confirmed_by = :by WHERE id = :id")
        .param("id", id)
        .param("note", note)
        .param("open", open)
        .param("now", utc(now))
        .param("by", by)
        .update();
  }

  /** Deletes the closing if it is a DRAFT; returns the number of deleted rows (0 or 1). */
  public int deleteDraft(UUID id) {
    return jdbc.sql("DELETE FROM sales_closing WHERE id = :id AND status = 'DRAFT'")
        .param("id", id)
        .update();
  }

  public int deleteDraftsOf(LocalDate date) {
    return jdbc.sql("DELETE FROM sales_closing WHERE closing_date = :date AND status = 'DRAFT'")
        .param("date", date)
        .update();
  }

  public long count(Filter filter) {
    Map<String, Object> params = new HashMap<>();
    String where = where(filter, params);
    return jdbc.sql("SELECT count(*) FROM sales_closing" + where)
        .params(params)
        .query(Long.class)
        .single();
  }

  /** Newest date first; ties by creation time and id so that paging is deterministic. */
  public List<Row> page(Filter filter, int limit, int offset) {
    Map<String, Object> params = new HashMap<>();
    String where = where(filter, params);
    params.put("limit", limit);
    params.put("offset", offset);
    return jdbc.sql(
            "SELECT "
                + COLUMNS
                + " FROM sales_closing"
                + where
                + " ORDER BY closing_date DESC, created_at DESC, id LIMIT :limit OFFSET :offset")
        .params(params)
        .query(ClosingRepository::row)
        .list();
  }

  private static String where(Filter f, Map<String, Object> params) {
    List<String> conditions = new ArrayList<>();
    if (f.from() != null) {
      conditions.add("closing_date >= :from");
      params.put("from", f.from());
    }
    if (f.to() != null) {
      conditions.add("closing_date <= :to");
      params.put("to", f.to());
    }
    if (f.machineId() != null) {
      conditions.add("machine_id = :machineId");
      params.put("machineId", f.machineId());
    }
    if (f.status() != null) {
      conditions.add("status = :status");
      params.put("status", f.status().name());
    }
    return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
  }

  private static Row row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
    OffsetDateTime confirmedAt = rs.getObject("confirmed_at", OffsetDateTime.class);
    return new Row(
        rs.getObject("id", UUID.class),
        rs.getObject("closing_date", LocalDate.class),
        rs.getObject("machine_id", UUID.class),
        ClosingStatus.valueOf(rs.getString("status")),
        rs.getString("note"),
        rs.getInt("open_reconciliations"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("created_by", UUID.class),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
        confirmedAt == null ? null : confirmedAt.toInstant(),
        rs.getObject("confirmed_by", UUID.class));
  }
}
