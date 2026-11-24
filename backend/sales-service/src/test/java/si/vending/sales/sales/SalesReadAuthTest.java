package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.SalesReadTest;

/**
 * Callers of the read routes (x-callers in openapi.yaml): GET /v1/sales is for the operator
 * (gateway token + X-Operator-Id), frontend-api (gateway token, no identity) and product (its own
 * token); /v1/sales/{saleId} and both statistics routes are for the operator and frontend-api only.
 * Any other caller, and a machine identity, is 403 CALLER_NOT_ALLOWED; no or an unknown token is
 * 401 UNAUTHENTICATED; an ambiguous identity is 401. Reads never call Product or Inventory.
 */
class SalesReadAuthTest extends SalesReadTest {

  private UUID machine;
  private Fx sale;

  @BeforeEach
  void fixture() {
    machine = UUID.randomUUID();
    sale = insert(machine, UUID.randomUUID(), now().minusSeconds(60));
  }

  private List<String> operatorOrFrontendRoutes() {
    return List.of("/v1/sales/" + sale.saleId(), "/v1/sales/stats", "/v1/sales/stats/" + machine);
  }

  private List<String> allRoutes() {
    List<String> all = new java.util.ArrayList<>(operatorOrFrontendRoutes());
    all.add("/v1/sales?machineId=" + machine);
    return all;
  }

  @Test
  void operatorCanReadEveryRoute() {
    for (String path : allRoutes()) {
      assertThat(get(path, operator()).status()).as(path).isEqualTo(200);
    }
  }

  @Test
  void frontendApiCanReadEveryRoute() {
    for (String path : allRoutes()) {
      assertThat(get(path, frontendApi()).status()).as(path).isEqualTo(200);
    }
  }

  @Test
  void productCanOnlyListSales() {
    assertThat(get("/v1/sales?machineId=" + machine, product()).status()).isEqualTo(200);
    for (String path : operatorOrFrontendRoutes()) {
      assertError(get(path, product()), 403, "CALLER_NOT_ALLOWED");
    }
  }

  @Test
  void otherServicesAreForbiddenEverywhere() {
    for (String path : allRoutes()) {
      assertError(get(path, inventory()), 403, "CALLER_NOT_ALLOWED");
    }
  }

  @Test
  void machineIdentityIsForbiddenEverywhere() {
    for (String path : allRoutes()) {
      assertError(get(path, machine()), 403, "CALLER_NOT_ALLOWED");
    }
  }

  @Test
  void noTokenIs401() {
    for (String path : allRoutes()) {
      assertError(get(path), 401, "UNAUTHENTICATED");
      assertError(get(path, "X-Operator-Id", OPERATOR_ID), 401, "UNAUTHENTICATED");
    }
  }

  @Test
  void unknownTokenIs401() {
    for (String path : allRoutes()) {
      assertError(
          get(path, "X-Service-Token", "nope", "X-Operator-Id", OPERATOR_ID),
          401,
          "UNAUTHENTICATED");
    }
  }

  @Test
  void operatorAndMachineIdTogetherIs401() {
    for (String path : allRoutes()) {
      Http.Response response =
          get(
              path,
              "X-Service-Token",
              GATEWAY_TOKEN,
              "X-Operator-Id",
              OPERATOR_ID,
              "X-Machine-Id",
              UUID.randomUUID().toString());
      assertError(response, 401, "UNAUTHENTICATED");
    }
  }

  @Test
  void malformedOperatorIdIs401() {
    for (String path : allRoutes()) {
      assertError(
          get(path, "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", "not-a-uuid"),
          401,
          "UNAUTHENTICATED");
    }
  }

  @Test
  void aRejectedCallerSeesNoSaleData() {
    Http.Response response = get("/v1/sales?machineId=" + machine, inventory());

    assertError(response, 403, "CALLER_NOT_ALLOWED");
    assertThat(response.body()).doesNotContain(sale.saleId().toString());
  }

  @Test
  void readsNeverCallProductOrInventory() {
    for (String path : allRoutes()) {
      assertThat(get(path, operator()).status()).as(path).isEqualTo(200);
      assertThat(get(path, frontendApi()).status()).as(path).isEqualTo(200);
    }
    assertThat(get("/v1/sales?machineId=" + machine, product()).status()).isEqualTo(200);

    assertThat(PRODUCT.calls()).isEmpty();
    assertThat(INVENTORY.calls()).isEmpty();
  }
}
