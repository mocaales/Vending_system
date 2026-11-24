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
 * #25: PUT /v1/alerts/{id}/resolution (docs/03 section 5, docs/01 section 6.2). Manual resolution
 * only for TEMPERATURE_HIGH, DOOR_OPEN and OFFLINE; it needs no acknowledgement; the body {@code
 * ResolveIn} ({@code note?}, at most 500) is optional; repeating on a resolved alert is 200 without
 * change. LOW_STOCK is resolved only by the Inventory report.
 */
class AlertResolutionTest extends AlertApiTest {

  private final String machine = newMachine();

  @Test
  void resolvesEachResolvableTypeManuallyWithoutAcknowledgement() {
    for (String type : new String[] {"TEMPERATURE_HIGH", "DOOR_OPEN", "OFFLINE"}) {
      String id = seedActive(machine, type);
      Instant before = Instant.now().minusSeconds(2).truncatedTo(ChronoUnit.MILLIS);

      Http.Response response = resolve(id, null);

      assertThat(response.status()).as(type).isEqualTo(200);
      Map<String, Object> alert = response.json();
      assertThat(alert.keySet()).containsAll(ALERT_FIELDS);
      assertThat(alert)
          .containsEntry("id", id)
          .containsEntry("type", type)
          .containsEntry("status", "RESOLVED")
          .containsEntry("resolution", "MANUAL")
          .containsEntry("resolvedBy", OPERATOR_ID)
          .containsEntry("resolutionNote", null)
          .containsEntry("acknowledgedAt", null);
      assertThat(instant(alert.get("resolvedAt"))).isBetween(before, Instant.now().plusSeconds(2));
      assertThat(find(machine, id)).containsEntry("status", "RESOLVED");
    }
  }

  @Test
  void noteIsStored() {
    String id = seedActive(machine, "OFFLINE");

    Http.Response response = resolve(id, "{\"note\":\"door fixed on site\"}");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsEntry("resolutionNote", "door fixed on site");
    assertThat(find(machine, id)).containsEntry("resolutionNote", "door fixed on site");
  }

  @Test
  void emptyObjectAndMissingBodyAreBothAccepted() {
    String first = seedActive(machine, "OFFLINE");
    String second = seedActive(machine, "DOOR_OPEN");

    assertThat(resolve(first, "{}").status()).isEqualTo(200);
    assertThat(resolve(second, null).status()).isEqualTo(200);
  }

  @Test
  void note500CharactersIsAcceptedAnd501IsRejected() {
    String ok = seedActive(machine, "OFFLINE");
    String tooLong = seedActive(machine, "DOOR_OPEN");

    assertThat(resolve(ok, "{\"note\":\"" + "x".repeat(500) + "\"}").status()).isEqualTo(200);
    assertError(
        resolve(tooLong, "{\"note\":\"" + "x".repeat(501) + "\"}"), 422, "VALIDATION_ERROR");
    assertThat(find(machine, tooLong)).containsEntry("status", "ACTIVE");
  }

  @Test
  void unknownFieldsAndWrongTypesAreRejected() {
    String id = seedActive(machine, "OFFLINE");

    assertError(
        resolve(id, "{\"note\":\"a\",\"resolvedBy\":\"" + OTHER_OPERATOR_ID + "\"}"),
        422,
        "VALIDATION_ERROR");
    assertError(resolve(id, "{\"note\":5}"), 422, "VALIDATION_ERROR");
    assertThat(find(machine, id)).containsEntry("status", "ACTIVE");
  }

  @Test
  void malformedJsonIs400() {
    String id = seedActive(machine, "OFFLINE");

    assertError(resolve(id, "{\"note\":"), 400, "BAD_REQUEST");
  }

  @Test
  void resolvedByIsTheCallingOperator() {
    String id = seedActive(machine, "OFFLINE");

    Http.Response response = resolve(id, null, otherOperator());

    assertThat(response.json()).containsEntry("resolvedBy", OTHER_OPERATOR_ID);
  }

  @Test
  void anAcknowledgedAlertCanBeResolvedAndKeepsItsAcknowledgement() {
    String id = seedActive(machine, "OFFLINE");
    Map<String, Object> acked = acknowledge(id).json();

    Http.Response response = resolve(id, null);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json())
        .containsEntry("status", "RESOLVED")
        .containsEntry("acknowledgedBy", OPERATOR_ID)
        .containsEntry("acknowledgedAt", acked.get("acknowledgedAt"));
  }

  @Test
  void repeatingOnAResolvedAlertIs200AndChangesNothing() throws Exception {
    String id = seedActive(machine, "DOOR_OPEN");
    Map<String, Object> first = resolve(id, "{\"note\":\"first\"}").json();
    Thread.sleep(25);

    Http.Response repeat = resolve(id, "{\"note\":\"second\"}", otherOperator());

    assertThat(repeat.status()).isEqualTo(200);
    assertThat(repeat.json())
        .containsEntry("resolvedAt", first.get("resolvedAt"))
        .containsEntry("resolvedBy", OPERATOR_ID)
        .containsEntry("resolutionNote", "first")
        .containsEntry("resolution", "MANUAL");
  }

  @Test
  void aSeededAutoResolvedAlertStaysAutoOnManualResolve() {
    Instant resolvedAt = ago(Duration.ofMinutes(10));
    String id =
        seedAlert(
            machine,
            "OFFLINE",
            "RESOLVED",
            ago(Duration.ofHours(1)),
            null,
            null,
            resolvedAt,
            null,
            "AUTO");

    Http.Response response = resolve(id, "{\"note\":\"late\"}");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json())
        .containsEntry("resolution", "AUTO")
        .containsEntry("resolvedBy", null)
        .containsEntry("resolutionNote", null);
    assertThat(instant(response.json().get("resolvedAt")))
        .isEqualTo(resolvedAt.truncatedTo(ChronoUnit.MILLIS));
  }

  @Test
  void afterManualResolutionTheSameTypeMayBeActiveAgain() {
    String id = seedActive(machine, "OFFLINE");
    resolve(id, null);

    String next = seedActive(machine, "OFFLINE");

    assertThat(find(machine, next)).containsEntry("status", "ACTIVE");
    assertThat(find(machine, id)).containsEntry("status", "RESOLVED");
  }

  @Test
  void lowStockIs409AlertTypeNotResolvableAndStaysActive() {
    String id = openLowStock(machine, 1, slot("A1", newProduct(), 1, 3)).get("id").toString();

    assertError(resolve(id, null), 409, "ALERT_TYPE_NOT_RESOLVABLE");
    assertError(resolve(id, "{\"note\":\"try\"}"), 409, "ALERT_TYPE_NOT_RESOLVABLE");

    assertThat(find(machine, id))
        .containsEntry("status", "ACTIVE")
        .containsEntry("resolvedAt", null)
        .containsEntry("resolution", null);
  }

  @Test
  void lowStockIsStillResolvedByTheInventoryReport() {
    String id = openLowStock(machine, 1, slot("A1", newProduct(), 1, 3)).get("id").toString();
    resolve(id, null);

    Http.Response empty = report(machine, 2);

    assertThat(empty.json()).containsEntry("outcome", "RESOLVED");
    assertThat(find(machine, id)).containsEntry("resolution", "AUTO");
  }

  @Test
  void unknownAlertIs404AlertNotFound() {
    assertError(resolve(UUID.randomUUID(), null), 404, "ALERT_NOT_FOUND");
    assertError(resolve(UUID.randomUUID(), "{\"note\":\"x\"}"), 404, "ALERT_NOT_FOUND");
  }

  @Test
  void malformedIdIs400() {
    assertError(resolve("not-a-uuid", null), 400, "BAD_REQUEST");
  }
}
