package si.vending.monitoring.measurement;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/**
 * Security-review leftover (b): ambiguous credentials are 401 UNAUTHENTICATED, never resolved by
 * picking one value: a comma-joined identity header and a service token sent twice.
 */
class MeasurementAmbiguousHeadersTest extends MonitoringApiTest {

  private static final String OTHER_UUID = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

  private Http.Response[] operatorRoutes(String... headers) {
    String machine = newMachine();
    return new Http.Response[] {
      get("/v1/measurements/" + machine, headers),
      get("/v1/monitoring/" + machine + "/status", headers),
      delete("/v1/measurements/" + machine + "?before=" + ago(Duration.ofDays(9)), headers)
    };
  }

  private Http.Response thresholdRoute(String... headers) {
    return get("/v1/thresholds/" + newMachine(), headers);
  }

  @Test
  void ambiguousCredentialsAreAlso401OnTheThresholdRoutes() {
    assertError(
        thresholdRoute(
            "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", OPERATOR_ID + "," + OTHER_UUID),
        401,
        "UNAUTHENTICATED");
    assertError(
        thresholdRoute(
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Operator-Id",
            OPERATOR_ID),
        401,
        "UNAUTHENTICATED");
  }

  @Test
  void commaJoinedOperatorIdIs401() {
    for (String joined :
        new String[] {OPERATOR_ID + "," + OTHER_UUID, OPERATOR_ID + ", " + OTHER_UUID}) {
      for (Http.Response r :
          operatorRoutes("X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", joined)) {
        assertError(r, 401, "UNAUTHENTICATED");
      }
    }
  }

  @Test
  void commaJoinedMachineIdIs401() {
    String machine = newMachine();
    String body =
        json(
            java.util.UUID.randomUUID().toString(),
            machine,
            ago(Duration.ofMinutes(1)),
            "4.2",
            "CLOSED");

    assertError(
        post(
            "/v1/measurements",
            body,
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Machine-Id",
            machine + "," + OTHER_UUID),
        401,
        "UNAUTHENTICATED");
  }

  @Test
  void serviceTokenSentTwiceIs401EvenIfBothAreValid() {
    for (Http.Response r :
        operatorRoutes(
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Operator-Id",
            OPERATOR_ID)) {
      assertError(r, 401, "UNAUTHENTICATED");
    }
  }

  @Test
  void serviceTokenSentTwiceWithADifferentSecondValueIs401() {
    for (Http.Response r :
        operatorRoutes(
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Service-Token",
            INVENTORY_TOKEN,
            "X-Operator-Id",
            OPERATOR_ID)) {
      assertError(r, 401, "UNAUTHENTICATED");
    }
  }
}
