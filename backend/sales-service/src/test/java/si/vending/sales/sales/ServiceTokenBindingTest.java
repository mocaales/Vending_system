package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.DependencyStub;
import si.vending.sales.sales.support.SalesApiTest;

/**
 * Regression for the token hijack found in docker compose: the Sales container also carries {@code
 * PRODUCT_SERVICE_TOKEN} and {@code INVENTORY_SERVICE_TOKEN} (they are the tokens those services
 * expect from THEIR callers). Spring relaxed binding maps such an env var onto {@code
 * product.service-token}, which beats {@code application.yml}, so Sales presented Product's own
 * token and got 403.
 *
 * <p>The test puts an OS-environment-like property source in front of everything and requires that
 * the calls to Product and Inventory carry the token of Sales ({@code SALES_SERVICE_TOKEN}), never
 * the foreign ones. Fix: the client properties must not live under a prefix that equals another
 * service's name; use {@code product-client.*} and {@code inventory-client.*}.
 */
@ContextConfiguration(initializers = ServiceTokenBindingTest.FakeOsEnvironment.class)
class ServiceTokenBindingTest extends SalesApiTest {

  static final String SALES_OWN = "sales-own-token";
  static final String PRODUCT_FOREIGN = "product-own-token";
  static final String INVENTORY_FOREIGN = "inventory-own-token";

  /** Behaves like the process environment: same name mapping, highest precedence. */
  static class FakeOsEnvironment
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new SystemEnvironmentPropertySource(
                  "testEnv",
                  Map.of(
                      "SALES_SERVICE_TOKEN", SALES_OWN,
                      "PRODUCT_SERVICE_TOKEN", PRODUCT_FOREIGN,
                      "INVENTORY_SERVICE_TOKEN", INVENTORY_FOREIGN)));
    }
  }

  /**
   * Legacy keys so that the stubs are reachable before the prefix rename; once the fix lands they
   * are unused and harmless. Reaching the stubs is what lets the test see the token that was sent.
   */
  @DynamicPropertySource
  static void legacyKeys(DynamicPropertyRegistry registry) {
    registry.add("product.base-url", PRODUCT::baseUrl);
    registry.add("inventory.base-url", INVENTORY::baseUrl);
  }

  @Test
  void productAndInventoryCallsCarryTheSalesTokenNotTheirOwn() {
    Setup setup = knownSlot();

    Http.Response response = postSale(sale(setup));

    assertThat(response.status()).isEqualTo(201);
    assertThat(PRODUCT.calls()).isNotEmpty();
    assertThat(INVENTORY.calls()).isNotEmpty();
    assertThat(PRODUCT.calls())
        .extracting(DependencyStub.Call::serviceToken)
        .containsOnly(SALES_OWN);
    assertThat(INVENTORY.calls())
        .extracting(DependencyStub.Call::serviceToken)
        .containsOnly(SALES_OWN);
  }
}
