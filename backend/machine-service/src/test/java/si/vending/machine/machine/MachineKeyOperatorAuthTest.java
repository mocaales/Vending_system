package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineFixtures;
import si.vending.machine.machine.support.MachineKeyApiTest;

/**
 * The key routes are x-callers [OPERATOR] in openapi.yaml: they follow the #17 operator rule
 * (gateway X-Service-Token AND a valid UUID X-Operator-Id), see MachineOperatorAuthTest. Unlike the
 * machine GET routes, no other service may read keys.
 */
class MachineKeyOperatorAuthTest extends MachineKeyApiTest {

  private static final String OP_ID = MachineFixtures.OPERATOR_ID;

  /** Sends all three key routes of a real machine and key with the given headers. */
  private Http.Response[] keyRoutes(Created m, String... headers) {
    String[] json = new String[headers.length + 2];
    json[0] = "Content-Type";
    json[1] = "application/json";
    System.arraycopy(headers, 0, json, 2, headers.length);
    return new Http.Response[] {
      get(keysPath(m.machineId()), headers),
      post(keysPath(m.machineId()), "{}", json),
      delete(keysPath(m.machineId()) + "/" + m.keyId(), headers)
    };
  }

  private void assertAll(Http.Response[] responses, int status, String code) {
    for (Http.Response r : responses) {
      assertError(r, status, code);
    }
  }

  @Test
  void noCredentialIsUnauthenticated() {
    Created m = newMachineWithKey();

    assertAll(keyRoutes(m), 401, "UNAUTHENTICATED");
  }

  @Test
  void operatorHeaderAloneIsUnauthenticated() {
    Created m = newMachineWithKey();

    assertAll(keyRoutes(m, "X-Operator-Id", OP_ID), 401, "UNAUTHENTICATED");
  }

  @Test
  void unknownTokenIsUnauthenticated() {
    Created m = newMachineWithKey();

    assertAll(
        keyRoutes(m, "X-Service-Token", "nope", "X-Operator-Id", OP_ID), 401, "UNAUTHENTICATED");
  }

  @Test
  void otherServicesWithOperatorIdAreForbidden() {
    Created m = newMachineWithKey();

    for (String token : new String[] {SALES_TOKEN, INVENTORY_TOKEN, MONITORING_TOKEN}) {
      assertAll(
          keyRoutes(m, "X-Service-Token", token, "X-Operator-Id", OP_ID),
          403,
          "CALLER_NOT_ALLOWED");
    }
  }

  @Test
  void otherServicesCannotReadKeysEvenWithoutOperatorId() {
    Created m = newMachineWithKey();

    for (String token : new String[] {SALES_TOKEN, INVENTORY_TOKEN, MONITORING_TOKEN}) {
      assertError(
          get(keysPath(m.machineId()), "X-Service-Token", token), 403, "CALLER_NOT_ALLOWED");
    }
  }

  @Test
  void gatewayWithoutOperatorIdIsForbidden() {
    Created m = newMachineWithKey();

    assertAll(keyRoutes(m, "X-Service-Token", GATEWAY_TOKEN), 403, "CALLER_NOT_ALLOWED");
    assertAll(
        keyRoutes(m, "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", ""),
        403,
        "CALLER_NOT_ALLOWED");
  }

  @Test
  void malformedOperatorIdIsUnauthenticated() {
    Created m = newMachineWithKey();

    assertAll(
        keyRoutes(m, "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", "not-a-uuid"),
        401,
        "UNAUTHENTICATED");
  }

  @Test
  void rejectedCallersChangeNothing() {
    Created m = newMachineWithKey();
    keyRoutes(m, "X-Service-Token", SALES_TOKEN, "X-Operator-Id", OP_ID);
    keyRoutes(m);

    assertThat(listKeys(m.machineId()).items()).hasSize(1);
    assertThat(verify(m.machineKey()).status()).isEqualTo(200);
  }

  @Test
  void guardRunsBeforeLookupSoUnknownMachineDoesNotLeak() {
    Http.Response r = get(keysPath(UUID.randomUUID().toString()), "X-Service-Token", SALES_TOKEN);

    assertError(r, 403, "CALLER_NOT_ALLOWED");
  }
}
