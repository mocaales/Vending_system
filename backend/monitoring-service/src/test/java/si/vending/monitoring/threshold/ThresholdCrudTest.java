package si.vending.monitoring.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;

/** #24: GET / PUT / DELETE /v1/thresholds/{machineId}. */
class ThresholdCrudTest extends ThresholdApiTest {

  private static void assertDefaults(Http.Response response) {
    assertThat(response.status()).isEqualTo(200);
    Map<String, Object> json = response.json();
    assertThat(json)
        .containsOnlyKeys(
            "machineId",
            "maxTempC",
            "resetTempC",
            "consecutiveReadings",
            "doorOpenMaxSeconds",
            "noDataMaxSeconds",
            "isDefault");
    assertDecimal(json.get("maxTempC"), "8.0");
    assertDecimal(json.get("resetTempC"), "7.0");
    assertThat(json)
        .containsEntry("consecutiveReadings", 2)
        .containsEntry("doorOpenMaxSeconds", 120)
        .containsEntry("noDataMaxSeconds", 300)
        .containsEntry("isDefault", true);
  }

  private static void assertStored(Http.Response response, String machine) {
    assertThat(response.status()).isEqualTo(200);
    Map<String, Object> json = response.json();
    assertThat(json).containsEntry("machineId", machine);
    assertDecimal(json.get("maxTempC"), "9.5");
    assertDecimal(json.get("resetTempC"), "6.5");
    assertThat(json)
        .containsEntry("consecutiveReadings", 3)
        .containsEntry("doorOpenMaxSeconds", 90)
        .containsEntry("noDataMaxSeconds", 600)
        .containsEntry("isDefault", false);
  }

  @Test
  void getWithoutStoredThresholdsReturnsTheDefaults() {
    String machine = newMachine();

    Http.Response response = getThresholds(machine);

    assertDefaults(response);
    assertThat(response.json()).containsEntry("machineId", machine);
  }

  @Test
  void putStoresAndReturnsTheValuesWithIsDefaultFalse() {
    String machine = newMachine();

    assertStored(putThresholds(machine, validBody()), machine);
    assertStored(getThresholds(machine), machine);
  }

  @Test
  void putIsIdempotentRepeatingTheSameBodyIs200WithTheSameValues() {
    String machine = newMachine();

    Http.Response first = putThresholds(machine, validBody());
    Http.Response second = putThresholds(machine, validBody());

    assertStored(first, machine);
    assertStored(second, machine);
    assertThat(second.json()).isEqualTo(first.json());
  }

  @Test
  void putReplacesTheWholeStoredRecord() {
    String machine = newMachine();
    putThresholds(machine, validBody());

    Http.Response response = putThresholds(machine, body("12.0", "10.5", 5, 30, 45));

    assertThat(response.status()).isEqualTo(200);
    assertDecimal(response.json().get("maxTempC"), "12.0");
    assertDecimal(response.json().get("resetTempC"), "10.5");
    assertThat(response.json())
        .containsEntry("consecutiveReadings", 5)
        .containsEntry("doorOpenMaxSeconds", 30)
        .containsEntry("noDataMaxSeconds", 45)
        .containsEntry("isDefault", false);
    assertThat(getThresholds(machine).json()).containsEntry("noDataMaxSeconds", 45);
  }

  @Test
  void putWithTheDefaultValuesIsStillNotDefault() {
    String machine = newMachine();

    Http.Response response = putThresholds(machine, body("8.0", "7.0", 2, 120, 300));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsEntry("isDefault", false);
    assertThat(getThresholds(machine).json()).containsEntry("isDefault", false);
  }

  @Test
  void thresholdsAreStoredPerMachine() {
    String a = newMachine();
    String b = newMachine();

    putThresholds(a, validBody());

    assertStored(getThresholds(a), a);
    assertDefaults(getThresholds(b));
  }

  @Test
  void putAcceptsNegativeAndIntegerFormDecimals() {
    String machine = newMachine();

    Http.Response response = putThresholds(machine, body("-2", "-5.5", 1, 1, 1));

    assertThat(response.status()).isEqualTo(200);
    assertDecimal(response.json().get("maxTempC"), "-2");
    assertDecimal(response.json().get("resetTempC"), "-5.5");
  }

  @Test
  void putAcceptsTheUpperBoundsOfEveryField() {
    String machine = newMachine();

    Http.Response response = putThresholds(machine, body("8.0", "7.0", 100, 86400, 86400));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json())
        .containsEntry("consecutiveReadings", 100)
        .containsEntry("doorOpenMaxSeconds", 86400)
        .containsEntry("noDataMaxSeconds", 86400);
  }

  @Test
  void deleteResetsToDefaultsAndRepeatingIs204() {
    String machine = newMachine();
    putThresholds(machine, validBody());

    Http.Response first = deleteThresholds(machine);
    Http.Response second = deleteThresholds(machine);

    assertThat(first.status()).isEqualTo(204);
    assertThat(first.body()).isEmpty();
    assertThat(second.status()).isEqualTo(204);
    assertDefaults(getThresholds(machine));
  }

  @Test
  void deleteWithNothingStoredIs204() {
    String machine = newMachine();

    assertThat(deleteThresholds(machine).status()).isEqualTo(204);
    assertDefaults(getThresholds(machine));
  }

  @Test
  void putAfterDeleteStoresAgain() {
    String machine = newMachine();
    putThresholds(machine, validBody());
    deleteThresholds(machine);

    assertStored(putThresholds(machine, validBody()), machine);
  }

  @Test
  void deleteOnlyResetsTheAddressedMachine() {
    String a = newMachine();
    String b = newMachine();
    putThresholds(a, validBody());
    putThresholds(b, validBody());

    deleteThresholds(a);

    assertDefaults(getThresholds(a));
    assertStored(getThresholds(b), b);
  }

  @Test
  void malformedMachineIdIs400OnAllThreeCalls() {
    assertError(getThresholds("not-a-uuid"), 400, "BAD_REQUEST");
    assertError(putThresholds("not-a-uuid", validBody()), 400, "BAD_REQUEST");
    assertError(deleteThresholds("not-a-uuid"), 400, "BAD_REQUEST");
  }

  @Test
  void unreadableJsonIs400AndStoresNothing() {
    String machine = newMachine();

    assertError(putThresholds(machine, "{not json"), 400, "BAD_REQUEST");
    assertDefaults(getThresholds(machine));
  }
}
