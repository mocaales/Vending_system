package si.vending.monitoring.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/** #23: GET /v1/measurements/{machineId} (docs/03 section 5). */
class MeasurementListTest extends MonitoringApiTest {

  @SuppressWarnings("unchecked")
  private static List<String> temperatures(Http.Response response) {
    return response.items().stream()
        .map(i -> (String) ((Map<String, Object>) i).get("temperatureC"))
        .toList();
  }

  @Test
  void listsOldestFirstInTheStandardEnvelope() {
    String machine = newMachine();
    store(machine, ago(Duration.ofMinutes(10)), "3.0");
    store(machine, ago(Duration.ofMinutes(30)), "1.0");
    store(machine, ago(Duration.ofMinutes(20)), "2.0");

    Http.Response response = listMeasurements(machine, "");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsOnlyKeys("items", "total", "limit", "offset");
    assertThat(response.json())
        .containsEntry("total", 3)
        .containsEntry("limit", 50)
        .containsEntry("offset", 0);
    assertThat(temperatures(response)).containsExactly("1.0", "2.0", "3.0");
    assertThat(response.items().get(0))
        .asString()
        .contains("measurementId", "machineId", "observedAt", "doorState", "receivedAt");
  }

  @Test
  void defaultWindowIsTheLast24Hours() {
    String machine = newMachine();
    store(machine, ago(Duration.ofHours(25)), "9.1");
    store(machine, ago(Duration.ofHours(23)), "9.2");

    assertThat(temperatures(listMeasurements(machine, ""))).containsExactly("9.2");
  }

  @Test
  void onlyFromGivenWindowEndsNow() {
    String machine = newMachine();
    store(machine, ago(Duration.ofHours(5)), "1.1");
    store(machine, ago(Duration.ofHours(1)), "1.2");

    Http.Response response = listMeasurements(machine, "from=" + ago(Duration.ofHours(2)));

    assertThat(temperatures(response)).containsExactly("1.2");
  }

  @Test
  void onlyToGivenWindowStarts24HoursBeforeIt() {
    String machine = newMachine();
    Instant to = ago(Duration.ofDays(3));
    store(machine, to.minus(Duration.ofHours(25)), "2.1");
    store(machine, to.minus(Duration.ofHours(23)), "2.2");
    store(machine, to.plus(Duration.ofHours(1)), "2.3");

    Http.Response response = listMeasurements(machine, "to=" + to);

    assertThat(temperatures(response)).containsExactly("2.2");
  }

  @Test
  void fromIsInclusiveAndToIsExclusive() {
    String machine = newMachine();
    Instant t1 = ago(Duration.ofHours(3));
    Instant t2 = t1.plusSeconds(60);
    Instant t3 = t1.plusSeconds(120);
    store(machine, t1, "1.0");
    store(machine, t2, "2.0");
    store(machine, t3, "3.0");

    Http.Response response = listMeasurements(machine, "from=" + t1 + "&to=" + t3);

    assertThat(temperatures(response)).containsExactly("1.0", "2.0");
  }

  @Test
  void aWindowOfExactlySevenDaysIsAccepted() {
    String machine = newMachine();
    Instant to = ago(Duration.ofHours(1));
    store(machine, to.minus(Duration.ofDays(7)).plusSeconds(10), "4.0");

    Http.Response response =
        listMeasurements(machine, "from=" + to.minus(Duration.ofDays(7)) + "&to=" + to);

    assertThat(response.status()).isEqualTo(200);
    assertThat(temperatures(response)).containsExactly("4.0");
  }

  @Test
  void aWindowLargerThanSevenDaysIs422() {
    String machine = newMachine();
    Instant to = ago(Duration.ofHours(1));

    Http.Response response =
        listMeasurements(
            machine, "from=" + to.minus(Duration.ofDays(7)).minusSeconds(1) + "&to=" + to);

    assertError(response, 422, "VALIDATION_ERROR");
  }

  @Test
  void fromOlderThanSevenDaysWithoutToIs422() {
    String machine = newMachine();

    assertError(
        listMeasurements(machine, "from=" + ago(Duration.ofDays(8))), 422, "VALIDATION_ERROR");
  }

  @Test
  void fromAfterToIs422() {
    String machine = newMachine();

    Http.Response response =
        listMeasurements(
            machine, "from=" + ago(Duration.ofHours(1)) + "&to=" + ago(Duration.ofHours(2)));

    assertError(response, 422, "VALIDATION_ERROR");
  }

  @Test
  void unparsableInstantIsAClientError() {
    String machine = newMachine();

    Http.Response response = listMeasurements(machine, "from=yesterday");

    assertThat(response.status()).isIn(400, 422);
    assertThat(response.json().get("code")).isIn("BAD_REQUEST", "VALIDATION_ERROR");
  }

  @Test
  void limitAndOffsetPageTheWindow() {
    String machine = newMachine();
    for (int i = 0; i < 5; i++) {
      store(machine, ago(Duration.ofMinutes(50 - i)), "1." + i);
    }

    Http.Response response = listMeasurements(machine, "limit=2&offset=1");

    assertThat(response.json())
        .containsEntry("total", 5)
        .containsEntry("limit", 2)
        .containsEntry("offset", 1);
    assertThat(temperatures(response)).containsExactly("1.1", "1.2");
  }

  @Test
  void otherMachinesMeasurementsAreNotIncluded() {
    String mine = newMachine();
    String other = newMachine();
    store(mine, ago(Duration.ofMinutes(5)), "1.0");
    store(other, ago(Duration.ofMinutes(5)), "2.0");

    assertThat(temperatures(listMeasurements(mine, ""))).containsExactly("1.0");
  }

  @Test
  void machineWithoutMeasurementsIsAnEmptyPage() {
    Http.Response response = listMeasurements(newMachine(), "");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.items()).isEmpty();
    assertThat(response.json()).containsEntry("total", 0);
  }

  @Test
  void unknownMachineIs404() {
    assertError(listMeasurements(UUID.randomUUID().toString(), ""), 404, "MACHINE_NOT_FOUND");
  }

  @Test
  void malformedMachineIdIs400() {
    assertError(get("/v1/measurements/not-a-uuid", operator()), 400, "BAD_REQUEST");
  }
}
