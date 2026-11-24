package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.vending.machine.core.support.AbstractApiTest;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineFixtures;

/** #17 criterion 4: Inventory not reachable at all (connection refused) gives 503. */
class MachineRetireInventoryUnreachableTest extends AbstractApiTest {

  @DynamicPropertySource
  static void unreachableInventory(DynamicPropertyRegistry registry) {
    registry.add("inventory-client.base-url", () -> "http://localhost:" + closedPort());
  }

  private static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void retireReturns503AndMachineStaysActive() {
    String id = MachineFixtures.createMachine(port, "T" + MachineFixtures.tag(), 1);

    Http.Response response =
        MachineFixtures.delete(port, "/v1/machines/" + id, MachineFixtures.operator());

    assertError(response, 503, "DEPENDENCY_UNAVAILABLE");
    assertThat(MachineFixtures.getMachine(port, id).json().get("status")).isEqualTo("ACTIVE");
  }
}
