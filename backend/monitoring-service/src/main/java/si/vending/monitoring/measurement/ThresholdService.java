package si.vending.monitoring.measurement;

import java.util.UUID;
import org.springframework.stereotype.Service;
import si.vending.monitoring.threshold.ThresholdRepository;

/**
 * The one place that answers "which thresholds apply to this machine": the stored ones, else the
 * defaults of docs/03 section 6.
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

  public static final Thresholds DEFAULTS = new Thresholds("8.0", "7.0", 2, 120, 300);

  private final ThresholdRepository repository;

  public ThresholdService(ThresholdRepository repository) {
    this.repository = repository;
  }

  public Thresholds forMachine(UUID machineId) {
    return repository.find(machineId).orElse(DEFAULTS);
  }
}
