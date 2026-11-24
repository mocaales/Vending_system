package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.sales.sales.support.DependencyStub.Mode;
import si.vending.sales.sales.support.SalesApiTest;

/** #26: Product or Inventory not answering gives 503 and nothing is stored. */
class SaleIntakeDependencyTest extends SalesApiTest {

  private void assert503ThenRetrySucceedsAsFirstAcceptance(Map<String, Object> body) {
    assertError(postSale(body), 503, "DEPENDENCY_UNAVAILABLE");
    PRODUCT.reset();
    INVENTORY.reset();
    // nothing was stored by the failed attempt: the retry with the same saleId is a 201
    assertThat(postSale(body).status()).isEqualTo(201);
  }

  @Test
  void productServerErrorIs503AndNothingIsStored() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    PRODUCT.setMode(Mode.SERVER_ERROR);

    assert503ThenRetrySucceedsAsFirstAcceptance(body);
  }

  @Test
  void productUnavailableIs503AndNothingIsStored() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    PRODUCT.setMode(Mode.UNAVAILABLE);

    assert503ThenRetrySucceedsAsFirstAcceptance(body);
  }

  @Test
  void inventoryServerErrorIs503AndNothingIsStored() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    INVENTORY.setMode(Mode.SERVER_ERROR);

    assert503ThenRetrySucceedsAsFirstAcceptance(body);
  }

  @Test
  void inventoryUnavailableIs503AndNothingIsStored() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    INVENTORY.setMode(Mode.UNAVAILABLE);

    assert503ThenRetrySucceedsAsFirstAcceptance(body);
  }

  @Test
  void slowProductTimesOutAs503NotAHang() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    PRODUCT.setMode(Mode.SLOW);
    long start = System.nanoTime();

    assertError(postSale(body), 503, "DEPENDENCY_UNAVAILABLE");

    assertThat((System.nanoTime() - start) / 1_000_000_000L)
        .as("a client timeout shorter than the stub's 12 s answer")
        .isLessThan(11);
  }

  @Test
  void slowInventoryTimesOutAs503NotAHang() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    INVENTORY.setMode(Mode.SLOW);
    long start = System.nanoTime();

    assertError(postSale(body), 503, "DEPENDENCY_UNAVAILABLE");

    assertThat((System.nanoTime() - start) / 1_000_000_000L).isLessThan(11);
  }

  @Test
  void theTraceIdIsPropagatedToBothDependencies() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    String trace = "trace-" + java.util.UUID.randomUUID();

    var response =
        post(
            "/v1/sales",
            json(body),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Machine-Id",
            setup.machineId(),
            "X-Trace-Id",
            trace);

    assertThat(response.status()).isEqualTo(201);
    assertThat(PRODUCT.callsTo(setup.productId())).allMatch(c -> trace.equals(c.traceId()));
    assertThat(INVENTORY.callsTo(setup.machineId())).allMatch(c -> trace.equals(c.traceId()));
    assertThat(PRODUCT.callsTo(setup.productId())).isNotEmpty();
    assertThat(INVENTORY.callsTo(setup.machineId())).isNotEmpty();
  }
}
