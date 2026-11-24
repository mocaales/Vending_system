package si.vending.monitoring.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/** #23: GET /v1/monitoring/{machineId}/status (docs/03 section 5, reachability rule). */
class MachineMonitoringStatusTest extends MonitoringApiTest {

  private Http.Response status(String machine) {
    return get("/v1/monitoring/" + machine + "/status", operator());
  }

  @Test
  void machineWithoutMeasurementIsUnknownWithNullLastMeasurement() {
    String machine = newMachine();

    Http.Response response = status(machine);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsOnlyKeys("machineId", "reachability", "lastMeasurement");
    assertThat(response.json())
        .containsEntry("machineId", machine)
        .containsEntry("reachability", "UNKNOWN")
        .containsEntry("lastMeasurement", null);
  }

  @Test
  @SuppressWarnings("unchecked")
  void recentMeasurementIsOnlineAndReturnedAsLastMeasurement() {
    String machine = newMachine();
    String id = UUID.randomUUID().toString();
    Instant observed = ago(Duration.ofSeconds(20));
    postMeasurement(machine, id, observed, "6.5");

    Http.Response response = status(machine);

    assertThat(response.json()).containsEntry("reachability", "ONLINE");
    Map<String, Object> last = (Map<String, Object>) response.json().get("lastMeasurement");
    assertThat(last)
        .containsEntry("measurementId", id)
        .containsEntry("temperatureC", "6.5")
        .containsEntry("machineId", machine);
    assertThat(last).containsKeys("observedAt", "doorState", "receivedAt");
  }

  @Test
  @SuppressWarnings("unchecked")
  void measurementOlderThanFiveMinutesIsOfflineButStillReturned() {
    String machine = newMachine();
    String id = UUID.randomUUID().toString();
    postMeasurement(machine, id, ago(Duration.ofMinutes(10)), "7.0");

    Http.Response response = status(machine);

    assertThat(response.json()).containsEntry("reachability", "OFFLINE");
    assertThat(((Map<String, Object>) response.json().get("lastMeasurement")))
        .containsEntry("measurementId", id);
  }

  @Test
  @SuppressWarnings("unchecked")
  void lastMeasurementIsTheLatestObserved() {
    String machine = newMachine();
    String newest = UUID.randomUUID().toString();
    postMeasurement(machine, UUID.randomUUID().toString(), ago(Duration.ofMinutes(30)), "1.0");
    postMeasurement(machine, newest, ago(Duration.ofSeconds(30)), "2.0");
    postMeasurement(machine, UUID.randomUUID().toString(), ago(Duration.ofMinutes(15)), "3.0");

    Http.Response response = status(machine);

    assertThat(response.json()).containsEntry("reachability", "ONLINE");
    assertThat(((Map<String, Object>) response.json().get("lastMeasurement")))
        .containsEntry("measurementId", newest);
  }

  @Test
  void otherMachinesMeasurementsDoNotMakeThisOneOnline() {
    String mine = newMachine();
    String other = newMachine();
    store(other, ago(Duration.ofSeconds(10)), "2.0");

    assertThat(status(mine).json()).containsEntry("reachability", "UNKNOWN");
  }

  @Test
  void unknownMachineIs404() {
    assertError(status(UUID.randomUUID().toString()), 404, "MACHINE_NOT_FOUND");
  }

  @Test
  void machineServiceFailureIs503() {
    String machine = newMachine();
    MACHINES.setMode(
        machine, si.vending.monitoring.measurement.support.MachineStub.Mode.SERVER_ERROR);

    assertError(status(machine), 503, "DEPENDENCY_UNAVAILABLE");
  }
}
