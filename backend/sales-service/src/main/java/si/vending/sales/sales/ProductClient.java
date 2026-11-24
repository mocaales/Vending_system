package si.vending.sales.sales;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Product {@code GET /v1/products/{id}} (docs/03 s. 6). */
@Component
public class ProductClient {

  private final ReferenceClient client;

  public ProductClient(DependencyProperties.Product properties) {
    this.client =
        new ReferenceClient(
            "Product", properties.baseUrl(), properties.serviceToken(), "PRODUCT_NOT_FOUND");
  }

  public boolean exists(UUID productId) {
    return client.exists("/v1/products/{id}", productId);
  }
}
