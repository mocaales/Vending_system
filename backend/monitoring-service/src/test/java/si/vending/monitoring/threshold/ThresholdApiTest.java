package si.vending.monitoring.threshold;

import java.math.BigDecimal;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/**
 * Base for the threshold tests (issue #24). Contract: openapi.yaml {@code
 * /v1/thresholds/{machineId}}; defaults from docs/03 section 5. Decimals are JSON strings matching
 * {@code ^-?\d+(\.\d+)?$}; because the pattern allows scale variants ("8.0" or "8.00"), tests
 * compare them numerically (see {@link #assertDecimal}).
 */
abstract class ThresholdApiTest extends MonitoringApiTest {

  protected static String body(
      String maxTempC,
      String resetTempC,
      int consecutiveReadings,
      int doorOpenMaxSeconds,
      int noDataMaxSeconds) {
    return """
    {"maxTempC":"%s","resetTempC":"%s","consecutiveReadings":%d,\
    "doorOpenMaxSeconds":%d,"noDataMaxSeconds":%d}\
    """
        .formatted(maxTempC, resetTempC, consecutiveReadings, doorOpenMaxSeconds, noDataMaxSeconds);
  }

  protected static String validBody() {
    return body("9.5", "6.5", 3, 90, 600);
  }

  protected static String path(String machineId) {
    return "/v1/thresholds/" + machineId;
  }

  protected Http.Response put(String path, String json, String... headers) {
    String[] all = new String[headers.length + 2];
    all[0] = "Content-Type";
    all[1] = "application/json";
    System.arraycopy(headers, 0, all, 2, headers.length);
    return Http.send(port, "PUT", path, json, all);
  }

  protected Http.Response putThresholds(String machineId, String json) {
    return put(path(machineId), json, operator());
  }

  protected Http.Response getThresholds(String machineId) {
    return get(path(machineId), operator());
  }

  protected Http.Response deleteThresholds(String machineId) {
    return delete(path(machineId), operator());
  }

  /** Asserts a decimal-string field: must be a JSON string, equal numerically to the expected. */
  protected static void assertDecimal(Object actual, String expected) {
    org.assertj.core.api.Assertions.assertThat(actual)
        .as("decimal string, not a JSON number")
        .isInstanceOf(String.class);
    org.assertj.core.api.Assertions.assertThat(new BigDecimal((String) actual))
        .isEqualByComparingTo(new BigDecimal(expected));
  }
}
