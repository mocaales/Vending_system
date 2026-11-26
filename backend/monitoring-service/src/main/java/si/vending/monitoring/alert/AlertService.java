package si.vending.monitoring.alert;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.vending.monitoring.alert.AlertDtos.AlertOut;
import si.vending.monitoring.alert.AlertDtos.LowStockReport;
import si.vending.monitoring.alert.AlertDtos.LowStockSlot;
import si.vending.monitoring.alert.AlertDtos.ReportResult;
import si.vending.monitoring.core.ApiException;
import si.vending.monitoring.core.Page;
import si.vending.monitoring.measurement.MachineClient;
import si.vending.monitoring.notification.NotificationOutbox;
import tools.jackson.databind.JsonNode;

@Service
public class AlertService {

  static final int MAX_SLOTS = 100;
  static final int MAX_SLOT_CODE = 8;
  static final int MAX_NOTE = 500;
  static final long MAX_VERSION = 9007199254740991L;
  static final int MAX_COUNT = 1000;

  private static final Pattern SLOT_CODE_RE = Pattern.compile("^[A-Za-z0-9_-]{1,8}$");
  private static final Pattern CONTROL_RE = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

  private static final Pattern UUID_RE =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private static final Set<String> REPORT_FIELDS = Set.of("machineId", "type", "version", "slots");
  private static final Set<String> SLOT_FIELDS =
      Set.of("slotCode", "productId", "quantity", "lowStockThreshold");
  private static final Set<String> RESOLVABLE = Set.of("TEMPERATURE_HIGH", "DOOR_OPEN", "OFFLINE");

  private final AlertRepository repository;
  private final MachineClient machines;
  private final NotificationOutbox outbox;
  private final TransactionTemplate tx;

  public AlertService(
      AlertRepository repository,
      MachineClient machines,
      NotificationOutbox outbox,
      PlatformTransactionManager txManager) {
    this.repository = repository;
    this.outbox = outbox;
    this.machines = machines;
    this.tx = new TransactionTemplate(txManager);
  }

  /**
   * Applies a low-stock report. The body is validated and the machine looked up first; the state
   * change is one transaction serialized per machine by the lock on its version row.
   */
  public ReportResult report(JsonNode body) {
    LowStockReport report = parseReport(body);
    if (!machines.exists(report.machineId())) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY, "REFERENCE_NOT_FOUND", "The machine does not exist.");
    }
    return tx.execute(status -> apply(report));
  }

  private ReportResult apply(LowStockReport report) {
    UUID machineId = report.machineId();
    long last = repository.lockReportVersion(machineId);
    Optional<UUID> active = repository.findActiveLowStockId(machineId);
    if (report.version() <= last) {
      return new ReportResult(
          "IGNORED", active.flatMap(id -> repository.find(id, false)).orElse(null));
    }
    repository.setReportVersion(machineId, report.version());
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    if (report.slots().isEmpty()) {
      if (active.isEmpty()) {
        return new ReportResult("NO_CHANGE", null);
      }
      repository.resolveAuto(active.get(), now);
      return new ReportResult("RESOLVED", repository.find(active.get(), false).orElseThrow());
    }
    if (active.isPresent()) {
      repository.replaceSlots(active.get(), report.slots());
      return new ReportResult("UPDATED", repository.find(active.get(), false).orElseThrow());
    }
    UUID id = UUID.randomUUID();
    repository.insertLowStock(id, machineId, now);
    repository.replaceSlots(id, report.slots());
    outbox.enqueue(id, now);
    return new ReportResult("OPENED", repository.find(id, false).orElseThrow());
  }

  /**
   * The single hook where a rule opens an alarm (TEMPERATURE_HIGH, DOOR_OPEN, OFFLINE). Must run
   * inside the caller's transaction. Returns the id of the alarm only if this call opened it, empty
   * if an ACTIVE alarm of that type already exists; the caller enqueues the e-mail notification
   * when the result is present, in the same transaction.
   */
  public Optional<UUID> openAutomatic(UUID machineId, String type, Instant now) {
    UUID id = UUID.randomUUID();
    return repository.openIfAbsent(id, machineId, type, now) ? Optional.of(id) : Optional.empty();
  }

  /** Resolves the ACTIVE alarm of the machine and type as AUTO; true if there was one. */
  public boolean resolveAutomatic(UUID machineId, String type, Instant now) {
    return repository.resolveActiveAuto(machineId, type, now);
  }

  /** Takes the per-machine evaluation lock (transaction scoped). */
  public void lockMachine(UUID machineId) {
    repository.lockMachine(machineId);
  }

  /** AUTO-resolves OFFLINE and DOOR_OPEN alarms of machines that are not in {@code activeIds}. */
  public int resolveTimeAlarmsExcept(List<UUID> activeIds, Instant now) {
    return repository.resolveTimeAlarmsExcept(activeIds, now);
  }

  public Page<AlertOut> list(
      UUID machineId, String status, Boolean acknowledged, String type, int limit, int offset) {
    return tx.execute(s ->
        new Page<>(
            repository.list(machineId, status, acknowledged, type, limit, offset),
            repository.count(machineId, status, acknowledged, type),
            limit,
            offset));
  }

  public AlertOut acknowledge(UUID id, UUID operatorId) {
    return tx.execute(s -> {
      AlertOut alert = lock(id);
      if ("RESOLVED".equals(alert.status())) {
        throw new ApiException(
            HttpStatus.CONFLICT, "ALERT_NOT_ACTIVE", "The alert is already resolved.");
      }
      if (alert.acknowledgedAt() == null) {
        repository.acknowledge(id, Instant.now().truncatedTo(ChronoUnit.MICROS), operatorId);
        return repository.find(id, false).orElseThrow();
      }
      return alert;
    });
  }

  /** The alert is looked up first, so an unknown alert is 404 whatever the body looks like. */
  public AlertOut resolve(UUID id, UUID operatorId, JsonNode body) {
    return tx.execute(s -> {
      AlertOut alert = lock(id);
      String note = parseNote(body);
      if (!RESOLVABLE.contains(alert.type())) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "ALERT_TYPE_NOT_RESOLVABLE",
            "This alert type is resolved only by its own source.");
      }
      if ("RESOLVED".equals(alert.status())) {
        return alert;
      }
      repository.resolveManual(id, Instant.now().truncatedTo(ChronoUnit.MICROS), operatorId, note);
      return repository.find(id, false).orElseThrow();
    });
  }

  private AlertOut lock(UUID id) {
    return repository
        .find(id, true)
        .orElseThrow(
            () -> new ApiException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "Alert not found."));
  }

  private static String parseNote(JsonNode body) {
    if (body == null || body.isNull() || body.isMissingNode()) {
      return null;
    }
    if (!body.isObject()) {
      throw ApiException.invalidField("body", "must be a JSON object.");
    }
    rejectUnknown(body, Set.of("note"), "");
    JsonNode note = body.get("note");
    if (note == null || note.isNull()) {
      return null;
    }
    if (!note.isString()) {
      throw ApiException.invalidField("note", "must be a string.");
    }
    String value = note.asString().strip();
    if (CONTROL_RE.matcher(value).find()) {
      throw ApiException.invalidField("note", "must not contain control characters.");
    }
    if (value.length() > MAX_NOTE) {
      throw ApiException.invalidField("note", "must be at most " + MAX_NOTE + " characters.");
    }
    return value.isEmpty() ? null : value;
  }

  private static LowStockReport parseReport(JsonNode body) {
    if (body == null || !body.isObject()) {
      throw ApiException.invalidField("body", "must be a JSON object.");
    }
    rejectUnknown(body, REPORT_FIELDS, "");
    UUID machineId = uuid(body, "machineId", "machineId");
    JsonNode type = body.get("type");
    if (type == null || !type.isString() || !"LOW_STOCK".equals(type.asString())) {
      throw ApiException.invalidField("type", "must be LOW_STOCK.");
    }
    JsonNode version = body.get("version");
    if (version == null || !version.isIntegralNumber() || !version.canConvertToLong()) {
      throw ApiException.invalidField("version", "must be an integer.");
    }
    if (version.longValue() < 1) {
      throw ApiException.invalidField("version", "must be at least 1.");
    }
    if (version.longValue() > MAX_VERSION) {
      throw ApiException.invalidField("version", "must be at most " + MAX_VERSION + ".");
    }
    JsonNode slotsNode = body.get("slots");
    if (slotsNode == null || !slotsNode.isArray()) {
      throw ApiException.invalidField("slots", "must be an array.");
    }
    if (slotsNode.size() > MAX_SLOTS) {
      throw ApiException.invalidField("slots", "must have at most " + MAX_SLOTS + " items.");
    }
    List<LowStockSlot> slots = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < slotsNode.size(); i++) {
      LowStockSlot slot = parseSlot(slotsNode.get(i), "slots[" + i + "]");
      if (!seen.add(slot.slotCode())) {
        throw ApiException.invalidField("slots", "must not repeat a slot code.");
      }
      slots.add(slot);
    }
    return new LowStockReport(machineId, version.longValue(), slots);
  }

  private static LowStockSlot parseSlot(JsonNode node, String path) {
    if (!node.isObject()) {
      throw ApiException.invalidField(path, "must be a JSON object.");
    }
    rejectUnknown(node, SLOT_FIELDS, path + ".");
    JsonNode code = node.get("slotCode");
    if (code == null || !code.isString() || !SLOT_CODE_RE.matcher(code.asString()).matches()) {
      throw ApiException.invalidField(
          path + ".slotCode",
          "must be 1 to " + MAX_SLOT_CODE + " letters, digits, underscores or hyphens.");
    }
    return new LowStockSlot(
        code.asString(),
        uuid(node, "productId", path + ".productId"),
        count(node, "quantity", path + ".quantity"),
        count(node, "lowStockThreshold", path + ".lowStockThreshold"));
  }

  private static UUID uuid(JsonNode node, String field, String path) {
    JsonNode value = node.get(field);
    if (value == null || !value.isString() || !UUID_RE.matcher(value.asString()).matches()) {
      throw ApiException.invalidField(path, "must be a UUID.");
    }
    return UUID.fromString(value.asString());
  }

  private static int count(JsonNode node, String field, String path) {
    JsonNode value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
      throw ApiException.invalidField(path, "must be an integer.");
    }
    if (value.intValue() < 0) {
      throw ApiException.invalidField(path, "must not be negative.");
    }
    if (value.intValue() > MAX_COUNT) {
      throw ApiException.invalidField(path, "must be at most " + MAX_COUNT + ".");
    }
    return value.intValue();
  }

  private static void rejectUnknown(JsonNode node, Set<String> allowed, String prefix) {
    for (Iterator<String> names = node.propertyNames().iterator(); names.hasNext(); ) {
      String name = names.next();
      if (!allowed.contains(name)) {
        throw ApiException.invalidField(prefix + name, "Unknown field.");
      }
    }
  }
}
