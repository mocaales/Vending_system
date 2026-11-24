package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.ReconciliationTest;

/**
 * #35: PUT /v1/sales/{saleId}/reconciliation is for the operator only (x-callers [OPERATOR]).
 * Frontend-api (bare gateway token), product, inventory and machine identity are 403
 * CALLER_NOT_ALLOWED; no or an unknown token and an ambiguous identity are 401. A refused call
 * changes nothing.
 */
class ReconciliationAuthTest extends ReconciliationTest {

  @Test
  void operatorIsAllowed() {
    Fx sale = open();
    assertThat(resolve(sale, adjusted(), operator()).status()).isEqualTo(200);
  }

  @Test
  void otherCallersAre403AndTheSaleStaysOpen() {
    Fx sale = open();
    Map<String, String[]> callers =
        Map.of(
            "frontend-api", frontendApi(),
            "product", product(),
            "inventory", inventory(),
            "machine", machine());
    callers.forEach((name, headers) -> {
      Http.Response response = resolve(sale, adjusted(), headers);
      assertError(response, 403, "CALLER_NOT_ALLOWED");
    });
    assertThat(stateOf(sale.saleId().toString())).isEqualTo("NEEDS_RECONCILIATION");
  }

  @Test
  void noTokenAndUnknownTokenAre401() {
    Fx sale = open();
    assertError(resolve(sale, adjusted()), 401, "UNAUTHENTICATED");
    assertError(
        resolve(sale, adjusted(), "X-Service-Token", "wrong", "X-Operator-Id", OPERATOR_ID),
        401,
        "UNAUTHENTICATED");
    assertThat(stateOf(sale.saleId().toString())).isEqualTo("NEEDS_RECONCILIATION");
  }

  @Test
  void ambiguousOrMalformedIdentityIs401() {
    Fx sale = open();
    assertError(
        resolve(
            sale,
            adjusted(),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Operator-Id",
            OPERATOR_ID,
            "X-Machine-Id",
            java.util.UUID.randomUUID().toString()),
        401,
        "UNAUTHENTICATED");
    assertError(
        resolve(sale, adjusted(), "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", "not-a-uuid"),
        401,
        "UNAUTHENTICATED");
    assertThat(stateOf(sale.saleId().toString())).isEqualTo("NEEDS_RECONCILIATION");
  }

  @Test
  void authIsCheckedBeforeTheSaleLookup() {
    Http.Response response =
        put(path(java.util.UUID.randomUUID()), json(adjusted()), frontendApi());
    assertError(response, 403, "CALLER_NOT_ALLOWED");
  }
}
