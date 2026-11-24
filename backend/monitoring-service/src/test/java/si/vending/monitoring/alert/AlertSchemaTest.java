package si.vending.monitoring.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import si.vending.monitoring.alert.support.AlertApiTest;

/**
 * #25: the invariant "at most one ACTIVE alert per machine and type" is enforced by the database
 * (partial unique index), not only by application code. Also pins the {@code alert} table that the
 * other tests seed (see {@link AlertApiTest}).
 */
class AlertSchemaTest extends AlertApiTest {

  private final String machine = newMachine();

  @Test
  void aSecondActiveAlertOfTheSameMachineAndTypeIsRejectedByTheDatabase() {
    seedActive(machine, "OFFLINE");

    assertThatThrownBy(() -> seedActive(machine, "OFFLINE"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(alertsOf(machine)).hasSize(1);
  }

  @Test
  void resolvedAlertsOfTheSameMachineAndTypeAreAllowedNextToAnActiveOne() {
    seedAlert(
        machine,
        "DOOR_OPEN",
        "RESOLVED",
        ago(Duration.ofHours(3)),
        null,
        null,
        ago(Duration.ofHours(2)),
        null,
        "AUTO");
    seedAlert(
        machine,
        "DOOR_OPEN",
        "RESOLVED",
        ago(Duration.ofHours(1)),
        null,
        null,
        ago(Duration.ofMinutes(30)),
        OPERATOR_ID,
        "MANUAL");
    seedActive(machine, "DOOR_OPEN");

    assertThat(alertsOf(machine)).hasSize(3);
  }

  @Test
  void differentTypesAndMachinesMayBeActiveTogether() {
    seedActive(machine, "OFFLINE");
    seedActive(machine, "DOOR_OPEN");
    seedActive(machine, "TEMPERATURE_HIGH");
    seedActive(newMachine(), "OFFLINE");

    assertThat(alertsOf(machine)).hasSize(3);
  }

  @Test
  void seededRowsStartWithAPendingNotification() {
    String id = seedActive(machine, "OFFLINE");

    assertThat(find(machine, id).get("notification"))
        .isEqualTo(java.util.Map.of("status", "PENDING", "attempts", 0));
  }
}
