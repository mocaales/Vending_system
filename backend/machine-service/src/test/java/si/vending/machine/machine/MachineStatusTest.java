package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineApiTest;

/** #17 criterion 3: PUT /v1/machines/{id}/status; RETIRED is terminal. */
class MachineStatusTest extends MachineApiTest {

  @Test
  void activeAndServiceSwitchFreely() {
    String id = newMachine(1);

    Http.Response toService = setStatus(id, "SERVICE");
    Http.Response backToActive = setStatus(id, "ACTIVE");

    assertThat(toService.status()).isEqualTo(200);
    assertThat(toService.json().get("status")).isEqualTo("SERVICE");
    assertThat(toService.json().get("id")).isEqualTo(id);
    assertThat(backToActive.status()).isEqualTo(200);
    assertThat(backToActive.json().get("status")).isEqualTo("ACTIVE");
    assertThat(statusOf(id)).isEqualTo("ACTIVE");
  }

  @Test
  void responseIsAFullMachine() {
    String id = newMachine(2);

    Http.Response response = setStatus(id, "SERVICE");

    assertThat(response.json().keySet())
        .containsExactlyInAnyOrder(
            "id,code,name,address,latitude,longitude,status,slots,createdAt,updatedAt".split(","));
  }

  @Test
  void sameStatusIsOkAndChangesNothing() {
    String id = newMachine(1);
    Object updatedAt = getMachine(id).json().get("updatedAt");

    Http.Response sameActive = setStatus(id, "ACTIVE");
    setStatus(id, "SERVICE");
    Object serviceUpdatedAt = getMachine(id).json().get("updatedAt");
    Http.Response sameService = setStatus(id, "SERVICE");

    assertThat(sameActive.status()).isEqualTo(200);
    assertThat(sameActive.json().get("status")).isEqualTo("ACTIVE");
    assertThat(sameActive.json().get("updatedAt")).isEqualTo(updatedAt);
    assertThat(sameService.status()).isEqualTo(200);
    assertThat(sameService.json().get("updatedAt")).isEqualTo(serviceUpdatedAt);
  }

  @Test
  void requestingRetiredIsAnInvalidTransition() {
    String id = newMachine(1);
    setStatus(id, "SERVICE");

    assertError(setStatus(id, "RETIRED"), 409, "INVALID_STATE_TRANSITION");
    assertThat(statusOf(id)).isEqualTo("SERVICE");
    assertError(setStatus(newMachine(1), "RETIRED"), 409, "INVALID_STATE_TRANSITION");
  }

  @Test
  void retiredMachineCannotLeaveRetired() {
    String id = newMachine(1);
    assertThat(retire(id).status()).isEqualTo(204);

    // openapi.yaml: a retired machine returns MACHINE_RETIRED
    assertError(setStatus(id, "ACTIVE"), 409, "MACHINE_RETIRED");
    assertError(setStatus(id, "SERVICE"), 409, "MACHINE_RETIRED");
    assertThat(statusOf(id)).isEqualTo("RETIRED");
  }

  @Test
  void retiredMachineRequestingRetiredIsStillConflict() {
    String id = newMachine(1);
    retire(id);

    Http.Response response = setStatus(id, "RETIRED");

    // contract allows either 409 code here (openapi says MACHINE_RETIRED, issue says transition)
    assertThat(response.status()).isEqualTo(409);
    assertThat(response.json().get("code")).isIn("MACHINE_RETIRED", "INVALID_STATE_TRANSITION");
  }

  @Test
  void unknownMachineIsNotFound() {
    assertError(setStatus(UUID.randomUUID().toString(), "SERVICE"), 404, "MACHINE_NOT_FOUND");
  }

  @Test
  void malformedIdIsBadRequest() {
    assertError(
        put("/v1/machines/xyz/status", "{\"status\":\"SERVICE\"}", operator()), 400, "BAD_REQUEST");
  }

  @Test
  void invalidBodiesAreRejected() {
    String id = newMachine(1);
    String path = "/v1/machines/" + id + "/status";

    assertRejected(put(path, "{\"status\":\"BROKEN\"}", operator()));
    assertInvalidField(put(path, "{}", operator()), "status");
    assertError(put(path, "{\"status\":\"SERVICE\",\"x\":1}", operator()), 422, "VALIDATION_ERROR");
    assertError(put(path, "{\"status\":", operator()), 400, "BAD_REQUEST");
    assertThat(statusOf(id)).isEqualTo("ACTIVE");
  }

  @Test
  void onlyOperatorsMayChangeStatus() {
    String id = newMachine(1);
    String path = "/v1/machines/" + id + "/status";
    String body = "{\"status\":\"SERVICE\"}";

    assertError(put(path, body), 401, "UNAUTHENTICATED");
    assertError(put(path, body, "X-Service-Token", INVENTORY_TOKEN), 403, "CALLER_NOT_ALLOWED");
    assertThat(statusOf(id)).isEqualTo("ACTIVE");
  }
}
