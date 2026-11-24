package si.vending.monitoring.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.alert.support.AlertApiTest;
import si.vending.monitoring.core.support.Http;

/**
 * #25: GET /v1/alerts (docs/03 section 5; openapi {@code listAlerts}, newest first by openedAt).
 */
class AlertListTest extends AlertApiTest {

  private final String machine = newMachine();

  /**
   * Four alerts of one machine, opened 4h..1h ago: A active+unacknowledged OFFLINE (1h), B
   * active+acknowledged DOOR_OPEN (2h), C resolved+acknowledged TEMPERATURE_HIGH (3h), D
   * resolved+unacknowledged OFFLINE (4h).
   */
  private String a;

  private String b;
  private String c;
  private String d;

  private void seedFour() {
    a =
        seedAlert(
            machine, "OFFLINE", "ACTIVE", ago(Duration.ofHours(1)), null, null, null, null, null);
    b =
        seedAlert(
            machine,
            "DOOR_OPEN",
            "ACTIVE",
            ago(Duration.ofHours(2)),
            ago(Duration.ofMinutes(90)),
            OPERATOR_ID,
            null,
            null,
            null);
    c =
        seedAlert(
            machine,
            "TEMPERATURE_HIGH",
            "RESOLVED",
            ago(Duration.ofHours(3)),
            ago(Duration.ofMinutes(170)),
            OPERATOR_ID,
            ago(Duration.ofMinutes(150)),
            OPERATOR_ID,
            "MANUAL");
    d =
        seedAlert(
            machine,
            "OFFLINE",
            "RESOLVED",
            ago(Duration.ofHours(4)),
            null,
            null,
            ago(Duration.ofHours(3)),
            null,
            "AUTO");
  }

  private List<Object> ids(Http.Response response) {
    return response.items().stream().<Object>map(o -> ((Map<?, ?>) o).get("id")).toList();
  }

  @Test
  void unfilteredListForAMachineIsNewestFirstWithPageEnvelope() {
    seedFour();

    Http.Response response = listAlerts("machineId=" + machine);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().keySet())
        .containsExactlyInAnyOrder("items", "total", "limit", "offset");
    assertThat(response.json())
        .containsEntry("total", 4)
        .containsEntry("limit", 50)
        .containsEntry("offset", 0);
    assertThat(ids(response)).containsExactly(a, b, c, d);
  }

  @Test
  void itemsCarryEveryAlertFieldWithSeparateStatusAndAcknowledgement() {
    seedFour();

    Map<String, Object> active = find(machine, a);
    Map<String, Object> acked = find(machine, b);
    Map<String, Object> manual = find(machine, c);
    Map<String, Object> auto = find(machine, d);

    for (Map<String, Object> alert : List.of(active, acked, manual, auto)) {
      assertThat(alert.keySet()).containsAll(ALERT_FIELDS);
      assertThat(alert).containsEntry("machineId", machine);
    }
    assertThat(active)
        .containsEntry("type", "OFFLINE")
        .containsEntry("status", "ACTIVE")
        .containsEntry("acknowledgedAt", null)
        .containsEntry("resolution", null);
    assertThat(acked)
        .containsEntry("status", "ACTIVE")
        .containsEntry("acknowledgedBy", OPERATOR_ID)
        .containsEntry("resolvedAt", null);
    assertThat(instant(acked.get("acknowledgedAt")))
        .isBefore(instant(acked.get("openedAt")).plus(Duration.ofHours(1)));
    assertThat(manual)
        .containsEntry("status", "RESOLVED")
        .containsEntry("resolution", "MANUAL")
        .containsEntry("resolvedBy", OPERATOR_ID)
        .containsEntry("acknowledgedBy", OPERATOR_ID);
    assertThat(auto)
        .containsEntry("status", "RESOLVED")
        .containsEntry("resolution", "AUTO")
        .containsEntry("resolvedBy", null)
        .containsEntry("acknowledgedAt", null);
  }

  @Test
  void openedAtIsEchoedAsTheSeededInstantToTheMilli() {
    java.time.Instant opened = ago(Duration.ofHours(5));
    String id = seedAlert(machine, "OFFLINE", "ACTIVE", opened, null, null, null, null, null);

    assertThat(instant(find(machine, id).get("openedAt")))
        .isEqualTo(opened.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
  }

  @Test
  void filtersByStatus() {
    seedFour();

    assertThat(ids(listAlerts("machineId=" + machine + "&status=ACTIVE"))).containsExactly(a, b);
    assertThat(ids(listAlerts("machineId=" + machine + "&status=RESOLVED"))).containsExactly(c, d);
  }

  @Test
  void filtersByAcknowledgedIndependentlyOfStatus() {
    seedFour();

    assertThat(ids(listAlerts("machineId=" + machine + "&acknowledged=true")))
        .containsExactly(b, c);
    assertThat(ids(listAlerts("machineId=" + machine + "&acknowledged=false")))
        .containsExactly(a, d);
  }

  @Test
  void filtersByType() {
    seedFour();

    assertThat(ids(listAlerts("machineId=" + machine + "&type=OFFLINE"))).containsExactly(a, d);
    assertThat(ids(listAlerts("machineId=" + machine + "&type=DOOR_OPEN"))).containsExactly(b);
    assertThat(ids(listAlerts("machineId=" + machine + "&type=LOW_STOCK"))).isEmpty();
  }

  @Test
  void filtersCombineWithAnd() {
    seedFour();

    assertThat(ids(listAlerts("machineId=" + machine + "&status=ACTIVE&acknowledged=false")))
        .containsExactly(a);
    assertThat(ids(listAlerts("machineId=" + machine + "&status=RESOLVED&acknowledged=true")))
        .containsExactly(c);
    assertThat(ids(listAlerts("machineId=" + machine + "&type=OFFLINE&status=RESOLVED")))
        .containsExactly(d);
    assertThat(ids(listAlerts("machineId=" + machine + "&type=DOOR_OPEN&status=RESOLVED")))
        .isEmpty();
  }

  @Test
  void machineFilterExcludesOtherMachines() {
    seedFour();
    String other = newMachine();
    String foreign = seedActive(other, "OFFLINE");

    assertThat(ids(listAlerts("machineId=" + other))).containsExactly(foreign);
    assertThat(ids(listAlerts("machineId=" + machine))).doesNotContain(foreign);
  }

  @Test
  void withoutMachineFilterAlertsOfSeveralMachinesAreListed() {
    String other = newMachine();
    String mine = seedActive(machine, "OFFLINE");
    String theirs = seedActive(other, "OFFLINE");

    Http.Response response = listAlerts("type=OFFLINE&status=ACTIVE&limit=200");

    assertThat(response.status()).isEqualTo(200);
    assertThat(ids(response)).contains(mine, theirs);
  }

  @Test
  void machineWithoutAlertsAndUnknownMachineGiveAnEmptyPage() {
    assertThat(listAlerts("machineId=" + machine).json())
        .containsEntry("total", 0)
        .containsEntry("items", List.of());
    Http.Response unknown = listAlerts("machineId=" + UUID.randomUUID());
    assertThat(unknown.status()).isEqualTo(200);
    assertThat(unknown.json()).containsEntry("total", 0);
  }

  @Test
  void paginationSlicesTheNewestFirstOrderAndTotalIgnoresThePage() {
    seedFour();

    Http.Response page = listAlerts("machineId=" + machine + "&limit=2&offset=1");

    assertThat(page.json())
        .containsEntry("total", 4)
        .containsEntry("limit", 2)
        .containsEntry("offset", 1);
    assertThat(ids(page)).containsExactly(b, c);
    assertThat(ids(listAlerts("machineId=" + machine + "&limit=2&offset=3"))).containsExactly(d);
    assertThat(ids(listAlerts("machineId=" + machine + "&offset=10"))).isEmpty();
  }

  @Test
  void totalCountsOnlyTheFilteredAlerts() {
    seedFour();

    assertThat(listAlerts("machineId=" + machine + "&status=ACTIVE&limit=1").json())
        .containsEntry("total", 2);
  }

  @Test
  void outOfRangePaginationIs422() {
    assertError(listAlerts("limit=0"), 422, "VALIDATION_ERROR");
    assertError(listAlerts("limit=201"), 422, "VALIDATION_ERROR");
    assertError(listAlerts("offset=-1"), 422, "VALIDATION_ERROR");
  }

  @Test
  void invalidFilterValuesAreClientErrors() {
    for (String query :
        new String[] {
          "status=BOGUS",
          "type=BOGUS",
          "acknowledged=maybe",
          "machineId=not-a-uuid",
          "status=active"
        }) {
      Http.Response response = listAlerts(query);
      assertThat(response.status()).as(query).isIn(400, 422);
      assertThat(response.json().get("code")).isIn("BAD_REQUEST", "VALIDATION_ERROR");
    }
  }

  @Test
  void frontendApiSeesTheSameList() {
    seedFour();

    Http.Response response = get("/v1/alerts?machineId=" + machine, frontendApi());

    assertThat(response.status()).isEqualTo(200);
    assertThat(ids(response)).containsExactly(a, b, c, d);
  }

  @Test
  void aLowStockAlertCarriesSlotsAndOthersDoNot() {
    seedFour();
    String product = newProduct();
    String other = newMachine();
    Map<String, Object> low = openLowStock(other, 1, slot("A1", product, 1, 3));

    Map<String, Object> listed = find(other, low.get("id"));

    assertThat(listed.get("slots")).asList().hasSize(1);
    Object slots = find(machine, a).get("slots");
    assertThat(slots == null || ((List<?>) slots).isEmpty()).isTrue();
  }
}
