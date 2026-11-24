package si.vending.monitoring.measurement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

/** Request and response models of the measurement routes. */
public final class MeasurementDtos {

  static final String UUID_RE =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
  static final String INSTANT_RE =
      "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?(Z|[+-]\\d{2}:\\d{2})$";
  static final String DECIMAL_RE = "^-?\\d{1,3}(\\.\\d{1,6})?$";

  private MeasurementDtos() {}

  /** {@code POST /v1/measurements} body; unknown fields are rejected by Jackson. */
  public record MeasurementIn(
      @NotNull @Pattern(regexp = UUID_RE, message = "must be a UUID") String measurementId,
      @NotNull @Pattern(regexp = UUID_RE, message = "must be a UUID") String machineId,
      @NotNull @Pattern(regexp = INSTANT_RE, message = "must be an ISO-8601 instant")
          String observedAt,
      @NotNull @Pattern(regexp = DECIMAL_RE, message = "must be a decimal string")
          String temperatureC,
      @NotNull @Pattern(regexp = "^(OPEN|CLOSED)$", message = "must be OPEN or CLOSED")
          String doorState) {}

  public record MeasurementOut(
      UUID measurementId,
      UUID machineId,
      Instant observedAt,
      String temperatureC,
      DoorState doorState,
      Instant receivedAt) {}

  public enum DoorState {
    OPEN,
    CLOSED
  }

  public record PurgeResult(long deleted) {}

  public record MachineMonitoringStatus(
      UUID machineId, Reachability reachability, MeasurementOut lastMeasurement) {}

  public enum Reachability {
    ONLINE,
    OFFLINE,
    UNKNOWN
  }
}
