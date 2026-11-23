package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineKeyApiTest;

/** #18 criterion 1: POST /v1/machines/{id}/keys. */
class MachineKeyCreateTest extends MachineKeyApiTest {

  private static final Set<String> KEY_CREATED_FIELDS =
      Set.of("keyId", "label", "hint", "status", "createdAt", "revokedAt", "machineKey");

  @Test
  void createsKeyAndReturnsSecretOnce() {
    Created m = newMachineWithKey();

    Http.Response response = createKey(m.machineId(), "{\"label\":\"simulator\"}");

    assertThat(response.status()).isEqualTo(201);
    Map<String, Object> body = response.json();
    assertThat(body.keySet()).isSubsetOf(KEY_CREATED_FIELDS);
    assertThat(body.keySet()).contains("keyId", "hint", "status", "createdAt", "machineKey");
    assertThat(UUID.fromString((String) body.get("keyId"))).isNotNull();
    assertThat(body.get("label")).isEqualTo("simulator");
    assertThat(body.get("status")).isEqualTo("ACTIVE");
    assertThat(body.get("revokedAt")).isNull();
    assertThat(Instant.parse((String) body.get("createdAt"))).isNotNull();
    String key = (String) body.get("machineKey");
    assertThat(key).isNotBlank().isNotEqualTo(m.machineKey());
    assertThat(body.get("hint")).isEqualTo(key.substring(key.length() - 4));
  }

  @Test
  void secretIsNeverReturnedAgain() {
    Created m = newMachineWithKey();
    Map<String, Object> created = createKey(m.machineId()).json();
    String key = (String) created.get("machineKey");

    Http.Response list = listKeys(m.machineId());

    assertThat(list.status()).isEqualTo(200);
    assertThat(list.body()).doesNotContain(key).doesNotContain("machineKey");
  }

  @Test
  void bodyIsOptional() {
    Created m = newMachineWithKey();

    Http.Response response = post(keysPath(m.machineId()), null, operator());

    assertThat(response.status()).isEqualTo(201);
    assertThat(response.json().get("label")).isNull();
  }

  @Test
  void eachCallIssuesADifferentKey() {
    Created m = newMachineWithKey();

    Map<String, Object> first = createKey(m.machineId()).json();
    Map<String, Object> second = createKey(m.machineId()).json();

    assertThat(first.get("keyId")).isNotEqualTo(second.get("keyId"));
    assertThat(first.get("machineKey")).isNotEqualTo(second.get("machineKey"));
  }

  @Test
  void oldKeysStayValidAndNewKeyWorks() {
    Created m = newMachineWithKey();

    String newKey = issueKey(m.machineId());

    assertThat(verify(m.machineKey()).status()).isEqualTo(200);
    Http.Response verified = verify(newKey);
    assertThat(verified.status()).isEqualTo(200);
    assertThat(verified.json().get("machineId")).isEqualTo(m.machineId());
  }

  // --- limit: at most 3 active keys, the key issued with the machine counts

  @Test
  void fourthActiveKeyIsRejectedWithKeyLimitReached() {
    Created m = newMachineWithKey();
    assertThat(createKey(m.machineId()).status()).isEqualTo(201);
    assertThat(createKey(m.machineId()).status()).isEqualTo(201);

    assertError(createKey(m.machineId()), 409, "KEY_LIMIT_REACHED");

    assertThat(listKeys(m.machineId()).items()).hasSize(3);
  }

  @Test
  void revokedKeysDoNotCountTowardsTheLimit() {
    Created m = newMachineWithKey();
    createKey(m.machineId());
    createKey(m.machineId());
    assertThat(revokeKey(m.machineId(), m.keyId()).status()).isEqualTo(204);

    assertThat(createKey(m.machineId()).status()).isEqualTo(201);

    assertError(createKey(m.machineId()), 409, "KEY_LIMIT_REACHED");
  }

  @Test
  void limitIsPerMachine() {
    Created a = newMachineWithKey();
    createKey(a.machineId());
    createKey(a.machineId());
    Created b = newMachineWithKey();

    assertThat(createKey(b.machineId()).status()).isEqualTo(201);
  }

  // --- machine state

  @Test
  void retiredMachineCannotGetNewKeys() {
    Created m = newMachineWithKey();
    assertThat(retire(m.machineId()).status()).isEqualTo(204);

    assertError(createKey(m.machineId()), 409, "MACHINE_RETIRED");
  }

  @Test
  void serviceStatusMachineCanGetKeys() {
    Created m = newMachineWithKey();
    assertThat(setStatus(m.machineId(), "SERVICE").status()).isEqualTo(200);

    assertThat(createKey(m.machineId()).status()).isEqualTo(201);
  }

  @Test
  void unknownMachineIsNotFound() {
    assertError(createKey(UUID.randomUUID().toString()), 404, "MACHINE_NOT_FOUND");
  }

  @Test
  void malformedMachineIdIsBadRequest() {
    assertError(createKey("not-a-uuid"), 400, "BAD_REQUEST");
  }

  // --- label validation (hardening as in #17)

  @Test
  void labelOfMaxLengthIsAccepted() {
    Created m = newMachineWithKey();

    Http.Response response = createKey(m.machineId(), "{\"label\":\"" + "x".repeat(60) + "\"}");

    assertThat(response.status()).isEqualTo(201);
  }

  @Test
  void tooLongLabelIsValidationError() {
    Created m = newMachineWithKey();

    assertInvalidField(createKey(m.machineId(), "{\"label\":\"" + "x".repeat(61) + "\"}"), "label");
  }

  @Test
  void nulInLabelIsValidationError() {
    Created m = newMachineWithKey();

    assertInvalidField(createKey(m.machineId(), "{\"label\":\"sim\\u0000x\"}"), "label");
  }

  @Test
  void otherControlCharactersInLabelAreValidationErrors() {
    Created m = newMachineWithKey();

    assertInvalidField(createKey(m.machineId(), "{\"label\":\"bell\\u0007\"}"), "label");
    assertInvalidField(createKey(m.machineId(), "{\"label\":\"line\\nbreak\"}"), "label");
  }

  @Test
  void nonStringLabelIsRejected() {
    Created m = newMachineWithKey();

    assertRejected(createKey(m.machineId(), "{\"label\":123}"));
  }

  @Test
  void unknownBodyFieldIsRejected() {
    Created m = newMachineWithKey();

    assertRejected(createKey(m.machineId(), "{\"label\":\"x\",\"status\":\"REVOKED\"}"));
  }

  @Test
  void invalidJsonIsRejected() {
    Created m = newMachineWithKey();

    assertRejected(createKey(m.machineId(), "{not json"));
  }

  @Test
  void rejectedRequestIssuesNoKey() {
    Created m = newMachineWithKey();
    createKey(m.machineId(), "{\"label\":\"" + "x".repeat(61) + "\"}");

    assertThat(listKeys(m.machineId()).items()).hasSize(1);
  }
}
