package si.vending.monitoring.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;

/**
 * #24: PUT validation. Every rejection is 422 VALIDATION_ERROR with {@code details[].field}, and
 * nothing is stored (the next GET still returns the defaults).
 */
class ThresholdValidationTest extends ThresholdApiTest {

  @SuppressWarnings("unchecked")
  private void assertRejected(String machine, String json, String field) {
    Http.Response response = putThresholds(machine, json);

    assertError(response, 422, "VALIDATION_ERROR");
    if (field != null) {
      List<Map<String, Object>> details =
          (List<Map<String, Object>>) response.json().get("details");
      assertThat(details).as("details for " + json).isNotNull().isNotEmpty();
      assertThat(details).anyMatch(d -> String.valueOf(d.get("field")).startsWith(field));
    }
    assertThat(getThresholds(machine).json())
        .as("nothing stored after rejected " + json)
        .containsEntry("isDefault", true);
  }

  @Test
  void resetTempAboveMaxTempIs422() {
    assertRejected(newMachine(), body("8.0", "9.0", 2, 120, 300), "resetTempC");
  }

  @Test
  void resetTempEqualToMaxTempIs422() {
    assertRejected(newMachine(), body("8.0", "8.0", 2, 120, 300), "resetTempC");
  }

  @Test
  void resetTempEqualInValueButDifferentScaleIs422() {
    assertRejected(newMachine(), body("8.0", "8.00", 2, 120, 300), "resetTempC");
  }

  @Test
  void resetTempJustBelowMaxTempIsAccepted() {
    assertThat(putThresholds(newMachine(), body("8.0", "7.99", 2, 120, 300)).status())
        .isEqualTo(200);
  }

  @Test
  void consecutiveReadingsOutOfRangeIs422() {
    String machine = newMachine();
    assertRejected(machine, body("8.0", "7.0", 0, 120, 300), "consecutiveReadings");
    assertRejected(machine, body("8.0", "7.0", -1, 120, 300), "consecutiveReadings");
    assertRejected(machine, body("8.0", "7.0", 101, 120, 300), "consecutiveReadings");
  }

  @Test
  void doorOpenMaxSecondsOutOfRangeIs422() {
    String machine = newMachine();
    assertRejected(machine, body("8.0", "7.0", 2, 0, 300), "doorOpenMaxSeconds");
    assertRejected(machine, body("8.0", "7.0", 2, -5, 300), "doorOpenMaxSeconds");
    assertRejected(machine, body("8.0", "7.0", 2, 86401, 300), "doorOpenMaxSeconds");
  }

  @Test
  void noDataMaxSecondsOutOfRangeIs422() {
    String machine = newMachine();
    assertRejected(machine, body("8.0", "7.0", 2, 120, 0), "noDataMaxSeconds");
    assertRejected(machine, body("8.0", "7.0", 2, 120, -5), "noDataMaxSeconds");
    assertRejected(machine, body("8.0", "7.0", 2, 120, 86401), "noDataMaxSeconds");
  }

  @Test
  void lowerBoundsOfEveryFieldAreAccepted() {
    assertThat(putThresholds(newMachine(), body("8.0", "7.0", 1, 1, 1)).status()).isEqualTo(200);
  }

  @Test
  void temperaturesThatAreNotDecimalStringsAreRejected() {
    String machine = newMachine();
    for (String bad : new String[] {"abc", "8,0", "", " 8.0", "1e3", "NaN", "8.", ".5", "+8.0"}) {
      assertRejected(machine, body(bad, "7.0", 2, 120, 300), "maxTempC");
      assertRejected(machine, body("8.0", bad, 2, 120, 300), "resetTempC");
    }
  }

  @Test
  void temperaturesSentAsJsonNumbersAreRejected() {
    String machine = newMachine();

    assertRejected(
        machine,
        """
        {"maxTempC":8.0,"resetTempC":7.0,"consecutiveReadings":2,"doorOpenMaxSeconds":120,"noDataMaxSeconds":300}\
        """,
        null);
  }

  @Test
  void integerFieldsThatAreNotIntegersAreRejected() {
    String machine = newMachine();
    String[] bad = {"2.5", "\"2\"", "null", "true", "[]"};
    for (String value : bad) {
      assertRejected(
          machine,
          """
          {"maxTempC":"8.0","resetTempC":"7.0","consecutiveReadings":%s,"doorOpenMaxSeconds":120,"noDataMaxSeconds":300}\
          """
              .formatted(value),
          null);
      assertRejected(
          machine,
          """
          {"maxTempC":"8.0","resetTempC":"7.0","consecutiveReadings":2,"doorOpenMaxSeconds":%s,"noDataMaxSeconds":300}\
          """
              .formatted(value),
          null);
      assertRejected(
          machine,
          """
          {"maxTempC":"8.0","resetTempC":"7.0","consecutiveReadings":2,"doorOpenMaxSeconds":120,"noDataMaxSeconds":%s}\
          """
              .formatted(value),
          null);
    }
  }

  @Test
  void unknownFieldsAreRejected() {
    assertRejected(
        newMachine(),
        """
        {"maxTempC":"8.0","resetTempC":"7.0","consecutiveReadings":2,"doorOpenMaxSeconds":120,"noDataMaxSeconds":300,"isDefault":false}\
        """,
        "isDefault");
    assertRejected(
        newMachine(),
        """
        {"maxTempC":"8.0","resetTempC":"7.0","consecutiveReadings":2,"doorOpenMaxSeconds":120,"noDataMaxSeconds":300,"machineId":"%s"}\
        """
            .formatted(newMachine()),
        "machineId");
  }

  @Test
  void everyMissingFieldIsRejected() {
    String machine = newMachine();
    Map<String, String> fields =
        Map.of(
            "maxTempC", "\"8.0\"",
            "resetTempC", "\"7.0\"",
            "consecutiveReadings", "2",
            "doorOpenMaxSeconds", "120",
            "noDataMaxSeconds", "300");
    for (String missing : fields.keySet()) {
      StringBuilder json = new StringBuilder("{");
      fields.forEach((name, value) -> {
        if (!name.equals(missing)) {
          json.append('"').append(name).append("\":").append(value).append(',');
        }
      });
      json.setLength(json.length() - 1);
      json.append('}');
      assertRejected(machine, json.toString(), missing);
    }
  }

  @Test
  void emptyObjectAndNullFieldsAreRejected() {
    String machine = newMachine();
    assertRejected(machine, "{}", null);
    assertRejected(
        machine,
        """
        {"maxTempC":null,"resetTempC":"7.0","consecutiveReadings":2,"doorOpenMaxSeconds":120,"noDataMaxSeconds":300}\
        """,
        "maxTempC");
  }

  @Test
  void aRejectedPutKeepsThePreviouslyStoredValues() {
    String machine = newMachine();
    putThresholds(machine, validBody());

    assertError(putThresholds(machine, body("8.0", "9.0", 2, 120, 300)), 422, "VALIDATION_ERROR");

    assertThat(getThresholds(machine).json())
        .containsEntry("noDataMaxSeconds", 600)
        .containsEntry("isDefault", false);
  }
}
