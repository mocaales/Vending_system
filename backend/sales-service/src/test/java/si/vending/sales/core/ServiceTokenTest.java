package si.vending.sales.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.AbstractApiTest;
import si.vending.sales.core.support.Http;

/**
 * Assumed contract: a route annotated with {@code @AllowedCallers("gateway")} requires header
 * {@code X-Service-Token}; the token identifies the caller via {@code
 * core.service-tokens.<caller>}. Missing or unknown token: 401 UNAUTHENTICATED. Known caller not in
 * the allowed list: 403 CALLER_NOT_ALLOWED. Routes without the annotation (health, openapi) need no
 * token.
 */
class ServiceTokenTest extends AbstractApiTest {

  private static final String ROUTE = "/test/guarded";

  @Test
  void missingTokenIsUnauthenticated() {
    assertError(get(ROUTE), 401, "UNAUTHENTICATED");
  }

  @Test
  void blankTokenIsUnauthenticated() {
    assertError(get(ROUTE, "X-Service-Token", ""), 401, "UNAUTHENTICATED");
  }

  @Test
  void unknownTokenIsUnauthenticated() {
    assertError(get(ROUTE, "X-Service-Token", "not-a-known-token"), 401, "UNAUTHENTICATED");
  }

  @Test
  void knownCallerThatIsNotAllowedIsForbidden() {
    assertError(get(ROUTE, "X-Service-Token", INVENTORY_TOKEN), 403, "CALLER_NOT_ALLOWED");
  }

  @Test
  void allowedCallerPasses() {
    Http.Response response = get(ROUTE, "X-Service-Token", GATEWAY_TOKEN);

    assertThat(response.status()).isEqualTo(200);
  }

  @Test
  void routesWithoutAnnotationNeedNoToken() {
    assertThat(get("/health/live").status()).isEqualTo(200);
    assertThat(get("/openapi.json").status()).isEqualTo(200);
  }
}
