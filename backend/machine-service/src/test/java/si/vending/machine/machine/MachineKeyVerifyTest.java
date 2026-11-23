package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineFixtures;
import si.vending.machine.machine.support.MachineKeyApiTest;

/** #18 criteria 5 and 6: POST /v1/internal/machine-keys/verify (gateway only). */
class MachineKeyVerifyTest extends MachineKeyApiTest {

  private static final String G = GATEWAY_TOKEN;
  private static final String OP_ID = MachineFixtures.OPERATOR_ID;

  private Http.Response verifyJson(String json, String... headers) {
    return post(VERIFY, json, headers);
  }

  // --- valid key

  @Test
  void validKeyReturnsMachineIdAndStatus() {
    Created m = newMachineWithKey();

    Http.Response response = verify(m.machineKey());

    assertThat(response.status()).isEqualTo(200);
    Map<String, Object> body = response.json();
    assertThat(body.keySet()).containsExactlyInAnyOrder("machineId", "status");
    assertThat(body.get("machineId")).isEqualTo(m.machineId());
    assertThat(body.get("status")).isEqualTo("ACTIVE");
    assertThat(response.body()).doesNotContain(m.machineKey());
  }

  @Test
  void statusReflectsTheMachineStatus() {
    Created m = newMachineWithKey();
    setStatus(m.machineId(), "SERVICE");

    Http.Response response = verify(m.machineKey());

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().get("status")).isEqualTo("SERVICE");
  }

  @Test
  void eachKeyResolvesToItsOwnMachine() {
    Created a = newMachineWithKey();
    Created b = newMachineWithKey();

    assertThat(verify(a.machineKey()).json().get("machineId")).isEqualTo(a.machineId());
    assertThat(verify(b.machineKey()).json().get("machineId")).isEqualTo(b.machineId());
  }

  // --- 401 cases

  @Test
  void unknownKeyIsUnauthenticated() {
    assertError(verify("mk_" + "A".repeat(43)), 401, "UNAUTHENTICATED");
  }

  @Test
  void revokedKeyIsUnauthenticated() {
    Created m = newMachineWithKey();
    revokeKey(m.machineId(), m.keyId());

    assertError(verify(m.machineKey()), 401, "UNAUTHENTICATED");
  }

  @Test
  void keysOfARetiredMachineAreUnauthenticated() {
    Created m = newMachineWithKey();
    String second = issueKey(m.machineId());
    assertThat(verify(m.machineKey()).status()).isEqualTo(200);
    assertThat(retire(m.machineId()).status()).isEqualTo(204);

    assertError(verify(m.machineKey()), 401, "UNAUTHENTICATED");
    assertError(verify(second), 401, "UNAUTHENTICATED");
  }

  @Test
  void retirementRevokesAllKeysVisibleInTheList() {
    Created m = newMachineWithKey();
    issueKey(m.machineId());
    retire(m.machineId());

    Http.Response list = listKeys(m.machineId());

    assertThat(list.status()).isEqualTo(200);
    assertThat(list.items()).hasSize(2);
    assertThat(list.body()).doesNotContain("\"ACTIVE\"");
  }

  @Test
  void keyDiffersByAnyCharacter() {
    Created m = newMachineWithKey();

    assertError(verify(m.machineKey().toUpperCase()), 401, "UNAUTHENTICATED");
    assertError(verify(m.machineKey() + "x"), 401, "UNAUTHENTICATED");
    assertError(verify(" " + m.machineKey()), 401, "UNAUTHENTICATED");
    assertError(verify(m.machineKey().substring(1)), 401, "UNAUTHENTICATED");
  }

  @Test
  void theMachineIdIsNotAKey() {
    Created m = newMachineWithKey();

    assertError(verify(m.machineId()), 401, "UNAUTHENTICATED");
    assertError(verify(m.keyId()), 401, "UNAUTHENTICATED");
  }

  @Test
  void doesNotRevealWhyTheKeyWasRejected() {
    Created revoked = newMachineWithKey();
    revokeKey(revoked.machineId(), revoked.keyId());
    Created retired = newMachineWithKey();
    retire(retired.machineId());

    Http.Response unknown = verify("mk_" + "B".repeat(43));
    Http.Response revokedResponse = verify(revoked.machineKey());
    Http.Response retiredResponse = verify(retired.machineKey());

    for (Http.Response r : new Http.Response[] {unknown, revokedResponse, retiredResponse}) {
      assertError(r, 401, "UNAUTHENTICATED");
    }
    assertThat(revokedResponse.json().get("message")).isEqualTo(unknown.json().get("message"));
    assertThat(retiredResponse.json().get("message")).isEqualTo(unknown.json().get("message"));
    for (Http.Response r : new Http.Response[] {unknown, revokedResponse, retiredResponse}) {
      assertThat(r.body())
          .doesNotContain(retired.machineId())
          .doesNotContain(revoked.machineId())
          .doesNotContainIgnoringCase("retired")
          .doesNotContainIgnoringCase("revoked");
    }
  }

  // --- malformed body (400 or 422 per the contract)

  @Test
  void malformedBodiesAreRejected() {
    assertRejected(verifyJson("{}", "X-Service-Token", G));
    assertRejected(verifyJson("{\"machineKey\":\"\"}", "X-Service-Token", G));
    assertRejected(verifyJson("{\"machineKey\":null}", "X-Service-Token", G));
    assertRejected(verifyJson("{\"machineKey\":123}", "X-Service-Token", G));
    assertRejected(verifyJson("{\"machineKey\":[\"a\"]}", "X-Service-Token", G));
    assertRejected(verifyJson("{not json", "X-Service-Token", G));
    assertRejected(verifyJson("", "X-Service-Token", G));
    assertRejected(
        verifyJson("{\"machineKey\":\"abc\",\"machineId\":\"x\"}", "X-Service-Token", G));
  }

  @Test
  void missingBodyIsRejected() {
    assertRejected(post(VERIFY, null, "X-Service-Token", G));
  }

  // --- who may call it: the gateway token only

  @Test
  void noTokenIsUnauthenticated() {
    Created m = newMachineWithKey();

    assertError(verifyJson("{\"machineKey\":\"" + m.machineKey() + "\"}"), 401, "UNAUTHENTICATED");
  }

  @Test
  void unknownTokenIsUnauthenticated() {
    Created m = newMachineWithKey();

    assertError(
        verifyJson("{\"machineKey\":\"" + m.machineKey() + "\"}", "X-Service-Token", "nope"),
        401,
        "UNAUTHENTICATED");
  }

  @Test
  void otherServicesAreForbidden() {
    Created m = newMachineWithKey();
    String json = "{\"machineKey\":\"" + m.machineKey() + "\"}";

    for (String token : new String[] {SALES_TOKEN, INVENTORY_TOKEN, MONITORING_TOKEN}) {
      Http.Response r = verifyJson(json, "X-Service-Token", token);
      assertError(r, 403, "CALLER_NOT_ALLOWED");
      assertThat(r.body()).doesNotContain(m.machineId());
    }
  }

  @Test
  void operatorHeaderAloneDoesNotGrantVerify() {
    Created m = newMachineWithKey();

    assertError(
        verifyJson("{\"machineKey\":\"" + m.machineKey() + "\"}", "X-Operator-Id", OP_ID),
        401,
        "UNAUTHENTICATED");
  }

  @Test
  void operatorHeaderDoesNotLiftOtherServicesOnVerify() {
    Created m = newMachineWithKey();

    for (String token : new String[] {SALES_TOKEN, INVENTORY_TOKEN, MONITORING_TOKEN}) {
      assertError(
          verifyJson(
              "{\"machineKey\":\"" + m.machineKey() + "\"}",
              "X-Service-Token",
              token,
              "X-Operator-Id",
              OP_ID),
          403,
          "CALLER_NOT_ALLOWED");
    }
  }

  @Test
  void callerCheckComesBeforeBodyValidation() {
    assertError(verifyJson("{}", "X-Service-Token", SALES_TOKEN), 403, "CALLER_NOT_ALLOWED");
  }

  @Test
  void wrongMethodsAreNotAllowed() {
    Http.Response r = get(VERIFY, "X-Service-Token", G);

    assertThat(r.status()).isIn(404, 405);
  }
}
