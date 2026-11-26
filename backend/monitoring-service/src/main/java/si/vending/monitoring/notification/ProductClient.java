package si.vending.monitoring.notification;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import si.vending.monitoring.core.ServiceTokens;
import si.vending.monitoring.core.TraceIdFilter;
import tools.jackson.databind.JsonNode;

/**
 * Product names for the LOW_STOCK e-mail ({@code GET /v1/products/{id}}, archived products
 * included). Best effort: the mail is never held back because Product does not answer.
 */
@Component
public class ProductClient {

  private static final Logger LOG = LoggerFactory.getLogger(ProductClient.class);

  private final RestClient client;
  private final String serviceToken;

  // The prefix is "product-client", not "product": PRODUCT_SERVICE_TOKEN in the environment would
  // otherwise override the token this service sends (see ConfigurationPrefixGuardTest).
  public ProductClient(@Value("${product-client.base-url}") String baseUrl, ServiceTokens tokens) {
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(3));
    this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    this.serviceToken = tokens.serviceTokens().get("monitoring");
  }

  /** The product name, or empty if Product does not answer or does not know the product. */
  public Optional<String> name(UUID productId) {
    try {
      JsonNode body =
          client
              .get()
              .uri("/v1/products/{id}", productId)
              .headers(headers -> {
                String traceId = MDC.get(TraceIdFilter.MDC_KEY);
                if (traceId != null) {
                  headers.set(TraceIdFilter.HEADER, traceId);
                }
                if (serviceToken != null && !serviceToken.isBlank()) {
                  headers.set("X-Service-Token", serviceToken);
                }
              })
              .retrieve()
              .body(JsonNode.class);
      JsonNode name = body == null ? null : body.get("name");
      return name != null && name.isString() && !name.asString().isBlank()
          ? Optional.of(name.asString())
          : Optional.empty();
    } catch (RestClientException e) {
      LOG.warn(
          "Product name lookup failed: error={} traceId={}",
          e.getMostSpecificCause().getClass().getSimpleName(),
          MDC.get(TraceIdFilter.MDC_KEY));
      return Optional.empty();
    }
  }
}
