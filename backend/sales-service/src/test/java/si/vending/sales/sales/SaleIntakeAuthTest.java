package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.SalesApiTest;

/** #26: POST /v1/sales is for the gateway acting for a machine (x-callers: MACHINE). */
class SaleIntakeAuthTest extends SalesApiTest {

  @Test
  void withoutServiceTokenIs401() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);

    Http.Response response = post("/v1/sales", json(body), "X-Machine-Id", setup.machineId());

    assertError(response, 401, "UNAUTHENTICATED");
  }

  @Test
  void unknownServiceTokenIs401() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);

    Http.Response response =
        post("/v1/sales", json(body), "X-Service-Token", "nope", "X-Machine-Id", setup.machineId());

    assertError(response, 401, "UNAUTHENTICATED");
  }

  @Test
  void anotherServiceIsNotAllowedToRecordASale() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);

    Http.Response response =
        post(
            "/v1/sales",
            json(body),
            "X-Service-Token",
            INVENTORY_TOKEN,
            "X-Machine-Id",
            setup.machineId());

    assertError(response, 403, "CALLER_NOT_ALLOWED");
    assertThat(PRODUCT.callsTo(setup.productId())).isEmpty();
  }

  @Test
  void rejectedCallerStoresNothing() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    post(
        "/v1/sales",
        json(body),
        "X-Service-Token",
        INVENTORY_TOKEN,
        "X-Machine-Id",
        setup.machineId());

    assertThat(postSale(body).status()).isEqualTo(201);
  }
}
