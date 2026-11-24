package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineFixtures;
import si.vending.machine.machine.support.MachineKeyApiTest;

/**
 * Security review L1: caller identity is strict, as in sales and monitoring. The service token must
 * appear once; a gateway request carries at most one identity header, exactly once, as a canonical
 * UUID; and a presented identity is never silently dropped (no downgrade to the bare gateway).
 * Machine identity (X-Machine-Id) is accepted nowhere in machine-service.
 */
class MachineIdentityHardeningTest extends MachineKeyApiTest {

  private static final String OP_ID = MachineFixtures.OPERATOR_ID;
  private static final String OTHER_ID = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";
  private static final String BODY =
      "{\"name\":\"N\",\"address\":\"A 1\",\"latitude\":46.1,\"longitude\":14.5}";

  /** All operator routes of an existing machine plus verify, sent with the given headers. */
  private Http.Response[] allRoutes(String machineId, String keyId, String... headers) {
    String[] json = new String[headers.length + 2];
    json[0] = "Content-Type";
    json[1] = "application/json";
    System.arraycopy(headers, 0, json, 2, headers.length);
    return new Http.Response[] {
      get("/v1/machines", headers),
      get("/v1/machines/" + machineId, headers),
      post("/v1/machines", MachineFixtures.createJson("IH" + MachineFixtures.tag(), "A1"), json),
      put("/v1/machines/" + machineId, BODY, headers),
      put("/v1/machines/" + machineId + "/status", "{\"status\":\"SERVICE\"}", headers),
      delete("/v1/machines/" + machineId, headers),
      get(keysPath(machineId), headers),
      post(keysPath(machineId), "{}", json),
      delete(keysPath(machineId) + "/" + keyId, headers),
      post(VERIFY, "{\"machineKey\":\"mk_nope\"}", json)
    };
  }

  private void assertAll(Http.Response[] responses, int status, String code) {
    for (int i = 0; i < responses.length; i++) {
      assertThat(responses[i].status())
          .as("route #" + i + ": " + responses[i].body())
          .isEqualTo(status);
      assertError(responses[i], status, code);
    }
  }

  // --- service token exactly once

  @Test
  void serviceTokenSentTwiceIs401() {
    Created m = newMachineWithKey();
    assertAll(
        allRoutes(
            m.machineId(),
            m.keyId(),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Operator-Id",
            OP_ID),
        401,
        "UNAUTHENTICATED");
    assertAll(
        allRoutes(
            m.machineId(),
            m.keyId(),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Service-Token",
            INVENTORY_TOKEN,
            "X-Operator-Id",
            OP_ID),
        401,
        "UNAUTHENTICATED");
  }

  @Test
  void commaJoinedServiceTokenIs401() {
    Created m = newMachineWithKey();
    assertAll(
        allRoutes(
            m.machineId(),
            m.keyId(),
            "X-Service-Token",
            GATEWAY_TOKEN + "," + GATEWAY_TOKEN,
            "X-Operator-Id",
            OP_ID),
        401,
        "UNAUTHENTICATED");
  }

  // --- ambiguous identity headers

  @Test
  void operatorAndMachineIdTogetherIs401() {
    Created m = newMachineWithKey();
    assertAll(
        allRoutes(
            m.machineId(),
            m.keyId(),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Operator-Id",
            OP_ID,
            "X-Machine-Id",
            m.machineId()),
        401,
        "UNAUTHENTICATED");
  }

  @Test
  void operatorIdSentTwiceIs401() {
    Created m = newMachineWithKey();
    assertAll(
        allRoutes(
            m.machineId(),
            m.keyId(),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Operator-Id",
            OP_ID,
            "X-Operator-Id",
            OP_ID),
        401,
        "UNAUTHENTICATED");
  }

  @Test
  void commaJoinedOperatorIdIs401() {
    Created m = newMachineWithKey();
    for (String joined : new String[] {OP_ID + "," + OTHER_ID, OP_ID + ", " + OTHER_ID}) {
      assertAll(
          allRoutes(
              m.machineId(), m.keyId(), "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", joined),
          401,
          "UNAUTHENTICATED");
    }
  }

  @Test
  void machineIdSentTwiceOrCommaJoinedIs401() {
    Created m = newMachineWithKey();
    assertAll(
        allRoutes(
            m.machineId(),
            m.keyId(),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Machine-Id",
            m.machineId(),
            "X-Machine-Id",
            m.machineId()),
        401,
        "UNAUTHENTICATED");
    assertAll(
        allRoutes(
            m.machineId(),
            m.keyId(),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Machine-Id",
            m.machineId() + "," + OTHER_ID),
        401,
        "UNAUTHENTICATED");
  }

  @Test
  void nonCanonicalUuidIs401() {
    Created m = newMachineWithKey();
    for (String bad : new String[] {"1-1-1-1-1", "2f0e3c1a7b9d4c558a1e6d4b9f0c3e21"}) {
      assertAll(
          allRoutes(
              m.machineId(), m.keyId(), "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", bad),
          401,
          "UNAUTHENTICATED");
    }
    assertAll(
        allRoutes(
            m.machineId(),
            m.keyId(),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Machine-Id",
            "1-1-1-1-1"),
        401,
        "UNAUTHENTICATED");
  }

  // --- no downgrade, machine identity accepted nowhere

  @Test
  void gatewayWithOperatorIdOnVerifyIsForbiddenNotDowngraded() {
    Created m = newMachineWithKey();

    assertError(
        post(
            VERIFY,
            "{\"machineKey\":\"" + m.machineKey() + "\"}",
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Operator-Id",
            OP_ID),
        403,
        "CALLER_NOT_ALLOWED");
  }

  @Test
  void gatewayWithMachineIdIsForbiddenOnEveryRoute() {
    Created m = newMachineWithKey();
    assertAll(
        allRoutes(
            m.machineId(),
            m.keyId(),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Machine-Id",
            m.machineId()),
        403,
        "CALLER_NOT_ALLOWED");
  }

  // --- positive paths still work

  @Test
  void operatorOnCrudAndBareGatewayOnVerifyStillWork() {
    Created m = newMachineWithKey();

    assertThat(get("/v1/machines/" + m.machineId(), operator()).status()).isEqualTo(200);
    assertThat(put("/v1/machines/" + m.machineId(), BODY, operator()).status()).isEqualTo(200);
    assertThat(listKeys(m.machineId()).status()).isEqualTo(200);
    assertThat(verify(m.machineKey()).status()).isEqualTo(200);
    assertThat(get("/v1/machines/" + m.machineId(), "X-Service-Token", INVENTORY_TOKEN).status())
        .isEqualTo(200);
  }

  @Test
  void blankIdentityHeaderCountsAsAbsent() {
    Created m = newMachineWithKey();

    assertThat(
            post(
                    VERIFY,
                    "{\"machineKey\":\"" + m.machineKey() + "\"}",
                    "X-Service-Token",
                    GATEWAY_TOKEN,
                    "X-Operator-Id",
                    " ")
                .status())
        .isEqualTo(200);
    assertError(
        delete(
            "/v1/machines/" + m.machineId(),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Operator-Id",
            " "),
        403,
        "CALLER_NOT_ALLOWED");
  }

  // --- rejected requests have no side effect and call no Inventory

  @Test
  void rejectedRequestsCreateNothingChangeNothingAndCallNoInventory() {
    Created m = newMachineWithKey();
    String code = "SE" + MachineFixtures.tag();
    String[] ambiguous = {
      "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", OP_ID, "X-Machine-Id", m.machineId()
    };
    String[] json = new String[ambiguous.length + 2];
    json[0] = "Content-Type";
    json[1] = "application/json";
    System.arraycopy(ambiguous, 0, json, 2, ambiguous.length);
    int before = INVENTORY.calls(m.machineId()).size();

    assertError(
        post("/v1/machines", MachineFixtures.createJson(code, "A1"), json), 401, "UNAUTHENTICATED");
    assertError(delete("/v1/machines/" + m.machineId(), ambiguous), 401, "UNAUTHENTICATED");
    assertError(
        put("/v1/machines/" + m.machineId() + "/status", "{\"status\":\"SERVICE\"}", ambiguous),
        401,
        "UNAUTHENTICATED");
    assertError(post(keysPath(m.machineId()), "{}", json), 401, "UNAUTHENTICATED");
    assertError(
        delete(keysPath(m.machineId()) + "/" + m.keyId(), ambiguous), 401, "UNAUTHENTICATED");

    assertThat(get("/v1/machines?q=" + code, operator()).body()).doesNotContain(code);
    assertThat(statusOf(m.machineId())).isNotEqualTo("RETIRED").isNotEqualTo("SERVICE");
    assertThat(listKeys(m.machineId()).body()).contains(m.keyId());
    assertThat(INVENTORY.calls(m.machineId())).hasSize(before);
    assertThat(UUID.fromString(m.machineId())).isNotNull();
  }
}
