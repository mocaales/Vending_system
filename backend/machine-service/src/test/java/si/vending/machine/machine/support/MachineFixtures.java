package si.vending.machine.machine.support;

import java.util.UUID;
import si.vending.machine.core.support.AbstractApiTest;
import si.vending.machine.core.support.Http;

/** Request builders shared by the machine tests; all talk to the service on {@code port}. */
public final class MachineFixtures {

  public static final String OPERATOR_ID = "2f0e3c1a-7b9d-4c55-8a1e-6d4b9f0c3e21";

  private MachineFixtures() {}

  /** An operator request as the gateway forwards it: gateway service token plus operator id. */
  public static String[] operator() {
    return new String[] {
      "X-Service-Token", AbstractApiTest.GATEWAY_TOKEN, "X-Operator-Id", OPERATOR_ID
    };
  }

  /** A short unique tag so that tests sharing one database never see each other's machines. */
  public static String tag() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  /** Slot codes become {@code {"slotCode":"A1"}}. */
  public static String createJson(String code, String... slotCodes) {
    StringBuilder slots = new StringBuilder();
    for (String slot : slotCodes) {
      if (slots.length() > 0) {
        slots.append(',');
      }
      slots.append("{\"slotCode\":\"").append(slot).append("\"}");
    }
    return """
    {"code":"%s","name":"Machine %s","address":"Trg republike 1, Maribor",\
    "latitude":46.5547,"longitude":15.6459,"slots":[%s]}\
    """
        .formatted(code, code, slots);
  }

  public static String createJson(
      String code, String name, String address, double lat, double lon) {
    return """
    {"code":"%s","name":"%s","address":"%s","latitude":%s,"longitude":%s,\
    "slots":[{"slotCode":"A1"}]}\
    """
        .formatted(code, name, address, lat, lon);
  }

  public static String updateJson(String name, String address, double lat, double lon) {
    return """
    {"name":"%s","address":"%s","latitude":%s,"longitude":%s}\
    """
        .formatted(name, address, lat, lon);
  }

  public static Http.Response create(int port, String json) {
    return Http.post(port, "/v1/machines", json, operator());
  }

  /** Creates a machine with slots A1..A{slots} and returns its id; fails the test if not 201. */
  public static String createMachine(int port, String code, int slots) {
    String[] codes = new String[slots];
    for (int i = 0; i < slots; i++) {
      codes[i] = "A" + (i + 1);
    }
    Http.Response response = create(port, createJson(code, codes));
    if (response.status() != 201) {
      throw new AssertionError(
          "setup: POST /v1/machines expected 201 but was %d: %s"
              .formatted(response.status(), response.body()));
    }
    return (String) response.json().get("id");
  }

  public static Http.Response getMachine(int port, String id) {
    return Http.get(port, "/v1/machines/" + id, operator());
  }

  public static Http.Response put(int port, String path, String json, String... headers) {
    String[] all = new String[headers.length + 2];
    all[0] = "Content-Type";
    all[1] = "application/json";
    System.arraycopy(headers, 0, all, 2, headers.length);
    return Http.send(port, "PUT", path, json, all);
  }

  public static Http.Response delete(int port, String path, String... headers) {
    return Http.send(port, "DELETE", path, null, headers);
  }

  public static Http.Response setStatus(int port, String id, String status) {
    return put(
        port, "/v1/machines/" + id + "/status", "{\"status\":\"" + status + "\"}", operator());
  }
}
