package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineApiTest;
import si.vending.machine.machine.support.MachineFixtures;

/** #17 criterion 2: PUT /v1/machines/{id} (idempotent replacement; 409 MACHINE_RETIRED). */
class MachineUpdateTest extends MachineApiTest {

  private static final String BODY =
      MachineFixtures.updateJson("New name", "New address 5", 46.1, 14.6);

  @Test
  void replacesEditableFieldsAndKeepsCodeAndSlots() {
    String id = newMachine(2);
    Map<String, Object> before = getMachine(id).json();

    Http.Response response = put("/v1/machines/" + id, BODY, operator());

    assertThat(response.status()).isEqualTo(200);
    Map<String, Object> after = response.json();
    assertThat(after.get("name")).isEqualTo("New name");
    assertThat(after.get("address")).isEqualTo("New address 5");
    assertThat(((Number) after.get("latitude")).doubleValue()).isEqualTo(46.1);
    assertThat(((Number) after.get("longitude")).doubleValue()).isEqualTo(14.6);
    assertThat(after.get("id")).isEqualTo(id);
    assertThat(after.get("code")).isEqualTo(before.get("code"));
    assertThat(after.get("slots")).isEqualTo(before.get("slots"));
    assertThat(after.get("status")).isEqualTo("ACTIVE");
    assertThat(after.get("createdAt")).isEqualTo(before.get("createdAt"));
    assertThat(getMachine(id).json().get("name")).isEqualTo("New name");
  }

  @Test
  void isIdempotent() {
    String id = newMachine(1);

    Map<String, Object> first = new HashMap<>(put("/v1/machines/" + id, BODY, operator()).json());
    Http.Response secondResponse = put("/v1/machines/" + id, BODY, operator());
    Map<String, Object> second = new HashMap<>(secondResponse.json());

    assertThat(secondResponse.status()).isEqualTo(200);
    first.remove("updatedAt");
    second.remove("updatedAt");
    assertThat(second).isEqualTo(first);
  }

  @Test
  void doesNotChangeStatus() {
    String id = newMachine(1);
    setStatus(id, "SERVICE");

    Http.Response response = put("/v1/machines/" + id, BODY, operator());

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().get("status")).isEqualTo("SERVICE");
  }

  @Test
  void retiredMachineCannotBeChanged() {
    String id = newMachine(1);
    assertThat(retire(id).status()).isEqualTo(204);

    assertError(put("/v1/machines/" + id, BODY, operator()), 409, "MACHINE_RETIRED");
    assertThat(getMachine(id).json().get("name")).isNotEqualTo("New name");
  }

  @Test
  void unknownMachineIsNotFound() {
    assertError(
        put("/v1/machines/" + UUID.randomUUID(), BODY, operator()), 404, "MACHINE_NOT_FOUND");
  }

  @Test
  void malformedIdIsBadRequest() {
    assertError(put("/v1/machines/xyz", BODY, operator()), 400, "BAD_REQUEST");
  }

  @Test
  void codeAndSlotsAreNotEditable() {
    String id = newMachine(1);
    String withCode =
        """
        {"code":"OTHER","name":"n","address":"a","latitude":1,"longitude":1}\
        """;
    String withSlots =
        """
        {"name":"n","address":"a","latitude":1,"longitude":1,"slots":[{"slotCode":"Z9"}]}\
        """;

    assertError(put("/v1/machines/" + id, withCode, operator()), 422, "VALIDATION_ERROR");
    assertError(put("/v1/machines/" + id, withSlots, operator()), 422, "VALIDATION_ERROR");
  }

  @Test
  void invalidFieldsAreRejectedWithDetails() {
    String id = newMachine(1);

    assertInvalidField(
        put("/v1/machines/" + id, MachineFixtures.updateJson("n", "a", 91, 14), operator()),
        "latitude");
    assertInvalidField(
        put("/v1/machines/" + id, MachineFixtures.updateJson("n", "a", 46, -181), operator()),
        "longitude");
    assertInvalidField(
        put("/v1/machines/" + id, MachineFixtures.updateJson("", "a", 46, 14), operator()), "name");
    assertInvalidField(
        put("/v1/machines/" + id, "{\"name\":\"n\",\"latitude\":1,\"longitude\":1}", operator()),
        "address");
  }

  @Test
  void malformedJsonIsBadRequest() {
    String id = newMachine(1);

    assertError(put("/v1/machines/" + id, "{\"name\":", operator()), 400, "BAD_REQUEST");
  }

  @Test
  void onlyOperatorsMayUpdate() {
    String id = newMachine(1);

    assertError(put("/v1/machines/" + id, BODY), 401, "UNAUTHENTICATED");
    assertError(
        put("/v1/machines/" + id, BODY, "X-Service-Token", INVENTORY_TOKEN),
        403,
        "CALLER_NOT_ALLOWED");
  }
}
