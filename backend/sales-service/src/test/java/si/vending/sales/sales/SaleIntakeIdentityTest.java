package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import si.vending.sales.core.AllowedCallers;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.SalesApiTest;

/**
 * Caller identity on POST /v1/sales follows the core rule used by Monitoring: the gateway token
 * needs a valid X-Machine-Id (missing or blank: 403 CALLER_NOT_ALLOWED, malformed: 401), and an
 * ambiguous identity (both headers, or one header twice) is 401 UNAUTHENTICATED. A rejected request
 * stores nothing and calls no dependency.
 */
class SaleIntakeIdentityTest extends SalesApiTest {

  private static final String OPERATOR_ID = "2f0e3c1a-7b9d-4c55-8a1e-6d4b9f0c3e21";

  @Autowired RequestMappingHandlerMapping mapping;

  private void assertRejectedWithoutSideEffects(
      Setup setup, Map<String, Object> body, int status, String code, String... headers) {
    Http.Response response = post("/v1/sales", json(body), headers);

    assertError(response, status, code);
    assertThat(PRODUCT.callsTo(setup.productId())).as("Product not called").isEmpty();
    assertThat(INVENTORY.callsTo(setup.machineId())).as("Inventory not called").isEmpty();
    assertThat(postSale(body).status()).as("nothing stored: first acceptance").isEqualTo(201);
  }

  @Test
  void gatewayTokenWithoutMachineIdIs403() {
    Setup setup = knownSlot();
    assertRejectedWithoutSideEffects(
        setup, sale(setup), 403, "CALLER_NOT_ALLOWED", "X-Service-Token", GATEWAY_TOKEN);
  }

  @Test
  void blankMachineIdIs403() {
    Setup setup = knownSlot();
    assertRejectedWithoutSideEffects(
        setup,
        sale(setup),
        403,
        "CALLER_NOT_ALLOWED",
        "X-Service-Token",
        GATEWAY_TOKEN,
        "X-Machine-Id",
        " ");
  }

  @Test
  void malformedMachineIdIs401() {
    Setup setup = knownSlot();
    assertRejectedWithoutSideEffects(
        setup,
        sale(setup),
        401,
        "UNAUTHENTICATED",
        "X-Service-Token",
        GATEWAY_TOKEN,
        "X-Machine-Id",
        "not-a-uuid");
  }

  @Test
  void operatorIdInsteadOfMachineIdIs403() {
    Setup setup = knownSlot();
    assertRejectedWithoutSideEffects(
        setup,
        sale(setup),
        403,
        "CALLER_NOT_ALLOWED",
        "X-Service-Token",
        GATEWAY_TOKEN,
        "X-Operator-Id",
        OPERATOR_ID);
  }

  @Test
  void bothOperatorAndMachineIdIs401() {
    Setup setup = knownSlot();
    assertRejectedWithoutSideEffects(
        setup,
        sale(setup),
        401,
        "UNAUTHENTICATED",
        "X-Service-Token",
        GATEWAY_TOKEN,
        "X-Machine-Id",
        setup.machineId(),
        "X-Operator-Id",
        OPERATOR_ID);
  }

  @Test
  void machineIdSentTwiceIs401() {
    Setup setup = knownSlot();
    assertRejectedWithoutSideEffects(
        setup,
        sale(setup),
        401,
        "UNAUTHENTICATED",
        "X-Service-Token",
        GATEWAY_TOKEN,
        "X-Machine-Id",
        setup.machineId(),
        "X-Machine-Id",
        UUID.randomUUID().toString());
  }

  @Test
  void sameMachineIdSentTwiceIsStill401() {
    Setup setup = knownSlot();
    assertRejectedWithoutSideEffects(
        setup,
        sale(setup),
        401,
        "UNAUTHENTICATED",
        "X-Service-Token",
        GATEWAY_TOKEN,
        "X-Machine-Id",
        setup.machineId(),
        "X-Machine-Id",
        setup.machineId());
  }

  @Test
  void operatorIdSentTwiceIs401() {
    Setup setup = knownSlot();
    assertRejectedWithoutSideEffects(
        setup,
        sale(setup),
        401,
        "UNAUTHENTICATED",
        "X-Service-Token",
        GATEWAY_TOKEN,
        "X-Machine-Id",
        setup.machineId(),
        "X-Operator-Id",
        OPERATOR_ID,
        "X-Operator-Id",
        UUID.randomUUID().toString());
  }

  /** Sales adopts the core interceptor's identity flags, like Monitoring's machine routes. */
  @Test
  void recordHandlerDeclaresMachineIdentityInAllowedCallers() throws Exception {
    HandlerMethod handler =
        mapping.getHandlerMethods().entrySet().stream()
            .filter(e -> e.getValue().getBeanType().equals(SaleController.class))
            .filter(e ->
                e.getKey().getPathPatternsCondition() != null
                    && e.getKey()
                        .getPathPatternsCondition()
                        .getPatternValues()
                        .contains("/v1/sales")
                    && e.getKey().getMethodsCondition().getMethods().stream()
                        .anyMatch(m -> m.name().equals("POST")))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new AssertionError("POST /v1/sales handler not mapped"));

    AllowedCallers annotation = handler.getMethod().getAnnotation(AllowedCallers.class);

    assertThat(annotation).as("@AllowedCallers on the handler method").isNotNull();
    // reflection: the attribute does not exist yet while this test is red
    Object machine;
    try {
      machine = annotation.annotationType().getMethod("machine").invoke(annotation);
    } catch (NoSuchMethodException e) {
      throw new AssertionError("@AllowedCallers has no machine() attribute yet", e);
    }
    assertThat(machine).as("@AllowedCallers(machine = true)").isEqualTo(true);
  }
}
