package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.SalesReadTest;

/**
 * Hardening found in the review of #26, pinned for the read routes and for POST /v1/sales:
 * comma-joined identity headers and a duplicated X-Service-Token are 401, and time values outside
 * 2020-01-01..2100-01-01 (also given with a UTC offset) are 422, never a 500 and never stored.
 */
class SalesReadHardeningTest extends SalesReadTest {

  private static final String LOWER = "2019-12-31T23:59:59Z";
  private static final String UPPER = "2100-01-01T00:00:01Z";

  /** 2019-12-31T23:30:00Z written with an offset; "+" is percent-encoded in a query. */
  private static final String OFFSET_BEFORE_BOUND = "2020-01-01T00:30:00%2B01:00";

  private final UUID machine = UUID.randomUUID();

  private List<String> readPaths() {
    return List.of(
        "/v1/sales?machineId=" + machine,
        "/v1/sales/" + UUID.randomUUID(),
        "/v1/sales/stats",
        "/v1/sales/stats/" + machine);
  }

  // --- comma-joined and duplicated headers

  @Test
  void commaJoinedOperatorIdIs401OnEveryReadRoute() {
    String joined = OPERATOR_ID + "," + UUID.randomUUID();
    for (String path : readPaths()) {
      assertError(
          get(path, "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", joined),
          401,
          "UNAUTHENTICATED");
    }
  }

  @Test
  void commaJoinedMachineIdIs401OnPostSales() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);

    Http.Response response =
        post(
            "/v1/sales",
            json(body),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Machine-Id",
            setup.machineId() + "," + UUID.randomUUID());

    assertError(response, 401, "UNAUTHENTICATED");
    assertThat(PRODUCT.calls()).isEmpty();
    assertThat(INVENTORY.calls()).isEmpty();
    assertThat(postSale(body).status()).as("nothing stored").isEqualTo(201);
  }

  @Test
  void operatorIdSentTwiceIs401() {
    for (String path : readPaths()) {
      assertError(
          get(
              path,
              "X-Service-Token",
              GATEWAY_TOKEN,
              "X-Operator-Id",
              OPERATOR_ID,
              "X-Operator-Id",
              UUID.randomUUID().toString()),
          401,
          "UNAUTHENTICATED");
    }
  }

  @Test
  void serviceTokenSentTwiceIs401OnEveryReadRoute() {
    for (String path : readPaths()) {
      assertError(
          get(
              path,
              "X-Service-Token",
              GATEWAY_TOKEN,
              "X-Service-Token",
              GATEWAY_TOKEN,
              "X-Operator-Id",
              OPERATOR_ID),
          401,
          "UNAUTHENTICATED");
    }
  }

  @Test
  void serviceTokenSentTwiceIs401OnPostSales() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);

    Http.Response response =
        post(
            "/v1/sales",
            json(body),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Machine-Id",
            setup.machineId());

    assertError(response, 401, "UNAUTHENTICATED");
    assertThat(INVENTORY.calls()).isEmpty();
  }

  // --- time bounds

  @Test
  void listFromOrToOutsideTheBoundsIs422() {
    for (String bad :
        new String[] {
          "from=" + LOWER,
          "to=" + LOWER,
          "from=" + UPPER,
          "to=" + UPPER,
          "from=0001-01-01T00:00:00Z",
          "to=9999-12-31T23:59:59Z",
          "from=" + OFFSET_BEFORE_BOUND
        }) {
      Http.Response response = asOperator("/v1/sales?machineId=" + machine + "&" + bad);

      assertThat(response.status()).as(bad).isEqualTo(422);
      assertError(response, 422, "VALIDATION_ERROR");
    }
  }

  @Test
  void statsFromOrToOutsideTheBoundsIs422() {
    for (String base : new String[] {"/v1/sales/stats", "/v1/sales/stats/" + machine}) {
      for (String bad :
          new String[] {
            "from=" + LOWER,
            "to=" + UPPER,
            "from=" + UPPER,
            "to=" + LOWER,
            "to=" + OFFSET_BEFORE_BOUND
          }) {
        Http.Response response = asOperator(base + "?" + bad);

        assertThat(response.status()).as(base + "?" + bad).isEqualTo(422);
        assertError(response, 422, "VALIDATION_ERROR");
      }
    }
  }

  @Test
  void theBoundsThemselvesAreAccepted() {
    assertThat(asOperator("/v1/sales?machineId=" + machine + "&from=2020-01-01T00:00:00Z").status())
        .isEqualTo(200);
    assertThat(asOperator("/v1/sales?machineId=" + machine + "&to=2100-01-01T00:00:00Z").status())
        .isEqualTo(200);
    assertThat(asOperator("/v1/sales/stats/" + machine + "?from=2020-01-01T00:00:00Z").status())
        .isEqualTo(200);
  }

  @Test
  void postOccurredAtWithAnOffsetBeforeTheBoundIs422() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    String valid = (String) body.get("occurredAt");
    body.put("occurredAt", "2020-01-01T00:30:00+01:00");

    Http.Response response = postSale(body);

    assertThat(response.status()).isEqualTo(422);
    assertInvalidField(response, "occurredAt");
    body.put("occurredAt", valid);
    assertThat(postSale(body).status()).as("nothing stored").isEqualTo(201);
  }
}
