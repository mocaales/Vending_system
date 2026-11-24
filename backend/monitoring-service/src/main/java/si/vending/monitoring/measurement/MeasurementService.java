package si.vending.monitoring.measurement;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.vending.monitoring.core.ApiException;
import si.vending.monitoring.core.Page;
import si.vending.monitoring.measurement.MeasurementDtos.DoorState;
import si.vending.monitoring.measurement.MeasurementDtos.MachineMonitoringStatus;
import si.vending.monitoring.measurement.MeasurementDtos.MeasurementIn;
import si.vending.monitoring.measurement.MeasurementDtos.MeasurementOut;
import si.vending.monitoring.measurement.MeasurementDtos.PurgeResult;
import si.vending.monitoring.measurement.MeasurementDtos.Reachability;

@Service
public class MeasurementService {

  static final Duration MAX_FUTURE = Duration.ofMinutes(5);
  static final Duration MAX_WINDOW = Duration.ofDays(7);
  static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

  /**
   * No instant before this is accepted (observedAt, list from/to, purge before). Checked before any
   * call or database conversion, so extreme years are a 422, never a 500.
   */
  public static final Instant EARLIEST_INSTANT = Instant.parse("2020-01-01T00:00:00Z");

  /** Result of an ingest: the stored measurement and whether this call created it. */
  public record Ingested(MeasurementOut measurement, boolean created) {}

  private final MeasurementRepository repository;
  private final MachineClient machines;
  private final ThresholdService thresholds;

  public MeasurementService(
      MeasurementRepository repository, MachineClient machines, ThresholdService thresholds) {
    this.repository = repository;
    this.machines = machines;
    this.thresholds = thresholds;
  }

  public Ingested ingest(MeasurementIn in, UUID headerMachineId) {
    UUID machineId = UUID.fromString(in.machineId());
    if (!machineId.equals(headerMachineId)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "MACHINE_KEY_MISMATCH",
          "The machineId in the body does not match the machine of the key.");
    }
    Instant now = Instant.now();
    Instant observedAt = parseInstant(in.observedAt());
    requireNotTooEarly("observedAt", observedAt);
    if (observedAt.isAfter(now.plus(MAX_FUTURE))) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "TIMESTAMP_OUT_OF_RANGE",
          "observedAt is more than 5 minutes in the future.");
    }
    if (!machines.exists(machineId)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY, "REFERENCE_NOT_FOUND", "The machine does not exist.");
    }
    MeasurementOut candidate =
        new MeasurementOut(
            UUID.fromString(in.measurementId()),
            machineId,
            observedAt,
            new BigDecimal(in.temperatureC()).toPlainString(),
            DoorState.valueOf(in.doorState()),
            now.truncatedTo(ChronoUnit.MICROS));
    // The insert is the single arbiter: of two concurrent identical posts exactly one creates the
    // row, the other sees it taken and falls through to the comparison below.
    if (repository.insertIfAbsent(candidate)) {
      return new Ingested(candidate, true);
    }
    MeasurementOut stored =
        repository
            .findById(candidate.measurementId())
            .orElseThrow(() -> new IllegalStateException("measurement vanished after conflict"));
    if (!sameContent(stored, candidate)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "MEASUREMENT_ID_CONFLICT",
          "This measurementId was already stored with different content.");
    }
    return new Ingested(stored, false);
  }

  @Transactional(readOnly = true)
  public Page<MeasurementOut> list(
      UUID machineId, Instant from, Instant to, int limit, int offset) {
    requireNotTooEarly("from", from);
    requireNotTooEarly("to", to);
    requireMachine(machineId);
    Instant end = to != null ? to : Instant.now();
    Instant start = from != null ? from : end.minus(DEFAULT_WINDOW);
    if (start.isAfter(end)) {
      throw invalid("from must not be after to.");
    }
    if (Duration.between(start, end).compareTo(MAX_WINDOW) > 0) {
      throw invalid("The window must not be longer than 7 days.");
    }
    return new Page<>(
        repository.list(machineId, start, end, limit, offset),
        repository.count(machineId, start, end),
        limit,
        offset);
  }

  @Transactional(readOnly = true)
  public MachineMonitoringStatus status(UUID machineId) {
    requireMachine(machineId);
    MeasurementOut last = repository.findLatest(machineId).orElse(null);
    Reachability reachability = Reachability.UNKNOWN;
    if (last != null) {
      Duration limit = Duration.ofSeconds(thresholds.forMachine(machineId).noDataMaxSeconds());
      reachability =
          Duration.between(last.observedAt(), Instant.now()).compareTo(limit) <= 0
              ? Reachability.ONLINE
              : Reachability.OFFLINE;
    }
    return new MachineMonitoringStatus(machineId, reachability, last);
  }

  public PurgeResult purge(UUID machineId, Instant before) {
    if (before == null) {
      throw invalid("before is required.");
    }
    requireNotTooEarly("before", before);
    requireMachine(machineId);
    if (!before.isBefore(Instant.now().minus(MAX_WINDOW))) {
      throw invalid("before must be more than 7 days in the past.");
    }
    return new PurgeResult(repository.deleteBefore(machineId, before));
  }

  private void requireMachine(UUID machineId) {
    if (!machines.exists(machineId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "MACHINE_NOT_FOUND", "Machine not found.");
    }
  }

  private static void requireNotTooEarly(String field, Instant value) {
    if (value != null && value.isBefore(EARLIEST_INSTANT)) {
      throw ApiException.invalidField(field, "must not be before " + EARLIEST_INSTANT + ".");
    }
  }

  private static boolean sameContent(MeasurementOut a, MeasurementOut b) {
    return a.machineId().equals(b.machineId())
        && a.observedAt()
            .truncatedTo(ChronoUnit.MICROS)
            .equals(b.observedAt().truncatedTo(ChronoUnit.MICROS))
        && a.doorState() == b.doorState()
        && new BigDecimal(a.temperatureC()).compareTo(new BigDecimal(b.temperatureC())) == 0;
  }

  private static Instant parseInstant(String text) {
    try {
      return OffsetDateTime.parse(text).toInstant().truncatedTo(ChronoUnit.MICROS);
    } catch (DateTimeParseException e) {
      throw invalid("observedAt is not a valid instant.");
    }
  }

  private static ApiException invalid(String message) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message);
  }
}
