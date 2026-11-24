package si.vending.sales.sales;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  DependencyProperties.Product.class,
  DependencyProperties.Inventory.class
})
public class SalesConfig {}
