package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.InventoryStub.Mode;
import si.vending.machine.machine.support.MachineApiTest;

/**
 * Retirement edge cases of the Inventory call (#17, security review findings I5 and the 404
 * decision).
 *
 * <p>404 from Inventory means "Inventory never saw this machine", i.e. no stock records, so the
 * machine may be retired (204). Inventory 5xx and timeouts stay 503 (see MachineRetireTest).
 *
 * <p>I5: a client that trusts Inventory's {@code total} can be made to page forever. The cap is 5
 * pages: at the page size of 200 that is 1 000 slots, ten times the 100-slot limit of a machine, so
 * a legitimate machine never reaches it. Past the cap the answer is 503 DEPENDENCY_UNAVAILABLE.
 */
class MachineRetireInventoryEdgeTest extends MachineApiTest {

  @Test
  void inventoryNotFoundMeansNoStockAndMachineIsRetired() {
    String id = newMachine(2);
    INVENTORY.setMode(id, Mode.NOT_FOUND);

    Http.Response response = retire(id);

    assertThat(response.status()).as(response.body()).isEqualTo(204);
    assertThat(statusOf(id)).isEqualTo("RETIRED");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS) // backstop only; the assertions fail first
  void endlessInventoryPagesEndInServiceUnavailableWithinFewPages() {
    String id = newMachine(1);
    INVENTORY.setMode(id, Mode.ENDLESS_ZERO_PAGES);

    long started = System.nanoTime();
    Http.Response response = retire(id);
    long seconds = (System.nanoTime() - started) / 1_000_000_000L;

    assertError(response, 503, "DEPENDENCY_UNAVAILABLE");
    assertThat(seconds).as("time spent paging Inventory").isLessThan(5);
    assertThat(INVENTORY.calls(id))
        .as("pages requested from Inventory")
        .hasSizeLessThanOrEqualTo(5);
    assertThat(statusOf(id)).isEqualTo("ACTIVE");
  }
}
