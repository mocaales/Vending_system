package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineKeyApiTest;

/** #18 criteria 2 and 3: GET /v1/machines/{id}/keys and DELETE /v1/machines/{id}/keys/{keyId}. */
class MachineKeyListRevokeTest extends MachineKeyApiTest {

  private static final Set<String> KEY_OUT_FIELDS =
      Set.of("keyId", "label", "hint", "status", "createdAt", "revokedAt");
  private static final Set<String> KEY_OUT_REQUIRED =
      Set.of("keyId", "hint", "status", "createdAt");

  // --- GET: metadata only

  @SuppressWarnings("unchecked")
  @Test
  void listsMetadataOnlyWithExactFieldSet() {
    Created m = newMachineWithKey();
    Map<String, Object> second = createKey(m.machineId(), "{\"label\":\"second\"}").json();
    String secret = (String) second.get("machineKey");

    Http.Response response = listKeys(m.machineId());

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().keySet()).containsExactly("items");
    List<Map<String, Object>> items = (List<Map<String, Object>>) (Object) response.items();
    assertThat(items).hasSize(2);
    for (Map<String, Object> key : items) {
      assertThat(key.keySet()).isSubsetOf(KEY_OUT_FIELDS).containsAll(KEY_OUT_REQUIRED);
      assertThat(key.get("status")).isEqualTo("ACTIVE");
      assertThat(UUID.fromString((String) key.get("keyId"))).isNotNull();
      assertThat(Instant.parse((String) key.get("createdAt"))).isNotNull();
      assertThat(key.get("hint")).asString().hasSize(4);
    }
    Map<String, Object> entry = keyEntry(m.machineId(), (String) second.get("keyId"));
    assertThat(entry.get("label")).isEqualTo("second");
    assertThat(entry.get("hint")).isEqualTo(secret.substring(secret.length() - 4));
  }

  @Test
  void hintIsTheLastFourCharactersOfTheMachineCreationKey() {
    Created m = newMachineWithKey();

    Map<String, Object> entry = keyEntry(m.machineId(), m.keyId());

    assertThat(entry).isNotNull();
    assertThat(entry.get("hint")).isEqualTo(m.machineKey().substring(m.machineKey().length() - 4));
  }

  @Test
  void listNeverContainsSecretOrHash() {
    Created m = newMachineWithKey();
    String second = issueKey(m.machineId());

    String body = listKeys(m.machineId()).body();

    assertThat(body)
        .doesNotContain(m.machineKey())
        .doesNotContain(second)
        .doesNotContain(MachineKeyStorageTest.sha256(m.machineKey()))
        .doesNotContain(MachineKeyStorageTest.sha256(second))
        .doesNotContainIgnoringCase("machineKey")
        .doesNotContainIgnoringCase("hash");
  }

  @Test
  void listIncludesRevokedKeysWithRevokedAt() {
    Created m = newMachineWithKey();
    issueKey(m.machineId());
    revokeKey(m.machineId(), m.keyId());

    Map<String, Object> revoked = keyEntry(m.machineId(), m.keyId());

    assertThat(listKeys(m.machineId()).items()).hasSize(2);
    assertThat(revoked.get("status")).isEqualTo("REVOKED");
    assertThat(Instant.parse((String) revoked.get("revokedAt"))).isNotNull();
  }

  @Test
  void listOnlyShowsKeysOfThisMachine() {
    Created a = newMachineWithKey();
    Created b = newMachineWithKey();

    assertThat(keyEntry(a.machineId(), b.keyId())).isNull();
    assertThat(listKeys(a.machineId()).items()).hasSize(1);
  }

  @Test
  void listOfUnknownMachineIsNotFound() {
    assertError(listKeys(UUID.randomUUID().toString()), 404, "MACHINE_NOT_FOUND");
  }

  @Test
  void listWithMalformedMachineIdIsBadRequest() {
    assertError(listKeys("not-a-uuid"), 400, "BAD_REQUEST");
  }

  // --- DELETE: revoke immediately

  @Test
  void revokeTakesEffectImmediately() {
    Created m = newMachineWithKey();
    String second = issueKey(m.machineId());
    assertThat(verify(second).status()).isEqualTo(200);

    Http.Response response = revokeKey(m.machineId(), m.keyId());

    assertThat(response.status()).isEqualTo(204);
    assertThat(response.body()).isEmpty();
    assertError(verify(m.machineKey()), 401, "UNAUTHENTICATED");
    assertThat(verify(second).status()).as("other key unaffected").isEqualTo(200);
    assertThat(keyEntry(m.machineId(), m.keyId()).get("status")).isEqualTo("REVOKED");
  }

  @Test
  void lastActiveKeyCanBeRevoked() {
    Created m = newMachineWithKey();

    assertThat(revokeKey(m.machineId(), m.keyId()).status()).isEqualTo(204);

    assertError(verify(m.machineKey()), 401, "UNAUTHENTICATED");
    assertThat(keyEntry(m.machineId(), m.keyId()).get("status")).isEqualTo("REVOKED");
  }

  @Test
  void revokeIsIdempotentAndKeepsTheFirstRevokedAt() {
    Created m = newMachineWithKey();
    assertThat(revokeKey(m.machineId(), m.keyId()).status()).isEqualTo(204);
    Object firstRevokedAt = keyEntry(m.machineId(), m.keyId()).get("revokedAt");
    assertThat(firstRevokedAt).isNotNull();

    Http.Response again = revokeKey(m.machineId(), m.keyId());

    assertThat(again.status()).isEqualTo(204);
    assertThat(keyEntry(m.machineId(), m.keyId()).get("revokedAt")).isEqualTo(firstRevokedAt);
  }

  @Test
  void unknownKeyIsNotFound() {
    Created m = newMachineWithKey();

    assertError(revokeKey(m.machineId(), UUID.randomUUID().toString()), 404, "KEY_NOT_FOUND");
  }

  @Test
  void unknownMachineIsNotFound() {
    assertError(
        revokeKey(UUID.randomUUID().toString(), UUID.randomUUID().toString()),
        404,
        "MACHINE_NOT_FOUND");
  }

  @Test
  void keyOfAnotherMachineIsNotFoundAndStaysValid() {
    Created a = newMachineWithKey();
    Created b = newMachineWithKey();

    assertError(revokeKey(a.machineId(), b.keyId()), 404, "KEY_NOT_FOUND");

    assertThat(verify(b.machineKey()).status()).isEqualTo(200);
    assertThat(keyEntry(b.machineId(), b.keyId()).get("status")).isEqualTo("ACTIVE");
  }

  @Test
  void malformedIdsAreBadRequest() {
    Created m = newMachineWithKey();

    assertError(revokeKey(m.machineId(), "not-a-uuid"), 400, "BAD_REQUEST");
    assertError(revokeKey("not-a-uuid", m.keyId()), 400, "BAD_REQUEST");
  }

  @Test
  void revokingFreesRoomForANewKey() {
    Created m = newMachineWithKey();
    createKey(m.machineId());
    createKey(m.machineId());
    assertError(createKey(m.machineId()), 409, "KEY_LIMIT_REACHED");

    revokeKey(m.machineId(), m.keyId());

    assertThat(createKey(m.machineId()).status()).isEqualTo(201);
  }
}
