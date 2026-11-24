package si.vending.monitoring.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * #24 with #23: the stored {@code noDataMaxSeconds} drives the reachability of GET
 * /v1/monitoring/{machineId}/status. The default is 300 s; a last measurement 120 s old is ONLINE
 * by default but OFFLINE once the machine has {@code noDataMaxSeconds = 60}.
 */
class ThresholdStatusIntegrationTest extends ThresholdApiTest {

  private String reachability(String machine) {
    return (String)
        get("/v1/monitoring/" + machine + "/status", operator()).json().get("reachability");
  }

  @Test
  void aSmallerNoDataLimitMakesAnOlderMeasurementOffline() {
    String machine = newMachine();
    store(machine, ago(Duration.ofSeconds(120)), "5.0");
    assertThat(reachability(machine)).as("defaults").isEqualTo("ONLINE");

    assertThat(putThresholds(machine, body("8.0", "7.0", 2, 120, 60)).status()).isEqualTo(200);

    assertThat(reachability(machine)).isEqualTo("OFFLINE");
  }

  @Test
  void deleteRestoresTheDefaultNoDataLimit() {
    String machine = newMachine();
    store(machine, ago(Duration.ofSeconds(120)), "5.0");
    putThresholds(machine, body("8.0", "7.0", 2, 120, 60));
    assertThat(reachability(machine)).isEqualTo("OFFLINE");

    assertThat(deleteThresholds(machine).status()).isEqualTo(204);

    assertThat(reachability(machine)).isEqualTo("ONLINE");
  }

  @Test
  void aLargerNoDataLimitKeepsAnOldMeasurementOnline() {
    String machine = newMachine();
    store(machine, ago(Duration.ofMinutes(10)), "5.0");
    assertThat(reachability(machine)).as("defaults").isEqualTo("OFFLINE");

    putThresholds(machine, body("8.0", "7.0", 2, 120, 3600));

    assertThat(reachability(machine)).isEqualTo("ONLINE");
  }

  @Test
  void anotherMachinesThresholdsDoNotAffectThisOne() {
    String machine = newMachine();
    String other = newMachine();
    store(machine, ago(Duration.ofSeconds(120)), "5.0");

    putThresholds(other, body("8.0", "7.0", 2, 120, 60));

    assertThat(reachability(machine)).isEqualTo("ONLINE");
  }
}
