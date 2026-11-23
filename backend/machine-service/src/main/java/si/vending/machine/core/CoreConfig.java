package si.vending.machine.core;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(ServiceTokens.class)
public class CoreConfig implements WebMvcConfigurer {

  private final ServiceTokens tokens;

  public CoreConfig(ServiceTokens tokens) {
    Map<String, String> owner = new HashMap<>();
    new TreeMap<>(tokens.serviceTokens())
        .forEach((caller, token) -> {
          if (token == null || token.isBlank()) {
            return;
          }
          String other = owner.putIfAbsent(token, caller);
          if (other != null) {
            throw new IllegalStateException(
                "Callers '"
                    + other
                    + "' and '"
                    + caller
                    + "' share one service token; use distinct tokens.");
          }
        });
    this.tokens = tokens;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new ServiceTokenInterceptor(tokens));
  }
}
