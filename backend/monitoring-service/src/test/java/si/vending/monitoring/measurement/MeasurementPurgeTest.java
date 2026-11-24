package si.vending.monitoring.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MachineStub;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/** #23: DELETE /v1/measurements/{machineId}?before= (docs/03 section 5). */
class MeasurementPurgeTest extends MonitoringApiTest {

  private Http.Response purge(String machine, String query) {
    return delete("/v1/measurements/" + machine + (query.isEmpty() ? "" : "?" + query), operator());
  }

  /** Counts measurements of a machine inside a window of at most 7 days. */
  private int countBetween(String machine, Duration fromAgo, Duration toAgo) {
    Http.Response response =
        listMeasurements(machine, "from=" + ago(fromAgo) + "&to=" + ago(toAgo));
    assertThat(response.status()).isEqualTo(200);
    return (int) response.json().get("total");
  }

  @Test
  void deletesOnlyMeasurementsBeforeTheInstantAndReportsTheCount() {
    String machine = newMachine();
    store(machine, ago(Duration.ofDays(12)), "1.0");
    store(machine, ago(Duration.ofDays(10)), "2.0");
    store(machine, ago(Duration.ofDays(9)).plusSeconds(1), "3.0");
    store(machine, ago(Duration.ofHours(1)), "4.0");

    Http.Response response = purge(machine, "before=" + ago(Duration.ofDays(9)));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsOnlyKeys("deleted").containsEntry("deleted", 2);
    assertThat(countBetween(machine, Duration.ofDays(13), Duration.ofDays(9))).isZero();
    assertThat(countBetween(machine, Duration.ofDays(9).plusSeconds(10), Duration.ofDays(8)))
        .isEqualTo(1);
    assertThat(listMeasurements(machine, "").json()).containsEntry("total", 1);
  }

  @Test
  void repeatingTheCallIs200WithDeletedZero() {
    String machine = newMachine();
    store(machine, ago(Duration.ofDays(10)), "2.0");
    String query = "before=" + ago(Duration.ofDays(9));
    purge(machine, query);

    Http.Response repeat = purge(machine, query);

    assertThat(repeat.status()).isEqualTo(200);
    assertThat(repeat.json()).containsEntry("deleted", 0);
  }

  @Test
  void machineWithNothingToDeleteIs200WithDeletedZero() {
    Http.Response response = purge(newMachine(), "before=" + ago(Duration.ofDays(9)));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsEntry("deleted", 0);
  }

  @Test
  void doesNotTouchOtherMachines() {
    String mine = newMachine();
    String other = newMachine();
    store(mine, ago(Duration.ofDays(10)), "1.0");
    store(other, ago(Duration.ofDays(10)), "2.0");

    purge(mine, "before=" + ago(Duration.ofDays(9)));

    assertThat(countBetween(other, Duration.ofDays(11), Duration.ofDays(9))).isEqualTo(1);
  }

  @Test
  void missingBeforeIs422() {
    assertError(purge(newMachine(), ""), 422, "VALIDATION_ERROR");
  }

  @Test
  void beforeWithinTheLastSevenDaysIs422AndDeletesNothing() {
    String machine = newMachine();
    store(machine, ago(Duration.ofDays(2)), "1.0");

    assertError(purge(machine, "before=" + ago(Duration.ofDays(6))), 422, "VALIDATION_ERROR");
    assertError(purge(machine, "before=" + ago(Duration.ofHours(1))), 422, "VALIDATION_ERROR");
    assertError(purge(machine, "before=" + ahead(Duration.ofDays(1))), 422, "VALIDATION_ERROR");
    assertThat(countBetween(machine, Duration.ofDays(3), Duration.ofDays(1))).isEqualTo(1);
  }

  @Test
  void unparsableBeforeIsAClientError() {
    Http.Response response = purge(newMachine(), "before=last-week");

    assertThat(response.status()).isIn(400, 422);
  }

  @Test
  void unknownMachineIs404() {
    assertError(
        purge(UUID.randomUUID().toString(), "before=" + ago(Duration.ofDays(9))),
        404,
        "MACHINE_NOT_FOUND");
  }

  @Test
  void machineServiceFailureIs503AndDeletesNothing() {
    String machine = newMachine();
    store(machine, ago(Duration.ofDays(10)), "1.0");
    MACHINES.setMode(machine, MachineStub.Mode.SERVER_ERROR);

    assertError(purge(machine, "before=" + ago(Duration.ofDays(9))), 503, "DEPENDENCY_UNAVAILABLE");

    MACHINES.setMode(machine, MachineStub.Mode.NORMAL);
    assertThat(countBetween(machine, Duration.ofDays(11), Duration.ofDays(9))).isEqualTo(1);
  }
}
