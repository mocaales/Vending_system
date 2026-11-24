package si.vending.monitoring.threshold;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import si.vending.monitoring.core.ApiException;
import si.vending.monitoring.measurement.MachineClient;
import si.vending.monitoring.measurement.ThresholdService.Thresholds;
import si.vending.monitoring.threshold.ThresholdDtos.ThresholdsOut;
import tools.jackson.databind.JsonNode;

/** Reads, replaces and resets the thresholds of one machine. */
@Service
public class ThresholdAdminService {

  private static final Pattern DECIMAL = Pattern.compile("^-?\\d{1,3}(\\.\\d{1,6})?$");
  private static final BigDecimal TEMP_LIMIT = new BigDecimal(1000);
  private static final Set<String> FIELDS =
      Set.of(
          "maxTempC",
          "resetTempC",
          "consecutiveReadings",
          "doorOpenMaxSeconds",
          "noDataMaxSeconds");
  private static final int MAX_SECONDS = 86400;
  private static final int MAX_READINGS = 100;

  private final ThresholdRepository repository;
  private final MachineClient machines;

  public ThresholdAdminService(ThresholdRepository repository, MachineClient machines) {
    this.repository = repository;
    this.machines = machines;
  }

  public ThresholdsOut get(UUID machineId) {
    requireMachine(machineId);
    return repository
        .find(machineId)
        .map(t -> out(machineId, t, false))
        .orElseGet(() ->
            out(machineId, si.vending.monitoring.measurement.ThresholdService.DEFAULTS, true));
  }

  /** The machine is checked first, so an unknown machine is 404 whatever the body looks like. */
  public ThresholdsOut replace(UUID machineId, JsonNode body) {
    requireMachine(machineId);
    Thresholds parsed = parse(body);
    repository.upsert(machineId, parsed);
    return out(machineId, repository.find(machineId).orElse(parsed), false);
  }

  public void reset(UUID machineId) {
    requireMachine(machineId);
    repository.delete(machineId);
  }

  private void requireMachine(UUID machineId) {
    if (!machines.exists(machineId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "MACHINE_NOT_FOUND", "Machine not found.");
    }
  }

  private static Thresholds parse(JsonNode body) {
    if (body == null || !body.isObject()) {
      throw ApiException.invalidField("body", "must be a JSON object.");
    }
    for (Iterator<String> names = body.propertyNames().iterator(); names.hasNext(); ) {
      String name = names.next();
      if (!FIELDS.contains(name)) {
        throw ApiException.invalidField(name, "Unknown field.");
      }
    }
    String max = decimal(body, "maxTempC");
    String reset = decimal(body, "resetTempC");
    int readings = integer(body, "consecutiveReadings", MAX_READINGS);
    int door = integer(body, "doorOpenMaxSeconds", MAX_SECONDS);
    int noData = integer(body, "noDataMaxSeconds", MAX_SECONDS);
    if (new BigDecimal(reset).compareTo(new BigDecimal(max)) >= 0) {
      throw ApiException.invalidField("resetTempC", "must be lower than maxTempC.");
    }
    return new Thresholds(max, reset, readings, door, noData);
  }

  private static String decimal(JsonNode body, String field) {
    JsonNode node = body.get(field);
    if (node == null || node.isNull()) {
      throw ApiException.invalidField(field, "is required.");
    }
    if (!node.isString() || !DECIMAL.matcher(node.asString()).matches()) {
      throw ApiException.invalidField(field, "must be a decimal string.");
    }
    BigDecimal value = new BigDecimal(node.asString());
    if (value.abs().compareTo(TEMP_LIMIT) > 0) {
      throw ApiException.invalidField(field, "must be between -1000 and 1000.");
    }
    return node.asString();
  }

  private static int integer(JsonNode body, String field, int max) {
    JsonNode node = body.get(field);
    if (node == null || node.isNull()) {
      throw ApiException.invalidField(field, "is required.");
    }
    if (!node.isIntegralNumber() || !node.canConvertToInt()) {
      throw ApiException.invalidField(field, "must be an integer.");
    }
    int value = node.intValue();
    if (value < 1 || value > max) {
      throw ApiException.invalidField(field, "must be between 1 and " + max + ".");
    }
    return value;
  }

  private static ThresholdsOut out(UUID machineId, Thresholds t, boolean isDefault) {
    return new ThresholdsOut(
        machineId,
        t.maxTempC(),
        t.resetTempC(),
        t.consecutiveReadings(),
        t.doorOpenMaxSeconds(),
        t.noDataMaxSeconds(),
        isDefault);
  }
}
