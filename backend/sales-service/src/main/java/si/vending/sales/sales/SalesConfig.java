package si.vending.sales.sales;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties({
  DependencyProperties.Product.class,
  DependencyProperties.Inventory.class,
  OutboxProperties.class
})
@EnableScheduling
public class SalesConfig {}
