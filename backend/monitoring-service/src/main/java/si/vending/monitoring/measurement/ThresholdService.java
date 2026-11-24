package si.vending.monitoring.measurement;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The one place that answers "which thresholds apply to this machine". Today every machine uses the
 * defaults (docs/03 section 6); issue #24 replaces the body with a lookup of stored thresholds.
 */
@Service
public class ThresholdService {

  /** Thresholds of one machine; defaults match docs/03 ("Pragovi (privzeto)"). */
  public record Thresholds(
      String maxTempC,
      String resetTempC,
      int consecutiveReadings,
      int doorOpenMaxSeconds,
      int noDataMaxSeconds) {}

  static final Thresholds DEFAULTS = new Thresholds("8.0", "7.0", 2, 120, 300);

  public Thresholds forMachine(UUID machineId) {
    return DEFAULTS;
  }
}
