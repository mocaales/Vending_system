package si.vending.monitoring.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/**
 * Security-review leftover (c): extreme future instants and non-UTC offset forms are a 422, never a
 * 500. Complements {@link MeasurementTimeBoundsTest} (lower bound 2020-01-01T00:00:00Z); the upper
 * bound for list from/to is 2100-01-01T00:00:00Z.
 */
class MeasurementUpperTimeBoundTest extends MonitoringApiTest {

  private static final String[] AFTER_UPPER_BOUND = {
    "2100-01-01T00:00:01Z",
    "2999-12-31T23:59:59Z",
    "+999999999-12-31T23:59:59Z",
    "9999-12-31T23:59:59Z"
  };

  @Test
  void listFromOrToAfterTheUpperBoundIs422() {
    String machine = newMachine();
    for (String value : AFTER_UPPER_BOUND) {
      Http.Response from = listMeasurements(machine, "from=" + value.replace("+", "%2B"));
      Http.Response to = listMeasurements(machine, "to=" + value.replace("+", "%2B"));

      assertThat(from.status()).as("from=" + value).isEqualTo(422);
      assertError(from, 422, "VALIDATION_ERROR");
      assertThat(to.status()).as("to=" + value).isEqualTo(422);
      assertError(to, 422, "VALIDATION_ERROR");
    }
  }

  @Test
  void listWindowEndingAtTheUpperBoundIsAccepted() {
    String machine = newMachine();

    Http.Response response =
        listMeasurements(machine, "from=2099-12-30T00:00:00Z&to=2100-01-01T00:00:00Z");

    assertThat(response.status()).isEqualTo(200);
  }

  @Test
  void observedAtInOffsetFormThatIsBeforeTheLowerBoundInUtcIs422() {
    String machine = newMachine();
    String id = UUID.randomUUID().toString();
    String body =
        """
        {"measurementId":"%s","machineId":"%s","observedAt":"2020-01-01T00:30:00+01:00","temperatureC":"4.2","doorState":"CLOSED"}\
        """
            .formatted(id, machine);

    Http.Response response = post("/v1/measurements", body, device(machine));

    assertThat(response.status())
        .as("2020-01-01T00:30:00+01:00 is 2019-12-31T23:30Z")
        .isEqualTo(422);
    assertError(response, 422, "VALIDATION_ERROR");
    assertThat(postMeasurement(machine, id, ago(Duration.ofMinutes(1)), "4.2").status())
        .as("nothing stored by the rejected request")
        .isEqualTo(201);
  }
}
