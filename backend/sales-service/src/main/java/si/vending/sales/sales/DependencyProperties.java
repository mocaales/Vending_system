package si.vending.sales.sales;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the services read during intake are, and the token this service presents to them.
 *
 * <p>The prefixes are {@code product-client} and {@code inventory-client}, not {@code product} and
 * {@code inventory}: relaxed binding would map the environment variables {@code
 * PRODUCT_SERVICE_TOKEN} and {@code INVENTORY_SERVICE_TOKEN} (the tokens those services expect from
 * callers) onto {@code product.service-token}, and Sales would present another service's token.
 */
public final class DependencyProperties {

  private DependencyProperties() {}

  /** {@code product-client.base-url} from {@code PRODUCT_URL}. */
  @ConfigurationProperties("product-client")
  public record Product(String baseUrl, String serviceToken) {}

  /** {@code inventory-client.base-url} from {@code INVENTORY_URL}. */
  @ConfigurationProperties("inventory-client")
  public record Inventory(String baseUrl, String serviceToken) {}
}
