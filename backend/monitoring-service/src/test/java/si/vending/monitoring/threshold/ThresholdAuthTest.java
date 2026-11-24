package si.vending.monitoring.threshold;

import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;

/**
 * #24 access rules (openapi x-callers: OPERATOR only). Same conventions as measurements: no or
 * unknown credential is 401 UNAUTHENTICATED; a known caller that is not allowed, including the
 * gateway token without an operator identity (frontend-api) and a machine identity, is 403
 * CALLER_NOT_ALLOWED.
 */
class ThresholdAuthTest extends ThresholdApiTest {

  private final String machine = newMachine();

  private Http.Response[] allRoutes(String... headers) {
    return new Http.Response[] {
      get(path(machine), headers),
      put(path(machine), validBody(), headers),
      delete(path(machine), headers)
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
    for (Http.Response r : allRoutes("X-Operator-Id", OPERATOR_ID, "X-Machine-Id", machine)) {
      assertError(r, 401, "UNAUTHENTICATED");
    }
  }

  @Test
  void unknownTokenIs401OnEveryRoute() {
    for (Http.Response r : allRoutes("X-Service-Token", "nope", "X-Operator-Id", OPERATOR_ID)) {
      assertError(r, 401, "UNAUTHENTICATED");
    }
  }

  @Test
  void otherServiceTokensAreForbidden() {
    for (Http.Response r :
        allRoutes("X-Service-Token", INVENTORY_TOKEN, "X-Operator-Id", OPERATOR_ID)) {
      assertError(r, 403, "CALLER_NOT_ALLOWED");
    }
    for (Http.Response r : allRoutes("X-Service-Token", MONITORING_TOKEN)) {
      assertError(r, 403, "CALLER_NOT_ALLOWED");
    }
  }

  @Test
  void gatewayTokenWithoutOperatorIdentityIsForbidden() {
    for (Http.Response r : allRoutes("X-Service-Token", GATEWAY_TOKEN)) {
      assertError(r, 403, "CALLER_NOT_ALLOWED");
    }
  }

  @Test
  void aMachineIdentityIsForbidden() {
    for (Http.Response r : allRoutes(device(machine))) {
      assertError(r, 403, "CALLER_NOT_ALLOWED");
    }
  }

  @Test
  void rejectedCallsChangeNothing() {
    put(path(machine), validBody(), device(machine));
    put(path(machine), validBody(), "X-Service-Token", GATEWAY_TOKEN);

    org.assertj.core.api.Assertions.assertThat(getThresholds(machine).json())
        .containsEntry("isDefault", true);
  }

  @Test
  void anOperatorIsAllowed() {
    org.assertj.core.api.Assertions.assertThat(getThresholds(machine).status()).isEqualTo(200);
  }
}
