package si.vending.monitoring.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.alert.support.AlertApiTest;
import si.vending.monitoring.core.support.Http;

/**
 * #25: PUT /v1/alerts/{id}/acknowledgement (docs/03 section 5). No body; acknowledgement is not
 * resolution; the repeat keeps the first acknowledgement; a resolved alert is 409 ALERT_NOT_ACTIVE.
 */
class AlertAcknowledgementTest extends AlertApiTest {

  private final String machine = newMachine();

  @Test
  void acknowledgesAnActiveAlertWithoutResolvingIt() {
    String id = seedActive(machine, "OFFLINE");
    Instant before = Instant.now().minusSeconds(2).truncatedTo(ChronoUnit.MILLIS);

    Http.Response response = acknowledge(id);

    assertThat(response.status()).isEqualTo(200);
    Map<String, Object> alert = response.json();
    assertThat(alert.keySet()).containsAll(ALERT_FIELDS);
    assertThat(alert)
        .containsEntry("id", id)
        .containsEntry("machineId", machine)
        .containsEntry("type", "OFFLINE")
        .containsEntry("status", "ACTIVE")
        .containsEntry("acknowledgedBy", OPERATOR_ID)
        .containsEntry("resolvedAt", null)
        .containsEntry("resolvedBy", null)
        .containsEntry("resolution", null);
    assertThat(instant(alert.get("acknowledgedAt")))
        .isBetween(before, Instant.now().plusSeconds(2));
    assertThat(find(machine, id))
        .containsEntry("status", "ACTIVE")
        .containsEntry("acknowledgedBy", OPERATOR_ID);
  }

  @Test
  void acknowledgedFilterSeesTheAlertAfterwards() {
    String id = seedActive(machine, "DOOR_OPEN");

    acknowledge(id);

    assertThat(
            listAlerts("machineId=" + machine + "&acknowledged=true").items().stream()
                .<Object>map(o -> ((Map<?, ?>) o).get("id"))
                .toList())
        .containsExactly(id);
    assertThat(listAlerts("machineId=" + machine + "&acknowledged=false").items()).isEmpty();
  }

  @Test
  void aLowStockAlertCanBeAcknowledgedAndStaysActive() {
    String id = openLowStock(machine, 1, slot("A1", newProduct(), 1, 3)).get("id").toString();

    Http.Response response = acknowledge(id);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsEntry("status", "ACTIVE");
    assertThat(response.json().get("slots")).asList().hasSize(1);
  }

  @Test
  void repeatingKeepsTheFirstAcknowledgementEvenFromAnotherOperator() throws Exception {
    String id = seedActive(machine, "OFFLINE");
    Map<String, Object> first = acknowledge(id).json();
    Thread.sleep(25);

    Http.Response repeat = acknowledge(id, otherOperator());

    assertThat(repeat.status()).isEqualTo(200);
    assertThat(repeat.json())
        .containsEntry("acknowledgedBy", OPERATOR_ID)
        .containsEntry("acknowledgedAt", first.get("acknowledgedAt"))
        .containsEntry("status", "ACTIVE");
    assertThat(find(machine, id)).containsEntry("acknowledgedBy", OPERATOR_ID);
  }

  @Test
  void acknowledgedByIsTheCallingOperator() {
    String id = seedActive(machine, "OFFLINE");

    Http.Response response = acknowledge(id, otherOperator());

    assertThat(response.json()).containsEntry("acknowledgedBy", OTHER_OPERATOR_ID);
  }

  @Test
  void alreadyAcknowledgedSeededAlertIsReturnedUnchanged() {
    Instant ackedAt = ago(Duration.ofMinutes(30));
    String id =
        seedAlert(
            machine,
            "TEMPERATURE_HIGH",
            "ACTIVE",
            ago(Duration.ofHours(1)),
            ackedAt,
            OTHER_OPERATOR_ID,
            null,
            null,
            null);

    Http.Response response = acknowledge(id);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsEntry("acknowledgedBy", OTHER_OPERATOR_ID);
    assertThat(instant(response.json().get("acknowledgedAt")))
        .isEqualTo(ackedAt.truncatedTo(ChronoUnit.MILLIS));
  }

  @Test
  void resolvedAlertIs409AlertNotActiveAndStaysUnacknowledged() {
    String id =
        seedAlert(
            machine,
            "OFFLINE",
            "RESOLVED",
            ago(Duration.ofHours(2)),
            null,
            null,
            ago(Duration.ofHours(1)),
            null,
            "AUTO");

    assertError(acknowledge(id), 409, "ALERT_NOT_ACTIVE");

    assertThat(find(machine, id))
        .containsEntry("acknowledgedAt", null)
        .containsEntry("acknowledgedBy", null);
  }

  @Test
  void alertResolvedByTheInventoryReportIs409() {
    String id = openLowStock(machine, 1, slot("A1", newProduct(), 1, 3)).get("id").toString();
    report(machine, 2);

    assertError(acknowledge(id), 409, "ALERT_NOT_ACTIVE");
  }

  @Test
  void acknowledgementThenResolutionOfTheReportStillAllowsNothingMore() {
    String id = openLowStock(machine, 1, slot("A1", newProduct(), 1, 3)).get("id").toString();
    acknowledge(id);
    report(machine, 2);

    assertError(acknowledge(id), 409, "ALERT_NOT_ACTIVE");
    assertThat(find(machine, id))
        .containsEntry("status", "RESOLVED")
        .containsEntry("acknowledgedBy", OPERATOR_ID);
  }

  @Test
  void unknownAlertIs404AlertNotFound() {
    assertError(acknowledge(UUID.randomUUID()), 404, "ALERT_NOT_FOUND");
  }

  @Test
  void malformedIdIs400() {
    assertError(acknowledge("not-a-uuid"), 400, "BAD_REQUEST");
  }
}
