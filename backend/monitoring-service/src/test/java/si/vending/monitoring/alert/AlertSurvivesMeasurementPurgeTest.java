package si.vending.monitoring.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.alert.support.AlertApiTest;
import si.vending.monitoring.core.support.Http;

/**
 * #25 (leftover of #23): docs/03 section 5 says the measurement purge "alarmov ne briše". Purging
 * measurements leaves alerts of every type and status intact.
 */
class AlertSurvivesMeasurementPurgeTest extends AlertApiTest {

  @Test
  void purgingMeasurementsKeepsEveryAlertOfTheMachine() {
    String machine = newMachine();
    store(machine, ago(Duration.ofDays(12)), "9.5");
    store(machine, ago(Duration.ofDays(10)), "9.6");
    String active = seedActive(machine, "TEMPERATURE_HIGH");
    String resolved =
        seedAlert(
            machine,
            "DOOR_OPEN",
            "RESOLVED",
            ago(Duration.ofDays(11)),
            null,
            null,
            ago(Duration.ofDays(11)).plusSeconds(60),
            null,
            "AUTO");
    Object lowStock = openLowStock(machine, 1, slot("A1", newProduct(), 0, 2)).get("id");
    List<Map<String, Object>> before = alertsOf(machine);

    Http.Response purge =
        delete("/v1/measurements/" + machine + "?before=" + ago(Duration.ofDays(9)), operator());

    assertThat(purge.status()).isEqualTo(200);
    assertThat(purge.json()).containsEntry("deleted", 2);
    List<Map<String, Object>> after = alertsOf(machine);
    assertThat(after).hasSize(3).isEqualTo(before);
    assertThat(after.stream().map(a -> a.get("id")))
        .containsExactlyInAnyOrder(active, resolved, lowStock);
  }
}
