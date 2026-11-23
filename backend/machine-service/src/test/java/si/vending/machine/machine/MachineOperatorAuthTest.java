package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineApiTest;
import si.vending.machine.machine.support.MachineFixtures;

/**
 * Hardening of #17: an operator request must carry the gateway service token AND a valid {@code
 * X-Operator-Id}. The operator header alone must never authenticate (any compose-network service
 * could forge it). Error codes follow the core convention (ServiceTokenTest): no/invalid credential
 * is 401 UNAUTHENTICATED; a known caller that is not allowed is 403 CALLER_NOT_ALLOWED.
 */
class MachineOperatorAuthTest extends MachineApiTest {

  private static final String BODY =
      "{\"name\":\"N\",\"address\":\"A 1\",\"latitude\":46.1,\"longitude\":14.5}";
  private static final String CREATE =
      MachineFixtures.createJson("OA" + MachineFixtures.tag(), "A1");

  private static final String OP_ID = MachineFixtures.OPERATOR_ID;

  private final String someId = UUID.randomUUID().toString();

  /** Sends each operator-only route with the given extra headers; returns responses in order. */
  private Http.Response[] operatorOnlyRoutes(String... headers) {
    String[] json = new String[headers.length + 2];
    json[0] = "Content-Type";
    json[1] = "application/json";
    System.arraycopy(headers, 0, json, 2, headers.length);
    return new Http.Response[] {
      post("/v1/machines", CREATE, json),
      put("/v1/machines/" + someId, BODY, headers),
      put("/v1/machines/" + someId + "/status", "{\"status\":\"SERVICE\"}", headers),
      delete("/v1/machines/" + someId, headers)
    };
  }

  // --- operator header alone (forgery by another internal service)

  @Test
  void operatorHeaderWithoutAnyTokenIsUnauthenticatedOnOperatorRoutes() {
    for (Http.Response r : operatorOnlyRoutes("X-Operator-Id", OP_ID)) {
      assertError(r, 401, "UNAUTHENTICATED");
    }
  }

  @Test
  void operatorHeaderWithoutAnyTokenIsUnauthenticatedOnGetRoutes() {
    assertError(get("/v1/machines", "X-Operator-Id", OP_ID), 401, "UNAUTHENTICATED");
    assertError(get("/v1/machines/" + someId, "X-Operator-Id", OP_ID), 401, "UNAUTHENTICATED");
  }

  @Test
  void operatorHeaderWithUnknownTokenIsUnauthenticated() {
    for (Http.Response r : operatorOnlyRoutes("X-Service-Token", "nope", "X-Operator-Id", OP_ID)) {
      assertError(r, 401, "UNAUTHENTICATED");
    }
  }

  @Test
  void operatorHeaderWithNonGatewayTokenIsForbiddenOnOperatorRoutes() {
    for (String token : new String[] {SALES_TOKEN, INVENTORY_TOKEN, MONITORING_TOKEN}) {
      for (Http.Response r : operatorOnlyRoutes("X-Service-Token", token, "X-Operator-Id", OP_ID)) {
        assertError(r, 403, "CALLER_NOT_ALLOWED");
      }
    }
  }

  @Test
  void operatorHeaderDoesNotLiftSalesOnGetRoutes() {
    assertError(
        get("/v1/machines", "X-Service-Token", SALES_TOKEN, "X-Operator-Id", OP_ID),
        403,
        "CALLER_NOT_ALLOWED");
    assertError(
        get("/v1/machines/" + someId, "X-Service-Token", SALES_TOKEN, "X-Operator-Id", OP_ID),
        403,
        "CALLER_NOT_ALLOWED");
  }

  // --- gateway token without operator id

  @Test
  void gatewayTokenWithoutOperatorIdIsForbiddenOnOperatorRoutes() {
    for (Http.Response r : operatorOnlyRoutes("X-Service-Token", GATEWAY_TOKEN)) {
      assertError(r, 403, "CALLER_NOT_ALLOWED");
    }
  }

  @Test
  void gatewayTokenWithBlankOperatorIdIsForbiddenOnOperatorRoutes() {
    for (Http.Response r :
        operatorOnlyRoutes("X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", "")) {
      assertError(r, 403, "CALLER_NOT_ALLOWED");
    }
  }

  // --- malformed operator id (a presented but invalid credential: 401 like an unknown token)

  @Test
  void malformedOperatorIdWithGatewayTokenIsUnauthenticated() {
    for (Http.Response r :
        operatorOnlyRoutes("X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", "not-a-uuid")) {
      assertError(r, 401, "UNAUTHENTICATED");
    }
    assertError(
        get("/v1/machines", "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", "not-a-uuid"),
        401,
        "UNAUTHENTICATED");
  }

  // --- positive: both present, and GET routes without operator id

  @Test
  void gatewayTokenWithOperatorIdIsAllowedOnAllRoutes() {
    String id = newMachine(1);

    assertThat(getMachine(id).status()).isEqualTo(200);
    assertThat(get("/v1/machines?q=" + id, operator()).status()).isEqualTo(200);
    assertThat(put("/v1/machines/" + id, BODY, operator()).status()).isEqualTo(200);
    assertThat(setStatus(id, "SERVICE").status()).isEqualTo(200);
  }

  @Test
  void getRoutesStayOpenToGatewayInventoryMonitoringWithoutOperatorId() {
    String id = newMachine(1);

    for (String token : new String[] {GATEWAY_TOKEN, INVENTORY_TOKEN, MONITORING_TOKEN}) {
      assertThat(get("/v1/machines/" + id, "X-Service-Token", token).status()).isEqualTo(200);
      assertThat(get("/v1/machines", "X-Service-Token", token).status()).isEqualTo(200);
    }
  }

  @Test
  void getRoutesStillForbidSalesWithoutOperatorId() {
    assertError(get("/v1/machines", "X-Service-Token", SALES_TOKEN), 403, "CALLER_NOT_ALLOWED");
  }
}
