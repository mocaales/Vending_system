package si.vending.monitoring.threshold;

import java.util.UUID;

/** Response model of the threshold routes; the request body is validated by hand. */
public final class ThresholdDtos {

  private ThresholdDtos() {}

  public record ThresholdsOut(
      UUID machineId,
      String maxTempC,
      String resetTempC,
      int consecutiveReadings,
      int doorOpenMaxSeconds,
      int noDataMaxSeconds,
      boolean isDefault) {}
}
