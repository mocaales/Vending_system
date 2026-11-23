package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.InventoryStub.Mode;
import si.vending.machine.machine.support.MachineApiTest;
import si.vending.machine.machine.support.MachineFixtures;

/**
 * #17 criterion 4: DELETE /v1/machines/{id} is a logical retirement guarded by Inventory (mocked by
 * {@code InventoryStub}, shape documented there).
 *
 * <p>Key revocation on retirement is observable only through the keys endpoints and the internal
 * verify endpoint (issue #18) and is pinned there.
 */
class MachineRetireTest extends MachineApiTest {

  @Test
  void machineWithoutStockRecordsIsRetired() {
    String id = newMachine(2);

    Http.Response response = retire(id);

    assertThat(response.status()).isEqualTo(204);
    assertThat(response.body()).isEmpty();
    assertThat(statusOf(id)).isEqualTo("RETIRED");
  }

  @Test
  void callsInventoryOnceForTheMachineWithTraceIdPropagated() {
    String id = newMachine(1);

    Http.Response response =
        delete(
            "/v1/machines/" + id,
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Operator-Id",
            MachineFixtures.OPERATOR_ID,
            "X-Trace-Id",
            "trace-17-abc");

    assertThat(response.status()).isEqualTo(204);
    assertThat(INVENTORY.calls(id)).hasSize(1);
    assertThat(INVENTORY.calls(id).get(0).method()).isEqualTo("GET");
    assertThat(INVENTORY.calls(id).get(0).traceId()).isEqualTo("trace-17-abc");
  }

  @Test
  void zeroQuantitiesDoNotBlockRetirement() {
    String id = newMachine(2);
    INVENTORY.setQuantity(id, "A1", 0);
    INVENTORY.setQuantity(id, "A2", 0);

    assertThat(retire(id).status()).isEqualTo(204);
    assertThat(statusOf(id)).isEqualTo("RETIRED");
  }

  @Test
  void anyQuantityAboveZeroBlocksRetirement() {
    String id = newMachine(3);
    INVENTORY.setQuantity(id, "A1", 0);
    INVENTORY.setQuantity(id, "A2", 1);
    INVENTORY.setQuantity(id, "A3", 0);

    assertError(retire(id), 409, "MACHINE_HAS_STOCK");
    assertThat(statusOf(id)).isEqualTo("ACTIVE");
  }

  @Test
  void stockBeyondTheFirstInventoryPageIsNotMissed() {
    String id = newMachine(100);
    for (int i = 1; i <= 99; i++) {
      INVENTORY.setQuantity(id, "A" + i, 0);
    }
    INVENTORY.setQuantity(id, "A100", 4); // Inventory default page is 50 items

    assertError(retire(id), 409, "MACHINE_HAS_STOCK");
    assertThat(statusOf(id)).isEqualTo("ACTIVE");
  }

  @Test
  void machineInServiceCanBeRetired() {
    String id = newMachine(1);
    setStatus(id, "SERVICE");

    assertThat(retire(id).status()).isEqualTo(204);
    assertThat(statusOf(id)).isEqualTo("RETIRED");
  }

  @Test
  void emptyingTheStockAllowsRetirementLater() {
    String id = newMachine(1);
    INVENTORY.setQuantity(id, "A1", 5);
    assertError(retire(id), 409, "MACHINE_HAS_STOCK");

    INVENTORY.setQuantity(id, "A1", 0);

    assertThat(retire(id).status()).isEqualTo(204);
  }

  @Test
  void inventoryErrorMeansServiceUnavailableAndMachineStaysUnchanged() {
    String id = newMachine(1);
    INVENTORY.setMode(id, Mode.SERVER_ERROR);

    assertError(retire(id), 503, "DEPENDENCY_UNAVAILABLE");
    assertThat(statusOf(id)).isEqualTo("ACTIVE");
  }

  @Test
  void inventoryTimeoutMeansServiceUnavailableAndMachineStaysUnchanged() {
    String id = newMachine(1);
    INVENTORY.setMode(id, Mode.SLOW); // answers (with stock-free list) only after 12 s

    long started = System.nanoTime();
    Http.Response response = retire(id);
    long seconds = (System.nanoTime() - started) / 1_000_000_000L;

    assertError(response, 503, "DEPENDENCY_UNAVAILABLE");
    assertThat(seconds).as("timeout on the Inventory call").isLessThan(11);
    assertThat(statusOf(id)).isEqualTo("ACTIVE");
  }

  @Test
  void repeatingTheDeleteReturns204WithoutAskingInventoryAgain() {
    String id = newMachine(1);
    assertThat(retire(id).status()).isEqualTo(204);
    // Inventory would now refuse or fail; the repeat must not even ask
    INVENTORY.setQuantity(id, "A1", 9);
    INVENTORY.setMode(id, Mode.SERVER_ERROR);

    Http.Response again = retire(id);

    assertThat(again.status()).isEqualTo(204);
    assertThat(INVENTORY.calls(id)).hasSize(1);
    assertThat(statusOf(id)).isEqualTo("RETIRED");
  }

  @Test
  void retiredMachineCannotBeChangedAnyMore() {
    String id = newMachine(1);
    retire(id);

    assertError(
        put(
            "/v1/machines/" + id,
            "{\"name\":\"n\",\"address\":\"a\",\"latitude\":1,\"longitude\":1}",
            operator()),
        409,
        "MACHINE_RETIRED");
    assertError(setStatus(id, "ACTIVE"), 409, "MACHINE_RETIRED");
  }

  @Test
  void retiredMachineIsHiddenFromDefaultListButShownWithStatusFilter() {
    String id = newMachine(1);
    String code = (String) getMachine(id).json().get("code");
    retire(id);

    assertThat(get("/v1/machines?q=" + code, operator()).json().get("total")).isEqualTo(0);
    Http.Response retired = get("/v1/machines?status=RETIRED&q=" + code, operator());
    assertThat(retired.json().get("total")).isEqualTo(1);
  }

  @Test
  void codeOfARetiredMachineStaysTaken() {
    String id = newMachine(1);
    String code = (String) getMachine(id).json().get("code");
    retire(id);

    Http.Response again =
        createMachine(
            "{\"code\":\"%s\",\"name\":\"n\",\"address\":\"a\",\"latitude\":1,\"longitude\":1,\"slots\":[{\"slotCode\":\"A1\"}]}"
                .formatted(code));

    assertError(again, 409, "MACHINE_CODE_TAKEN");
  }

  @Test
  void unknownMachineIsNotFoundAndInventoryIsNotAsked() {
    String id = UUID.randomUUID().toString();

    assertError(retire(id), 404, "MACHINE_NOT_FOUND");
    assertThat(INVENTORY.calls(id)).isEmpty();
  }

  @Test
  void malformedIdIsBadRequest() {
    assertError(delete("/v1/machines/xyz", operator()), 400, "BAD_REQUEST");
  }

  @Test
  void onlyOperatorsMayRetire() {
    String id = newMachine(1);

    assertError(delete("/v1/machines/" + id), 401, "UNAUTHENTICATED");
    assertError(
        delete("/v1/machines/" + id, "X-Service-Token", INVENTORY_TOKEN),
        403,
        "CALLER_NOT_ALLOWED");
    assertThat(statusOf(id)).isEqualTo("ACTIVE");
    assertThat(INVENTORY.calls(id)).isEmpty();
  }
}
