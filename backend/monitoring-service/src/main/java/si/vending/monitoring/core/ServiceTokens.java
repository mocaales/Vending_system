package si.vending.monitoring.core;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tokens of the calling services, keyed by caller name ({@code core.service-tokens.<caller>}). */
@ConfigurationProperties("core")
public record ServiceTokens(Map<String, String> serviceTokens) {

  public ServiceTokens {
    serviceTokens = serviceTokens == null ? Map.of() : serviceTokens;
  }
}
