package si.vending.monitoring.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MachineStub;

/** #24: the machine must exist in the Machine service (stub), on all three calls. */
class ThresholdMachineLookupTest extends ThresholdApiTest {

  @Test
  void unknownMachineIs404OnGetPutAndDelete() {
    String unknown = UUID.randomUUID().toString();

    assertError(getThresholds(unknown), 404, "MACHINE_NOT_FOUND");
    assertError(putThresholds(unknown, validBody()), 404, "MACHINE_NOT_FOUND");
    assertError(deleteThresholds(unknown), 404, "MACHINE_NOT_FOUND");
  }

  @Test
  void unknownMachineWithInvalidBodyIs404NotValidation() {
    String unknown = UUID.randomUUID().toString();

    assertError(putThresholds(unknown, "{}"), 404, "MACHINE_NOT_FOUND");
  }

  @Test
  void machineServerErrorIs503OnGetPutAndDeleteAndStoresNothing() {
    String machine = newMachine();
    MACHINES.setMode(machine, MachineStub.Mode.SERVER_ERROR);

    assertError(getThresholds(machine), 503, "DEPENDENCY_UNAVAILABLE");
    Http.Response put = putThresholds(machine, validBody());
    assertError(put, 503, "DEPENDENCY_UNAVAILABLE");
    assertError(deleteThresholds(machine), 503, "DEPENDENCY_UNAVAILABLE");
    assertThat(put.header("X-Trace-Id")).isNotBlank();

    MACHINES.setMode(machine, MachineStub.Mode.NORMAL);
    assertThat(getThresholds(machine).json()).containsEntry("isDefault", true);
  }

  @Test
  void theMachineIsLookedUpWithMonitoringsOwnServiceToken() {
    String machine = newMachine();

    getThresholds(machine);

    assertThat(MACHINES.calls())
        .anyMatch(c ->
            c.path().equals("/v1/machines/" + machine)
                && MONITORING_TOKEN.equals(c.serviceToken()));
  }
}
