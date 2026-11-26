package si.vending.monitoring.alarm;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.vending.monitoring.alert.AlertService;
import si.vending.monitoring.measurement.MeasurementDtos.DoorState;
import si.vending.monitoring.measurement.MeasurementDtos.MeasurementOut;
import si.vending.monitoring.measurement.MeasurementRepository;
import si.vending.monitoring.measurement.ThresholdService;
import si.vending.monitoring.measurement.ThresholdService.Thresholds;
import si.vending.monitoring.notification.NotificationOutbox;

/**
 * Alarm rules of docs/01 section 6.2 for TEMPERATURE_HIGH, DOOR_OPEN and OFFLINE.
 *
 * <p>Every rule is a pure function of the stored measurements, the machine's thresholds and "now"
 * (the injectable {@link Clock}); applying the verdict is idempotent SQL (at most one ACTIVE alarm
 * per machine and type is a database rule), so concurrent or repeated evaluations are harmless.
 *
 * <ul>
 *   <li>TEMPERATURE_HIGH is evaluated when a measurement arrives: opens after {@code
 *       consecutiveReadings} newest readings all above {@code maxTempC}, resolves (AUTO) when the
 *       newest reading is at most {@code resetTempC}; in between nothing changes.
 *   <li>OFFLINE and DOOR_OPEN are opened by the periodic job ({@link #evaluateTimeRules}) because
 *       they depend on time passing without a new measurement. An arriving measurement only
 *       resolves them.
 *   <li>A machine that never sent a measurement is not OFFLINE (reachability UNKNOWN).
 *   <li>An alarm resolved manually while the condition still holds is simply not ACTIVE any more,
 *       so the next evaluation of the condition opens a new one.
 * </ul>
 */
@Service
public class AlarmRules {

  public static final String TEMPERATURE_HIGH = "TEMPERATURE_HIGH";
  public static final String DOOR_OPEN = "DOOR_OPEN";
  public static final String OFFLINE = "OFFLINE";

  /** What a rule decided for the current data. */
  public enum Verdict {
    OPEN,
    RESOLVE,
    KEEP
  }

  private final MeasurementRepository measurements;
  private final ThresholdService thresholds;
  private final AlertService alerts;
  private final NotificationOutbox outbox;
  private final Clock clock;

  public AlarmRules(
      MeasurementRepository measurements,
      ThresholdService thresholds,
      AlertService alerts,
      NotificationOutbox outbox,
      Clock clock) {
    this.outbox = outbox;
    this.measurements = measurements;
    this.thresholds = thresholds;
    this.alerts = alerts;
    this.clock = clock;
  }

  /** After a measurement was stored: temperature rule, and auto-resolution of OFFLINE/DOOR_OPEN. */
  @Transactional
  public void onMeasurement(UUID machineId) {
    alerts.lockMachine(machineId);
    Thresholds t = thresholds.forMachine(machineId);
    Instant now = now();
    List<BigDecimal> temperatures =
        measurements.latestTemperatures(machineId, t.consecutiveReadings());
    apply(machineId, TEMPERATURE_HIGH, temperature(temperatures, t), now);
    Optional<MeasurementOut> latest = measurements.findLatest(machineId);
    if (offline(latest.map(MeasurementOut::observedAt).orElse(null), now, t) == Verdict.RESOLVE) {
      apply(machineId, OFFLINE, Verdict.RESOLVE, now);
    }
    if (latest.isPresent() && latest.get().doorState() == DoorState.CLOSED) {
      apply(machineId, DOOR_OPEN, Verdict.RESOLVE, now);
    }
  }

  /** The periodic evaluation of one machine: OFFLINE and DOOR_OPEN, open and resolve. */
  @Transactional
  public void evaluateTimeRules(UUID machineId) {
    alerts.lockMachine(machineId);
    Thresholds t = thresholds.forMachine(machineId);
    Instant now = now();
    Optional<MeasurementOut> latest = measurements.findLatest(machineId);
    apply(
        machineId,
        OFFLINE,
        offline(latest.map(MeasurementOut::observedAt).orElse(null), now, t),
        now);
    if (latest.isPresent()) {
      DoorState door = latest.get().doorState();
      Instant since =
          door == DoorState.OPEN ? measurements.openRunStart(machineId).orElse(null) : null;
      apply(machineId, DOOR_OPEN, doorOpen(door, since, now, t), now);
    }
  }

  /** Machines no longer ACTIVE (service, retired) keep no OFFLINE or DOOR_OPEN alarm. */
  @Transactional
  public int resolveForInactiveMachines(List<UUID> activeIds) {
    return alerts.resolveTimeAlarmsExcept(activeIds, now());
  }

  // ---- pure rules -------------------------------------------------------------------------

  /**
   * @param latestFirst temperatures of the newest readings, newest first
   */
  public static Verdict temperature(List<BigDecimal> latestFirst, Thresholds t) {
    if (latestFirst.isEmpty()) {
      return Verdict.KEEP;
    }
    if (latestFirst.get(0).compareTo(new BigDecimal(t.resetTempC())) <= 0) {
      return Verdict.RESOLVE;
    }
    BigDecimal max = new BigDecimal(t.maxTempC());
    if (latestFirst.size() >= t.consecutiveReadings()
        && latestFirst.stream()
            .limit(t.consecutiveReadings())
            .allMatch(temperature -> temperature.compareTo(max) > 0)) {
      return Verdict.OPEN;
    }
    return Verdict.KEEP;
  }

  /**
   * @param lastObservedAt observation time of the newest measurement, null if there is none
   */
  public static Verdict offline(Instant lastObservedAt, Instant now, Thresholds t) {
    if (lastObservedAt == null) {
      return Verdict.KEEP;
    }
    return Duration.between(lastObservedAt, now).compareTo(Duration.ofSeconds(t.noDataMaxSeconds()))
            > 0
        ? Verdict.OPEN
        : Verdict.RESOLVE;
  }

  /**
   * @param latestDoor door state of the newest measurement
   * @param openSince start of the uninterrupted OPEN run (null if the door is not open)
   */
  public static Verdict doorOpen(
      DoorState latestDoor, Instant openSince, Instant now, Thresholds t) {
    if (latestDoor == DoorState.OPEN
        && openSince != null
        && Duration.between(openSince, now).compareTo(Duration.ofSeconds(t.doorOpenMaxSeconds()))
            > 0) {
      return Verdict.OPEN;
    }
    return Verdict.RESOLVE;
  }

  // ---- applying -----------------------------------------------------------------------------

  private void apply(UUID machineId, String type, Verdict verdict, Instant now) {
    switch (verdict) {
      case OPEN -> open(machineId, type, now);
      case RESOLVE -> alerts.resolveAutomatic(machineId, type, now);
      case KEEP -> {}
    }
  }

  /**
   * The single point where a rule opens an alarm, inside the evaluation transaction. {@code opened}
   * is present only for the call that really created the alarm; the e-mail notification is enqueued
   * here, in the same transaction.
   */
  private void open(UUID machineId, String type, Instant now) {
    Optional<UUID> opened = alerts.openAutomatic(machineId, type, now);
    opened.ifPresent(alertId -> outbox.enqueue(alertId, now));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
