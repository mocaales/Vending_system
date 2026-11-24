package si.vending.monitoring.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.alert.support.AlertApiTest;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MachineStub;

/**
 * #25: request validation and machine lookup of POST /v1/alerts. The openapi lists 422
 * VALIDATION_ERROR and 422 REFERENCE_NOT_FOUND (unknown machine) for this operation, so the machine
 * IS looked up in the Machine service (like measurements), with Monitoring's own service token; a
 * failing Machine service is 503 DEPENDENCY_UNAVAILABLE.
 */
class AlertReportValidationTest extends AlertApiTest {

  private final String machine = newMachine();
  private final String product = newProduct();

  private Http.Response raw(String json) {
    return post("/v1/alerts", json, inventory());
  }

  private String valid(String slotCode, int quantity, int threshold) {
    return reportJson(machine, 1, slot(slotCode, product, quantity, threshold));
  }

  private void assertRejectedAndNothingStored(String json) {
    Http.Response response = raw(json);
    assertError(response, 422, "VALIDATION_ERROR");
    assertThat(alertsOf(machine)).isEmpty();
  }

  @Test
  void unknownMachineIs422ReferenceNotFound() {
    String unknown = UUID.randomUUID().toString();

    assertError(raw(reportJson(unknown, 1, slot("A1", product, 1, 3))), 422, "REFERENCE_NOT_FOUND");
    assertThat(listAlerts("machineId=" + unknown).json()).containsEntry("total", 0);
  }

  @Test
  void machineServerErrorIs503AndStoresNothing() {
    MACHINES.setMode(machine, MachineStub.Mode.SERVER_ERROR);

    assertError(raw(valid("A1", 1, 3)), 503, "DEPENDENCY_UNAVAILABLE");

    MACHINES.setMode(machine, MachineStub.Mode.NORMAL);
    assertThat(alertsOf(machine)).isEmpty();
    assertThat(report(machine, 1, slot("A1", product, 1, 3)).status()).isEqualTo(201);
  }

  @Test
  void theMachineIsLookedUpWithMonitoringsOwnServiceToken() {
    report(machine, 1, slot("A1", product, 1, 3));

    assertThat(MACHINES.calls())
        .anyMatch(c ->
            c.path().equals("/v1/machines/" + machine)
                && MONITORING_TOKEN.equals(c.serviceToken()));
  }

  @Test
  void typeOtherThanLowStockIs422() {
    for (String type : new String[] {"OFFLINE", "TEMPERATURE_HIGH", "DOOR_OPEN", "low_stock", ""}) {
      assertRejectedAndNothingStored(
          """
          {"machineId":"%s","type":"%s","version":1,"slots":[%s]}\
          """
              .formatted(machine, type, slot("A1", product, 1, 3)));
    }
  }

  @Test
  void missingRequiredFieldsAre422() {
    assertRejectedAndNothingStored("{}");
    assertRejectedAndNothingStored(
        """
        {"type":"LOW_STOCK","version":1,"slots":[]}\
        """);
    assertRejectedAndNothingStored(
        """
        {"machineId":"%s","version":1,"slots":[]}\
        """
            .formatted(machine));
    assertRejectedAndNothingStored(
        """
        {"machineId":"%s","type":"LOW_STOCK","slots":[]}\
        """
            .formatted(machine));
    assertRejectedAndNothingStored(
        """
        {"machineId":"%s","type":"LOW_STOCK","version":1}\
        """
            .formatted(machine));
    assertRejectedAndNothingStored(
        """
        {"machineId":"%s","type":"LOW_STOCK","version":1,"slots":null}\
        """
            .formatted(machine));
  }

  @Test
  void versionBelowOneOrNotAnIntegerIs422() {
    for (String version : new String[] {"0", "-3", "null"}) {
      assertRejectedAndNothingStored(
          """
          {"machineId":"%s","type":"LOW_STOCK","version":%s,"slots":[%s]}\
          """
              .formatted(machine, version, slot("A1", product, 1, 3)));
    }
  }

  @Test
  void slotFieldViolationsAre422() {
    assertRejectedAndNothingStored(valid("", 1, 3));
    assertRejectedAndNothingStored(valid("123456789", 1, 3));
    assertRejectedAndNothingStored(valid("A1", -1, 3));
    assertRejectedAndNothingStored(valid("A1", 1, -1));
    assertRejectedAndNothingStored(reportJson(machine, 1, slot("A1", "not-a-uuid", 1, 3)));
    assertRejectedAndNothingStored(
        reportJson(machine, 1, "{\"slotCode\":\"A1\",\"productId\":\"" + product + "\"}"));
  }

  @Test
  void slotBoundariesAreAccepted() {
    Http.Response response = report(machine, 1, slot("ABCDEFGH", product, 0, 0));

    assertThat(response.status()).isEqualTo(201);
  }

  @Test
  void moreThan100SlotsIs422() {
    String[] slots =
        IntStream.range(0, 101).mapToObj(i -> slot("S" + i, product, 1, 3)).toArray(String[]::new);

    assertRejectedAndNothingStored(reportJson(machine, 1, slots));
  }

  @Test
  void exactly100SlotsIsAccepted() {
    String[] slots =
        IntStream.range(0, 100).mapToObj(i -> slot("S" + i, product, 1, 3)).toArray(String[]::new);

    Http.Response response = report(machine, 1, slots);

    assertThat(response.status()).isEqualTo(201);
    assertThat(alertOf(response).get("slots")).asList().hasSize(100);
  }

  @Test
  void unknownFieldsAreRejected() {
    assertRejectedAndNothingStored(
        """
        {"machineId":"%s","type":"LOW_STOCK","version":1,"slots":[],"extra":true}\
        """
            .formatted(machine));
    assertRejectedAndNothingStored(
        """
        {"machineId":"%s","type":"LOW_STOCK","version":1,"slots":[%s]}\
        """
            .formatted(
                machine,
                "{\"slotCode\":\"A1\",\"productId\":\""
                    + product
                    + "\",\"quantity\":1,\"lowStockThreshold\":3,\"name\":\"milk\"}"));
  }

  @Test
  void invalidMachineIdIsAClientError() {
    Http.Response response =
        raw(
            """
            {"machineId":"not-a-uuid","type":"LOW_STOCK","version":1,"slots":[]}\
            """);

    assertThat(response.status()).isIn(400, 422);
    assertThat(response.json().get("code")).isIn("BAD_REQUEST", "VALIDATION_ERROR");
  }

  @Test
  void malformedJsonIs400() {
    assertError(raw("{\"machineId\":"), 400, "BAD_REQUEST");
  }

  @Test
  void invalidReportDoesNotAdvanceTheVersion() {
    raw(valid("", 1, 3));

    assertThat(report(machine, 1, slot("A1", product, 1, 3)).status()).isEqualTo(201);
  }
}
