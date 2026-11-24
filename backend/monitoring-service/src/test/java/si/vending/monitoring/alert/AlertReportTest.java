package si.vending.monitoring.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.alert.support.AlertApiTest;
import si.vending.monitoring.core.support.Http;

/**
 * #25: POST /v1/alerts outcomes (docs/03 section 5, openapi {@code reportLowStock}). Idempotent by
 * {@code version} per machine; at most one ACTIVE LOW_STOCK alert per machine.
 */
class AlertReportTest extends AlertApiTest {

  private final String machine = newMachine();
  private final String milk = newProduct();
  private final String eggs = newProduct();

  @Test
  void firstReportOpensOneActiveAlertWith201() {
    Instant before = Instant.now().minusSeconds(2);

    Http.Response response = report(machine, 1, slot("A1", milk, 1, 3), slot("B2", eggs, 0, 5));

    assertThat(response.status()).isEqualTo(201);
    assertThat(response.json()).containsOnlyKeys("outcome", "alert");
    assertThat(response.json()).containsEntry("outcome", "OPENED");
    Map<String, Object> alert = alertOf(response);
    assertThat(alert.keySet()).containsAll(ALERT_FIELDS);
    assertThat(alert)
        .containsEntry("machineId", machine)
        .containsEntry("type", "LOW_STOCK")
        .containsEntry("status", "ACTIVE")
        .containsEntry("acknowledgedAt", null)
        .containsEntry("acknowledgedBy", null)
        .containsEntry("resolvedAt", null)
        .containsEntry("resolvedBy", null)
        .containsEntry("resolution", null)
        .containsEntry("resolutionNote", null);
    assertThat(alert.get("id")).asString().isNotBlank();
    assertThat(instant(alert.get("openedAt")))
        .isBetween(before.truncatedTo(ChronoUnit.MILLIS), Instant.now().plusSeconds(2));
    assertThat(alert.get("slots"))
        .isEqualTo(
            List.of(
                Map.of("slotCode", "A1", "productId", milk, "quantity", 1, "lowStockThreshold", 3),
                Map.of(
                    "slotCode", "B2", "productId", eggs, "quantity", 0, "lowStockThreshold", 5)));
  }

  @Test
  void newAlertHasPendingNotificationWithZeroAttempts() {
    Map<String, Object> alert = openLowStock(machine, 1, slot("A1", milk, 1, 3));

    assertThat(alert.get("notification")).isEqualTo(Map.of("status", "PENDING", "attempts", 0));
  }

  @Test
  void theOpenedAlertIsListedForTheMachine() {
    Map<String, Object> alert = openLowStock(machine, 1, slot("A1", milk, 1, 3));

    List<Map<String, Object>> alerts = alertsOf(machine);

    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0))
        .containsEntry("id", alert.get("id"))
        .containsEntry("status", "ACTIVE");
    assertThat(alerts.get(0).get("slots")).isEqualTo(alert.get("slots"));
  }

  @Test
  void newerVersionWithSlotsUpdatesTheActiveAlertInPlace() {
    Map<String, Object> opened = openLowStock(machine, 1, slot("A1", milk, 1, 3));
    String id = (String) opened.get("id");

    Http.Response update = report(machine, 2, slot("A1", milk, 0, 3), slot("C3", eggs, 2, 4));

    assertThat(update.status()).isEqualTo(200);
    assertThat(update.json()).containsEntry("outcome", "UPDATED");
    Map<String, Object> alert = alertOf(update);
    assertThat(alert).containsEntry("id", id).containsEntry("status", "ACTIVE");
    assertThat(alert.get("openedAt")).isEqualTo(opened.get("openedAt"));
    assertThat(alert.get("slots"))
        .isEqualTo(
            List.of(
                Map.of("slotCode", "A1", "productId", milk, "quantity", 0, "lowStockThreshold", 3),
                Map.of(
                    "slotCode", "C3", "productId", eggs, "quantity", 2, "lowStockThreshold", 4)));
    List<Map<String, Object>> alerts = alertsOf(machine);
    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0).get("slots")).isEqualTo(alert.get("slots"));
  }

  @Test
  void updateKeepsAnExistingAcknowledgement() {
    Map<String, Object> opened = openLowStock(machine, 1, slot("A1", milk, 1, 3));
    Map<String, Object> acknowledged = acknowledge(opened.get("id")).json();

    report(machine, 2, slot("A1", milk, 0, 3));

    Map<String, Object> now = find(machine, opened.get("id"));
    assertThat(now.get("acknowledgedAt")).isEqualTo(acknowledged.get("acknowledgedAt"));
    assertThat(now).containsEntry("acknowledgedBy", OPERATOR_ID).containsEntry("status", "ACTIVE");
  }

  @Test
  void equalVersionIsIgnoredWithoutChange() {
    openLowStock(machine, 4, slot("A1", milk, 1, 3));

    Http.Response replay = report(machine, 4, slot("A1", milk, 0, 3), slot("Z9", eggs, 0, 9));

    assertThat(replay.status()).isEqualTo(200);
    assertThat(replay.json()).containsEntry("outcome", "IGNORED");
    List<Map<String, Object>> alerts = alertsOf(machine);
    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0).get("slots"))
        .isEqualTo(
            List.of(
                Map.of(
                    "slotCode", "A1", "productId", milk, "quantity", 1, "lowStockThreshold", 3)));
  }

  @Test
  void olderVersionIsIgnoredWithoutChange() {
    openLowStock(machine, 5, slot("A1", milk, 1, 3));

    Http.Response stale = report(machine, 3, slot("Z9", eggs, 0, 9));

    assertThat(stale.status()).isEqualTo(200);
    assertThat(stale.json()).containsEntry("outcome", "IGNORED");
    assertThat(alertsOf(machine)).hasSize(1);
    assertThat(alertsOf(machine).get(0).get("slots"))
        .isEqualTo(
            List.of(
                Map.of(
                    "slotCode", "A1", "productId", milk, "quantity", 1, "lowStockThreshold", 3)));
  }

  @Test
  void staleEmptyReportDoesNotResolve() {
    openLowStock(machine, 5, slot("A1", milk, 1, 3));

    Http.Response stale = report(machine, 5);

    assertThat(stale.status()).isEqualTo(200);
    assertThat(stale.json()).containsEntry("outcome", "IGNORED");
    assertThat(alertsOf(machine).get(0)).containsEntry("status", "ACTIVE");
  }

  @Test
  void emptySlotsResolveTheActiveAlertAutomatically() {
    Map<String, Object> opened = openLowStock(machine, 1, slot("A1", milk, 1, 3));
    Instant before = Instant.now().minusSeconds(2);

    Http.Response response = report(machine, 2);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsEntry("outcome", "RESOLVED");
    Map<String, Object> alert = alertOf(response);
    assertThat(alert)
        .containsEntry("id", opened.get("id"))
        .containsEntry("status", "RESOLVED")
        .containsEntry("resolution", "AUTO")
        .containsEntry("resolvedBy", null);
    assertThat(instant(alert.get("resolvedAt")))
        .isBetween(before.truncatedTo(ChronoUnit.MILLIS), Instant.now().plusSeconds(2));
    assertThat(find(machine, opened.get("id"))).containsEntry("status", "RESOLVED");
  }

  @Test
  void emptySlotsWithNothingActiveIsNoChange() {
    Http.Response response = report(machine, 1);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsEntry("outcome", "NO_CHANGE").containsEntry("alert", null);
    assertThat(alertsOf(machine)).isEmpty();
  }

  @Test
  void newerEmptyReportAfterResolutionIsNoChangeAndKeepsTheAlertResolved() {
    openLowStock(machine, 1, slot("A1", milk, 1, 3));
    report(machine, 2);

    Http.Response again = report(machine, 3);

    assertThat(again.status()).isEqualTo(200);
    assertThat(again.json()).containsEntry("outcome", "NO_CHANGE");
    assertThat(alertsOf(machine)).hasSize(1);
    assertThat(alertsOf(machine).get(0)).containsEntry("status", "RESOLVED");
  }

  @Test
  void reportAfterResolutionOpensANewAlertAndKeepsTheOldOneResolved() {
    Map<String, Object> first = openLowStock(machine, 1, slot("A1", milk, 1, 3));
    report(machine, 2);

    Http.Response reopened = report(machine, 3, slot("B2", eggs, 0, 5));

    assertThat(reopened.status()).isEqualTo(201);
    assertThat(reopened.json()).containsEntry("outcome", "OPENED");
    assertThat(alertOf(reopened).get("id")).isNotEqualTo(first.get("id"));
    List<Map<String, Object>> alerts = alertsOf(machine);
    assertThat(alerts).hasSize(2);
    assertThat(alerts.stream().filter(a -> "ACTIVE".equals(a.get("status")))).hasSize(1);
    assertThat(find(machine, first.get("id"))).containsEntry("status", "RESOLVED");
  }

  @Test
  void staleReportAfterResolutionDoesNotReopen() {
    openLowStock(machine, 1, slot("A1", milk, 1, 3));
    report(machine, 3);

    Http.Response stale = report(machine, 2, slot("A1", milk, 0, 3));

    assertThat(stale.status()).isEqualTo(200);
    assertThat(stale.json()).containsEntry("outcome", "IGNORED");
    assertThat(alertsOf(machine)).hasSize(1);
    assertThat(alertsOf(machine).get(0)).containsEntry("status", "RESOLVED");
  }

  @Test
  void emptyReportWithNothingActiveStillRecordsTheVersion() {
    assertThat(report(machine, 7).json()).containsEntry("outcome", "NO_CHANGE");

    Http.Response stale = report(machine, 6, slot("A1", milk, 0, 3));

    assertThat(stale.status()).isEqualTo(200);
    assertThat(stale.json()).containsEntry("outcome", "IGNORED");
    assertThat(alertsOf(machine)).isEmpty();
  }

  @Test
  void versionsAreTrackedPerMachine() {
    String other = newMachine();
    openLowStock(machine, 50, slot("A1", milk, 1, 3));

    Http.Response response = report(other, 1, slot("A1", eggs, 1, 3));

    assertThat(response.status()).isEqualTo(201);
    assertThat(response.json()).containsEntry("outcome", "OPENED");
  }

  @Test
  void emptyReportOfAnotherMachineDoesNotResolve() {
    String other = newMachine();
    openLowStock(machine, 1, slot("A1", milk, 1, 3));

    assertThat(report(other, 1).json()).containsEntry("outcome", "NO_CHANGE");

    assertThat(alertsOf(machine).get(0)).containsEntry("status", "ACTIVE");
  }

  @Test
  void aLowStockReportDoesNotTouchAlertsOfOtherTypes() {
    String offline = seedActive(machine, "OFFLINE");

    Http.Response response = report(machine, 1, slot("A1", milk, 1, 3));
    Http.Response empty = report(machine, 2);

    assertThat(response.status()).isEqualTo(201);
    assertThat(empty.json()).containsEntry("outcome", "RESOLVED");
    assertThat(find(machine, offline)).containsEntry("status", "ACTIVE");
  }

  @Test
  void newAlertCreatedAfterAnAckedOneIsResolvedStartsUnacknowledged() {
    Map<String, Object> first = openLowStock(machine, 1, slot("A1", milk, 1, 3));
    acknowledge(first.get("id"));
    report(machine, 2);

    Map<String, Object> second = alertOf(report(machine, 3, slot("A1", milk, 0, 3)));

    assertThat(second).containsEntry("acknowledgedAt", null).containsEntry("acknowledgedBy", null);
  }

  @Test
  void aLaterAlertIsListedBeforeTheEarlierOne() {
    Map<String, Object> first = openLowStock(machine, 1, slot("A1", milk, 1, 3));
    report(machine, 2);
    Map<String, Object> second = openLowStock(machine, 3, slot("A1", milk, 1, 3));

    List<Map<String, Object>> alerts = alertsOf(machine);

    assertThat(alerts.stream().map(a -> a.get("id")))
        .containsExactly(second.get("id"), first.get("id"));
    assertThat(instant(second.get("openedAt"))).isAfterOrEqualTo(instant(first.get("openedAt")));
  }
}
