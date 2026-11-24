package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import si.vending.sales.sales.support.DependencyStub.Mode;
import si.vending.sales.sales.support.SalesApiTest;

/** A failing dependency is logged without the body it answered with (it may hold anything). */
@ExtendWith(OutputCaptureExtension.class)
class DependencyLogTest extends SalesApiTest {

  private static final String MARKER = "SECRET-BODY-MARKER";
  private static final String BODY = "{\"code\":\"INTERNAL_ERROR\",\"message\":\"" + MARKER + "\"}";

  @Test
  void productFailureLogDoesNotContainTheResponseBody(CapturedOutput output) {
    Setup setup = knownSlot();
    PRODUCT.setErrorBody(BODY);
    PRODUCT.setMode(Mode.SERVER_ERROR);

    assertError(postSale(sale(setup)), 503, "DEPENDENCY_UNAVAILABLE");

    assertThat(output.getAll()).contains("Product").doesNotContain(MARKER);
  }

  @Test
  void inventoryFailureLogDoesNotContainTheResponseBody(CapturedOutput output) {
    Setup setup = knownSlot();
    INVENTORY.setErrorBody(BODY);
    INVENTORY.setMode(Mode.SERVER_ERROR);

    assertError(postSale(sale(setup)), 503, "DEPENDENCY_UNAVAILABLE");

    assertThat(output.getAll()).contains("Inventory").doesNotContain(MARKER);
  }
}
