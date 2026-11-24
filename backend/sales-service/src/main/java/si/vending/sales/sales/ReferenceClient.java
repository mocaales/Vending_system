package si.vending.sales.sales;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import si.vending.sales.core.DependencyUnavailableException;
import si.vending.sales.core.TraceIdFilter;

/**
 * One GET against another service to learn whether a referenced resource exists. Timeouts are
 * short; any failure other than the documented "not found" answer is a dependency failure.
 */
class ReferenceClient {

  private static final Logger LOG = LoggerFactory.getLogger(ReferenceClient.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  private final RestClient client;
  private final String serviceToken;
  private final String name;
  private final String notFoundCode;

  ReferenceClient(String name, String baseUrl, String serviceToken, String notFoundCode) {
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(READ_TIMEOUT);
    this.client =
        RestClient.builder().baseUrl(String.valueOf(baseUrl)).requestFactory(factory).build();
    this.serviceToken = serviceToken;
    this.name = name;
    this.notFoundCode = notFoundCode;
  }

  /**
   * @return true if the resource exists, false if the service answered 404 with its not-found code
   * @throws DependencyUnavailableException on timeout, connection failure, 5xx or any other answer
   */
  boolean exists(String path, Object... uriVariables) {
    try {
      client.get().uri(path, uriVariables).headers(this::addHeaders).retrieve().toBodilessEntity();
      return true;
    } catch (HttpClientErrorException.NotFound e) {
      if (e.getResponseBodyAsString().contains(notFoundCode)) {
        return false;
      }
      LOG.warn("{} answered 404 without {}", name, notFoundCode);
      throw new DependencyUnavailableException(name + " is not reachable.");
    } catch (RestClientException e) {
      if (e instanceof RestClientResponseException r) {
        LOG.warn("{} call failed: HTTP {}", name, r.getStatusCode().value());
      } else {
        LOG.warn("{} call failed: {}", name, e.getClass().getSimpleName());
      }
      throw new DependencyUnavailableException(name + " is not reachable.");
    }
  }

  private void addHeaders(HttpHeaders headers) {
    String traceId = MDC.get(TraceIdFilter.MDC_KEY);
    if (traceId != null) {
      headers.set(TraceIdFilter.HEADER, traceId);
    }
    if (serviceToken != null && !serviceToken.isBlank()) {
      headers.set("X-Service-Token", serviceToken);
    }
  }

  static Consumer<HttpHeaders> none() {
    return h -> {};
  }
}
