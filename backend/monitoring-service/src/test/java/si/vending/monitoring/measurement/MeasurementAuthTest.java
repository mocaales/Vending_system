package si.vending.monitoring.measurement;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/**
 * #23 access rules (docs/03 section 5 "Kdo kliče"): measurement ingest is a machine call through
 * the gateway; list, status and purge are operator calls through the gateway. Same conventions as
 * Machine: no/unknown credential is 401 UNAUTHENTICATED, a known caller that is not allowed (or the
 * gateway token without the identity header the route needs) is 403 CALLER_NOT_ALLOWED.
 */
class MeasurementAuthTest extends MonitoringApiTest {

  private final String machine = newMachine();

  private Http.Response[] allRoutes(String... headers) {
    String[] withJson = new String[headers.length + 2];
    withJson[0] = "Content-Type";
    withJson[1] = "application/json";
    System.arraycopy(headers, 0, withJson, 2, headers.length);
    String body =
        json(UUID.randomUUID().toString(), machine, ago(Duration.ofMinutes(1)), "4.2", "CLOSED");
    return new Http.Response[] {
      post("/v1/measurements", body, headers),
      get("/v1/measurements/" + machine, headers),
      get("/v1/monitoring/" + machine + "/status", headers),
      delete("/v1/measurements/" + machine + "?before=" + ago(Duration.ofDays(9)), headers)
    };
  }

  @Test
  void noTokenIs401OnEveryRoute() {
    for (Http.Response r : allRoutes()) {
      assertError(r, 401, "UNAUTHENTICATED");
    }
  }

  @Test
  void identityHeadersAloneNeverAuthenticate() {
    for (Http.Response r : allRoutes("X-Machine-Id", machine, "X-Operator-Id", OPERATOR_ID)) {
      assertError(r, 401, "UNAUTHENTICATED");
    }
  }

  @Test
  void unknownTokenIs401OnEveryRoute() {
    for (Http.Response r :
        allRoutes(
            "X-Service-Token", "nope", "X-Machine-Id", machine, "X-Operator-Id", OPERATOR_ID)) {
      assertError(r, 401, "UNAUTHENTICATED");
    }
  }

  @Test
  void otherServicesAreNotAllowedOnAnyRoute() {
    for (Http.Response r :
        allRoutes(
            "X-Service-Token",
            INVENTORY_TOKEN,
            "X-Machine-Id",
            machine,
            "X-Operator-Id",
            OPERATOR_ID)) {
      assertError(r, 403, "CALLER_NOT_ALLOWED");
    }
  }

  @Test
  void gatewayTokenWithoutMachineIdCannotPostMeasurements() {
    String body =
        json(UUID.randomUUID().toString(), machine, ago(Duration.ofMinutes(1)), "4.2", "CLOSED");

    assertError(
        post("/v1/measurements", body, "X-Service-Token", GATEWAY_TOKEN),
        403,
        "CALLER_NOT_ALLOWED");
    assertError(
        post(
            "/v1/measurements",
            body,
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Operator-Id",
            OPERATOR_ID),
        403,
        "CALLER_NOT_ALLOWED");
  }

  @Test
  void purgeNeedsAnOperatorNotAMachine() {
    Http.Response withoutOperator =
        delete(
            "/v1/measurements/" + machine + "?before=" + ago(Duration.ofDays(9)),
            "X-Service-Token",
            GATEWAY_TOKEN);
    Http.Response asMachine =
        delete(
            "/v1/measurements/" + machine + "?before=" + ago(Duration.ofDays(9)), device(machine));

    assertError(withoutOperator, 403, "CALLER_NOT_ALLOWED");
    assertError(asMachine, 403, "CALLER_NOT_ALLOWED");
  }

  @Test
  void aMachineCannotReadMeasurementsOrStatus() {
    assertError(get("/v1/measurements/" + machine, device(machine)), 403, "CALLER_NOT_ALLOWED");
    assertError(
        get("/v1/monitoring/" + machine + "/status", device(machine)), 403, "CALLER_NOT_ALLOWED");
  }
}
