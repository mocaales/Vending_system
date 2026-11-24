package si.vending.sales.sales;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the services read during intake are, and the token this service presents to them. */
public final class DependencyProperties {

  private DependencyProperties() {}

  /** {@code product.base-url} from {@code PRODUCT_URL}. */
  @ConfigurationProperties("product")
  public record Product(String baseUrl, String serviceToken) {}

  /** {@code inventory.base-url} from {@code INVENTORY_URL}. */
  @ConfigurationProperties("inventory")
  public record Inventory(String baseUrl, String serviceToken) {}
}
