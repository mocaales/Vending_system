package si.vending.monitoring.alert;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import si.vending.monitoring.alert.AlertDtos.AlertOut;
import si.vending.monitoring.alert.AlertDtos.LowStockSlot;
import si.vending.monitoring.alert.AlertDtos.Notification;

/**
 * Parameterised SQL over the {@code alert}, {@code alert_slot} and {@code alert_report_version}
 * tables.
 */
@Repository
public class AlertRepository {

  private static final String COLUMNS =
      """
      id, machine_id, type, status, opened_at, acknowledged_at, acknowledged_by, resolved_at,
      resolved_by, resolution, resolution_note, notification_status, notification_attempts
      """;

  private final JdbcClient jdbc;

  public AlertRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Makes sure the version row exists, then locks it until the end of the transaction. */
  public long lockReportVersion(UUID machineId) {
    jdbc.sql(
            "INSERT INTO alert_report_version (machine_id, version) VALUES (:machine, 0)"
                + " ON CONFLICT (machine_id) DO NOTHING")
        .param("machine", machineId)
        .update();
    return jdbc.sql(
            "SELECT version FROM alert_report_version WHERE machine_id = :machine FOR UPDATE")
        .param("machine", machineId)
        .query(Long.class)
        .single();
  }

  public void setReportVersion(UUID machineId, long version) {
    jdbc.sql("UPDATE alert_report_version SET version = :version WHERE machine_id = :machine")
        .param("version", version)
        .param("machine", machineId)
        .update();
  }

  /** The id of the active LOW_STOCK alert of the machine, if any. */
  public Optional<UUID> findActiveLowStockId(UUID machineId) {
    return jdbc.sql(
            "SELECT id FROM alert WHERE machine_id = :machine AND type = 'LOW_STOCK'"
                + " AND status = 'ACTIVE' FOR UPDATE")
        .param("machine", machineId)
        .query(UUID.class)
        .optional();
  }

  public void insertLowStock(UUID id, UUID machineId, Instant openedAt) {
    jdbc.sql(
            "INSERT INTO alert (id, machine_id, type, status, opened_at)"
                + " VALUES (:id, :machine, 'LOW_STOCK', 'ACTIVE', :opened)")
        .param("id", id)
        .param("machine", machineId)
        .param("opened", Timestamp.from(openedAt))
        .update();
  }

  public void replaceSlots(UUID alertId, List<LowStockSlot> slots) {
    jdbc.sql("DELETE FROM alert_slot WHERE alert_id = :id").param("id", alertId).update();
    int position = 0;
    for (LowStockSlot slot : slots) {
      jdbc.sql(
              "INSERT INTO alert_slot (alert_id, position, slot_code, product_id, quantity,"
                  + " low_stock_threshold) VALUES (:id, :pos, :code, :product, :quantity, :threshold)")
          .param("id", alertId)
          .param("pos", position++)
          .param("code", slot.slotCode())
          .param("product", slot.productId())
          .param("quantity", slot.quantity())
          .param("threshold", slot.lowStockThreshold())
          .update();
    }
  }

  public void resolveAuto(UUID alertId, Instant at) {
    jdbc.sql(
            "UPDATE alert SET status = 'RESOLVED', resolved_at = :at, resolved_by = NULL,"
                + " resolution = 'AUTO' WHERE id = :id")
        .param("at", Timestamp.from(at))
        .param("id", alertId)
        .update();
  }

  /**
   * Opens an alarm raised by a rule. The partial unique index on (machine, type) where ACTIVE is
   * the arbiter: false means an ACTIVE alarm of that type already exists (also under a race).
   */
  public boolean openIfAbsent(UUID id, UUID machineId, String type, Instant openedAt) {
    return jdbc.sql(
                "INSERT INTO alert (id, machine_id, type, status, opened_at)"
                    + " VALUES (:id, :machine, :type, 'ACTIVE', :opened)"
                    + " ON CONFLICT (machine_id, type) WHERE status = 'ACTIVE' DO NOTHING")
            .param("id", id)
            .param("machine", machineId)
            .param("type", type)
            .param("opened", Timestamp.from(openedAt))
            .update()
        == 1;
  }

  /** Serializes all rule evaluations of one machine until the end of the transaction. */
  public void lockMachine(UUID machineId) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:machine, 0))")
        .param("machine", machineId.toString())
        .query()
        .singleRow();
  }

  /** AUTO-resolves ACTIVE OFFLINE and DOOR_OPEN alarms of machines outside the given ids. */
  public int resolveTimeAlarmsExcept(List<UUID> keepIds, Instant at) {
    String except = keepIds.isEmpty() ? "" : " AND machine_id NOT IN (:ids)";
    var statement =
        jdbc.sql(
                "UPDATE alert SET status = 'RESOLVED', resolved_at = :at, resolved_by = NULL,"
                    + " resolution = 'AUTO' WHERE status = 'ACTIVE'"
                    + " AND type IN ('OFFLINE', 'DOOR_OPEN')"
                    + except)
            .param("at", Timestamp.from(at));
    if (!keepIds.isEmpty()) {
      statement = statement.param("ids", keepIds);
    }
    return statement.update();
  }

  /** Resolves the ACTIVE alarm of the machine and type, if any, as AUTO. */
  public boolean resolveActiveAuto(UUID machineId, String type, Instant at) {
    return jdbc.sql(
                "UPDATE alert SET status = 'RESOLVED', resolved_at = :at, resolved_by = NULL,"
                    + " resolution = 'AUTO' WHERE machine_id = :machine AND type = :type"
                    + " AND status = 'ACTIVE'")
            .param("at", Timestamp.from(at))
            .param("machine", machineId)
            .param("type", type)
            .update()
        > 0;
  }

  public void resolveManual(UUID alertId, Instant at, UUID operatorId, String note) {
    jdbc.sql(
            "UPDATE alert SET status = 'RESOLVED', resolved_at = :at, resolved_by = :operator,"
                + " resolution = 'MANUAL', resolution_note = :note WHERE id = :id")
        .param("at", Timestamp.from(at))
        .param("operator", operatorId)
        .param("note", note)
        .param("id", alertId)
        .update();
  }

  public void acknowledge(UUID alertId, Instant at, UUID operatorId) {
    jdbc.sql("UPDATE alert SET acknowledged_at = :at, acknowledged_by = :operator WHERE id = :id")
        .param("at", Timestamp.from(at))
        .param("operator", operatorId)
        .param("id", alertId)
        .update();
  }

  public Optional<AlertOut> find(UUID id, boolean lock) {
    Optional<AlertOut> found =
        jdbc.sql("SELECT " + COLUMNS + " FROM alert WHERE id = :id" + (lock ? " FOR UPDATE" : ""))
            .param("id", id)
            .query(AlertRepository::map)
            .optional();
    return found.map(a -> withSlots(List.of(a)).get(0));
  }

  public long count(UUID machineId, String status, Boolean acknowledged, String type) {
    Map<String, Object> params = new HashMap<>();
    String where = where(machineId, status, acknowledged, type, params);
    return jdbc.sql("SELECT count(*) FROM alert" + where).params(params).query(Long.class).single();
  }

  public List<AlertOut> list(
      UUID machineId, String status, Boolean acknowledged, String type, int limit, int offset) {
    Map<String, Object> params = new HashMap<>();
    String where = where(machineId, status, acknowledged, type, params);
    List<AlertOut> page =
        jdbc.sql(
                "SELECT "
                    + COLUMNS
                    + " FROM alert"
                    + where
                    + " ORDER BY opened_at DESC, id DESC LIMIT :limit OFFSET :offset")
            .params(params)
            .param("limit", limit)
            .param("offset", offset)
            .query(AlertRepository::map)
            .list();
    return withSlots(page);
  }

  /** Builds the WHERE clause from fixed fragments; every value is a bind parameter. */
  private static String where(
      UUID machineId,
      String status,
      Boolean acknowledged,
      String type,
      Map<String, Object> params) {
    List<String> conditions = new ArrayList<>();
    if (machineId != null) {
      conditions.add("machine_id = :machine");
      params.put("machine", machineId);
    }
    if (status != null) {
      conditions.add("status = :status");
      params.put("status", status);
    }
    if (acknowledged != null) {
      conditions.add(acknowledged ? "acknowledged_at IS NOT NULL" : "acknowledged_at IS NULL");
    }
    if (type != null) {
      conditions.add("type = :type");
      params.put("type", type);
    }
    return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
  }

  /** Adds the slot list to the LOW_STOCK alerts of the given page (one query for the page). */
  private List<AlertOut> withSlots(List<AlertOut> alerts) {
    List<UUID> lowStockIds =
        alerts.stream().filter(a -> "LOW_STOCK".equals(a.type())).map(AlertOut::id).toList();
    if (lowStockIds.isEmpty()) {
      return alerts;
    }
    Map<UUID, List<LowStockSlot>> slots = new HashMap<>();
    jdbc.sql(
            "SELECT alert_id, slot_code, product_id, quantity, low_stock_threshold"
                + " FROM alert_slot WHERE alert_id IN (:ids) ORDER BY alert_id, position")
        .param("ids", lowStockIds)
        .query((rs, row) -> {
          slots
              .computeIfAbsent(rs.getObject("alert_id", UUID.class), k -> new ArrayList<>())
              .add(
                  new LowStockSlot(
                      rs.getString("slot_code"),
                      rs.getObject("product_id", UUID.class),
                      rs.getInt("quantity"),
                      rs.getInt("low_stock_threshold")));
          return null;
        })
        .list();
    return alerts.stream()
        .map(a ->
            "LOW_STOCK".equals(a.type()) ? withSlots(a, slots.getOrDefault(a.id(), List.of())) : a)
        .toList();
  }

  private static AlertOut withSlots(AlertOut a, List<LowStockSlot> slots) {
    return new AlertOut(
        a.id(),
        a.machineId(),
        a.type(),
        a.status(),
        a.openedAt(),
        a.acknowledgedAt(),
        a.acknowledgedBy(),
        a.resolvedAt(),
        a.resolvedBy(),
        a.resolution(),
        a.resolutionNote(),
        a.notification(),
        slots);
  }

  private static AlertOut map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new AlertOut(
        rs.getObject("id", UUID.class),
        rs.getObject("machine_id", UUID.class),
        rs.getString("type"),
        rs.getString("status"),
        instant(rs, "opened_at"),
        instant(rs, "acknowledged_at"),
        rs.getObject("acknowledged_by", UUID.class),
        instant(rs, "resolved_at"),
        rs.getObject("resolved_by", UUID.class),
        rs.getString("resolution"),
        rs.getString("resolution_note"),
        new Notification(rs.getString("notification_status"), rs.getInt("notification_attempts")),
        null);
  }

  private static Instant instant(java.sql.ResultSet rs, String column)
      throws java.sql.SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }
}
