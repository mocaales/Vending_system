package si.vending.monitoring.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/**
 * Instants are not accepted before 2020-01-01T00:00:00Z (observedAt, list from/to, purge before);
 * extreme years are a 422, never a 500. Nothing is stored or deleted by a rejected request.
 */
class MeasurementTimeBoundsTest extends MonitoringApiTest {

  private static final String[] BEFORE_BOUND = {
    "2019-12-31T23:59:59Z", "0001-01-01T00:00:00Z", "-290000-01-01T00:00:00Z"
  };

  @SuppressWarnings("unchecked")
  private static void assertInvalidField(Http.Response response, String field) {
    assertError(response, 422, "VALIDATION_ERROR");
    List<Map<String, Object>> details = (List<Map<String, Object>>) response.json().get("details");
    assertThat(details).isNotNull().isNotEmpty();
    assertThat(details).anyMatch(d -> String.valueOf(d.get("field")).startsWith(field));
  }

  private Http.Response postRaw(String machine, String id, String observedAt) {
    String body =
        """
        {"measurementId":"%s","machineId":"%s","observedAt":"%s","temperatureC":"4.2","doorState":"CLOSED"}\
        """
            .formatted(id, machine, observedAt);
    return post("/v1/measurements", body, device(machine));
  }

  // --- observedAt

  @Test
  void observedAtBeforeTheBoundIs422AndNothingIsStored() {
    String machine = newMachine();
    String id = UUID.randomUUID().toString();
    for (String value : BEFORE_BOUND) {
      Http.Response response = postRaw(machine, id, value);

      assertThat(response.status()).as("observedAt=" + value).isEqualTo(422);
      assertInvalidField(response, "observedAt");
    }
    assertThat(postMeasurement(machine, id, ago(Duration.ofMinutes(1)), "4.2").status())
        .as("first acceptance")
        .isEqualTo(201);
  }

  @Test
  void observedAtJustAfterTheBoundIsAccepted() {
    String machine = newMachine();

    assertThat(postRaw(machine, UUID.randomUUID().toString(), "2020-01-02T00:00:00Z").status())
        .isEqualTo(201);
  }

  // --- list from / to

  @Test
  void listFromOrToBeforeTheBoundIs422() {
    String machine = newMachine();
    for (String value : BEFORE_BOUND) {
      assertError(listMeasurements(machine, "from=" + value), 422, "VALIDATION_ERROR");
      assertError(listMeasurements(machine, "to=" + value), 422, "VALIDATION_ERROR");
    }
  }

  @Test
  void listWindowAfterTheBoundIsAccepted() {
    String machine = newMachine();

    Http.Response response =
        listMeasurements(machine, "from=2020-01-02T00:00:00Z&to=2020-01-03T00:00:00Z");

    assertThat(response.status()).isEqualTo(200);
  }

  // --- purge before

  @Test
  void purgeBeforeTheBoundIs422AndDeletesNothing() {
    String machine = newMachine();
    store(machine, ago(Duration.ofDays(10)), "1.0");
    for (String value : BEFORE_BOUND) {
      Http.Response response =
          delete("/v1/measurements/" + machine + "?before=" + value, operator());

      assertThat(response.status()).as("before=" + value).isEqualTo(422);
      assertError(response, 422, "VALIDATION_ERROR");
    }

    Http.Response purge =
        delete("/v1/measurements/" + machine + "?before=" + ago(Duration.ofDays(9)), operator());
    assertThat(purge.json()).as("the measurement was still there").containsEntry("deleted", 1);
  }

  @Test
  void purgeBeforeJustAfterTheBoundIs200() {
    String machine = newMachine();

    Http.Response response =
        delete("/v1/measurements/" + machine + "?before=2020-01-02T00:00:00Z", operator());

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsEntry("deleted", 0);
  }
}
