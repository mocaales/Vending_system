package si.vending.machine.machine.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import si.vending.machine.core.support.Http;

/**
 * Base for the machine key tests (issue #18).
 *
 * <p>SHARED CONTRACT: backend/machine-service/openapi.yaml (KeyIn, KeyOut, KeyCreated, KeyList,
 * MachineKeyVerifyIn/Out). The key routes are operator-only (gateway token + X-Operator-Id, as in
 * #17); {@code POST /v1/internal/machine-keys/verify} is for the gateway token only.
 */
public abstract class MachineKeyApiTest extends MachineApiTest {

  public static final String VERIFY = "/v1/internal/machine-keys/verify";

  /** A created machine: id, and the key issued with it. */
  public record Created(String machineId, String keyId, String machineKey) {}

  /** Creates a machine (1 slot) and returns its id and first key. */
  protected Created newMachineWithKey() {
    Http.Response response =
        createMachine(MachineFixtures.createJson("K" + MachineFixtures.tag(), "A1"));
    assertThat(response.status()).as("setup: create machine: " + response.body()).isEqualTo(201);
    Map<String, Object> body = response.json();
    return new Created(
        (String) body.get("id"), (String) body.get("keyId"), (String) body.get("machineKey"));
  }

  protected static String keysPath(String machineId) {
    return "/v1/machines/" + machineId + "/keys";
  }

  protected Http.Response createKey(String machineId, String json) {
    return post(keysPath(machineId), json, operator());
  }

  protected Http.Response createKey(String machineId) {
    return createKey(machineId, "{}");
  }

  protected Http.Response listKeys(String machineId) {
    return get(keysPath(machineId), operator());
  }

  protected Http.Response revokeKey(String machineId, String keyId) {
    return delete(keysPath(machineId) + "/" + keyId, operator());
  }

  /** Verify as the gateway does: service token only. */
  protected Http.Response verify(String machineKey) {
    return post(
        VERIFY, "{\"machineKey\":\"" + machineKey + "\"}", "X-Service-Token", GATEWAY_TOKEN);
  }

  /** Issues a key and returns its secret; fails the test if not 201. */
  protected String issueKey(String machineId) {
    Http.Response response = createKey(machineId);
    assertThat(response.status()).as("setup: POST keys: " + response.body()).isEqualTo(201);
    return (String) response.json().get("machineKey");
  }

  @SuppressWarnings("unchecked")
  protected Map<String, Object> keyEntry(String machineId, String keyId) {
    Http.Response list = listKeys(machineId);
    assertThat(list.status()).as("GET keys: " + list.body()).isEqualTo(200);
    List<Map<String, Object>> items = (List<Map<String, Object>>) (Object) list.items();
    return items.stream().filter(k -> keyId.equals(k.get("keyId"))).findFirst().orElse(null);
  }
}
