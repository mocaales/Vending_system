package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.ClosingTest;

/**
 * #55 callers (openapi.yaml x-callers): POST, PUT, DELETE by id and DELETE by date are OPERATOR
 * only; the two GETs are OPERATOR and frontend-api. Product, inventory and machine identities are
 * 403 CALLER_NOT_ALLOWED; a missing, unknown or ambiguous identity is 401. A refused call changes
 * nothing, and authentication is checked before validation and lookup.
 */
class ClosingAuthTest extends ClosingTest {

  private static Map<String, String[]> notAllowedForWrites() {
    Map<String, String[]> callers = new LinkedHashMap<>();
    callers.put("frontend-api", frontendApi());
    callers.put("product", product());
    callers.put("inventory", inventory());
    callers.put("machine", machine());
    return callers;
  }

  private static Map<String, String[]> notAllowedForReads() {
    Map<String, String[]> callers = new LinkedHashMap<>();
    callers.put("product", product());
    callers.put("inventory", inventory());
    callers.put("machine", machine());
    return callers;
  }

  private static Map<String, String[]> unauthenticated() {
    Map<String, String[]> callers = new LinkedHashMap<>();
    callers.put("no token", new String[0]);
    callers.put(
        "unknown token", new String[] {"X-Service-Token", "wrong", "X-Operator-Id", OPERATOR_ID});
    callers.put(
        "operator and machine",
        new String[] {
          "X-Service-Token",
          GATEWAY_TOKEN,
          "X-Operator-Id",
          OPERATOR_ID,
          "X-Machine-Id",
          UUID.randomUUID().toString()
        });
    callers.put(
        "malformed operator id",
        new String[] {"X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", "not-a-uuid"});
    return callers;
  }

  private void assertRefused(
      Function<String[], Http.Response> request,
      int status,
      String code,
      Map<String, String[]> callers) {
    callers.forEach((name, headers) -> {
      Http.Response response = request.apply(headers);
      assertThat(response.status()).as(name + ": " + response.body()).isEqualTo(status);
      assertError(response, status, code);
    });
  }

  // --- operator only

  @Test
  void postIsForTheOperatorOnly() {
    UUID machine = UUID.randomUUID();
    Function<String[], Http.Response> post =
        headers -> createClosing(in(yesterday(), machine, null), headers);

    assertRefused(post, 403, "CALLER_NOT_ALLOWED", notAllowedForWrites());
    assertRefused(post, 401, "UNAUTHENTICATED", unauthenticated());
    assertThat(asOperator("/v1/sales/closings?machineId=" + machine).json().get("total"))
        .isEqualTo(0);
    assertThat(post.apply(operator()).status()).isEqualTo(201);
  }

  @Test
  void putIsForTheOperatorOnly() {
    Map<String, Object> created = created(UUID.randomUUID());
    Function<String[], Http.Response> put =
        headers -> putClosing(created.get("id"), update("CONFIRMED", "x"), headers);

    assertRefused(put, 403, "CALLER_NOT_ALLOWED", notAllowedForWrites());
    assertRefused(put, 401, "UNAUTHENTICATED", unauthenticated());
    assertThat(getClosing(created.get("id")).json()).isEqualTo(created);
  }

  @Test
  void deleteByIdIsForTheOperatorOnly() {
    Map<String, Object> created = created(UUID.randomUUID());
    Function<String[], Http.Response> delete = headers -> deleteClosing(created.get("id"), headers);

    assertRefused(delete, 403, "CALLER_NOT_ALLOWED", notAllowedForWrites());
    assertRefused(delete, 401, "UNAUTHENTICATED", unauthenticated());
    assertThat(getClosing(created.get("id")).json()).isEqualTo(created);
  }

  @Test
  void deleteByDateIsForTheOperatorOnly() {
    Map<String, Object> created = created(UUID.randomUUID());
    Function<String[], Http.Response> delete =
        headers -> deleteDate(yesterday().toString(), headers);

    assertRefused(delete, 403, "CALLER_NOT_ALLOWED", notAllowedForWrites());
    assertRefused(delete, 401, "UNAUTHENTICATED", unauthenticated());
    assertThat(getClosing(created.get("id")).json()).isEqualTo(created);
  }

  // --- reads: operator and frontend-api

  @Test
  void listAndGetAreForOperatorAndFrontendApi() {
    Map<String, Object> created = created(UUID.randomUUID());
    String one = "/v1/sales/closings/" + created.get("id");
    String list = "/v1/sales/closings?machineId=" + created.get("machineId");

    for (String[] caller : new String[][] {operator(), frontendApi()}) {
      assertThat(get(one, caller).status()).isEqualTo(200);
      assertThat(get(list, caller).status()).isEqualTo(200);
    }
    assertRefused(h -> get(one, h), 403, "CALLER_NOT_ALLOWED", notAllowedForReads());
    assertRefused(h -> get(list, h), 403, "CALLER_NOT_ALLOWED", notAllowedForReads());
    assertRefused(h -> get(one, h), 401, "UNAUTHENTICATED", unauthenticated());
    assertRefused(h -> get(list, h), 401, "UNAUTHENTICATED", unauthenticated());
  }

  // --- order of checks

  @Test
  void authIsCheckedBeforeLookupAndValidation() {
    assertError(
        putClosing(UUID.randomUUID(), update("CONFIRMED", null), frontendApi()),
        403,
        "CALLER_NOT_ALLOWED");
    assertError(deleteClosing(UUID.randomUUID(), frontendApi()), 403, "CALLER_NOT_ALLOWED");
    assertError(
        post("/v1/sales/closings", "{\"date\":\"nope\"}", frontendApi()),
        403,
        "CALLER_NOT_ALLOWED");
    assertError(deleteDate(null, frontendApi()), 403, "CALLER_NOT_ALLOWED");
    assertError(get("/v1/sales/closings?limit=abc", product()), 403, "CALLER_NOT_ALLOWED");
  }
}
