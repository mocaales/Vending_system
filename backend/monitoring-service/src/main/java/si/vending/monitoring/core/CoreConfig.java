package si.vending.monitoring.core;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(ServiceTokens.class)
public class CoreConfig implements WebMvcConfigurer {

  private final ServiceTokens tokens;

  public CoreConfig(ServiceTokens tokens) {
    this.tokens = tokens;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new ServiceTokenInterceptor(tokens));
  }
}
