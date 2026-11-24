package si.vending.monitoring.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import si.vending.monitoring.measurement.support.MachineStub;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/** A failing Machine service is logged without the body it answered with. */
@ExtendWith(OutputCaptureExtension.class)
class DependencyLogTest extends MonitoringApiTest {

  private static final String MARKER = "SECRET-BODY-MARKER";

  @AfterEach
  void resetBody() {
    MACHINES.setErrorBody(null);
  }

  @Test
  void machineFailureLogDoesNotContainTheResponseBody(CapturedOutput output) {
    String machine = newMachine();
    MACHINES.setErrorBody("{\"code\":\"INTERNAL_ERROR\",\"message\":\"" + MARKER + "\"}");
    MACHINES.setMode(machine, MachineStub.Mode.SERVER_ERROR);

    assertError(
        postMeasurement(machine, UUID.randomUUID().toString(), ago(Duration.ofMinutes(1)), "4.2"),
        503,
        "DEPENDENCY_UNAVAILABLE");

    assertThat(output.getAll()).contains("Machine").doesNotContain(MARKER);
  }
}
