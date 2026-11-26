package si.vending.monitoring.measurement;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import si.vending.monitoring.core.DependencyUnavailableException;
import si.vending.monitoring.core.ServiceTokens;
import si.vending.monitoring.core.TraceIdFilter;
import tools.jackson.databind.JsonNode;

/** Asks the Machine service whether a machine exists ({@code GET /v1/machines/{id}}, docs/03). */
@Component
public class MachineClient {

  private static final Logger LOG = LoggerFactory.getLogger(MachineClient.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
  private static final int PAGE_SIZE = 200;
  private static final int MAX_PAGES = 50;

  private final RestClient client;
  private final String serviceToken;

  public MachineClient(@Value("${machine.base-url}") String baseUrl, ServiceTokens tokens) {
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(READ_TIMEOUT);
    this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    this.serviceToken = tokens.serviceTokens().get("monitoring");
  }

  /**
   * Whether Machine knows the machine.
   *
   * @throws DependencyUnavailableException if Machine does not answer correctly in time
   */
  public boolean exists(UUID machineId) {
    try {
      client
          .get()
          .uri("/v1/machines/{id}", machineId)
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
          .toBodilessEntity();
      return true;
    } catch (HttpClientErrorException.NotFound e) {
      return false;
    } catch (RestClientResponseException e) {
      // status code only: the body and the message (which embeds the body) are never logged
      LOG.warn(
          "Machine call failed: status={} traceId={}",
          e.getStatusCode().value(),
          MDC.get(TraceIdFilter.MDC_KEY));
      throw new DependencyUnavailableException("Machine service is not reachable.");
    } catch (RestClientException e) {
      LOG.warn(
          "Machine call failed: error={} traceId={}",
          e.getMostSpecificCause().getClass().getSimpleName(),
          MDC.get(TraceIdFilter.MDC_KEY));
      throw new DependencyUnavailableException("Machine service is not reachable.");
    }
  }

  /**
   * Ids of all ACTIVE machines ({@code GET /v1/machines?status=ACTIVE}, every page).
   *
   * @throws DependencyUnavailableException if Machine does not answer correctly in time
   */
  public List<UUID> listActiveIds() {
    List<UUID> ids = new ArrayList<>();
    int offset = 0;
    for (int pages = 0; pages < MAX_PAGES; pages++) {
      final int pageOffset = offset;
      JsonNode page;
      try {
        page =
            client
                .get()
                .uri("/v1/machines?status=ACTIVE&limit={l}&offset={o}", PAGE_SIZE, pageOffset)
                .headers(this::addHeaders)
                .retrieve()
                .body(JsonNode.class);
      } catch (RestClientResponseException e) {
        LOG.warn(
            "Machine call failed: status={} traceId={}",
            e.getStatusCode().value(),
            MDC.get(TraceIdFilter.MDC_KEY));
        throw new DependencyUnavailableException("Machine service is not reachable.");
      } catch (RestClientException e) {
        LOG.warn(
            "Machine call failed: error={} traceId={}",
            e.getMostSpecificCause().getClass().getSimpleName(),
            MDC.get(TraceIdFilter.MDC_KEY));
        throw new DependencyUnavailableException("Machine service is not reachable.");
      }
      JsonNode items = page == null ? null : page.get("items");
      if (items == null || !items.isArray()) {
        throw new DependencyUnavailableException("Machine service returned an unexpected list.");
      }
      for (JsonNode item : items) {
        JsonNode id = item.get("id");
        if (id == null || !id.isString()) {
          throw new DependencyUnavailableException("Machine service returned an unexpected list.");
        }
        try {
          ids.add(UUID.fromString(id.asString()));
        } catch (IllegalArgumentException e) {
          throw new DependencyUnavailableException("Machine service returned an unexpected list.");
        }
      }
      JsonNode total = page.get("total");
      boolean lastByTotal =
          total != null && total.isIntegralNumber() && offset + PAGE_SIZE >= total.longValue();
      if (items.size() < PAGE_SIZE || lastByTotal) {
        return ids;
      }
      offset += PAGE_SIZE;
    }
    throw new DependencyUnavailableException("Machine service returned too many machines.");
  }

  private void addHeaders(org.springframework.http.HttpHeaders headers) {
    String traceId = MDC.get(TraceIdFilter.MDC_KEY);
    if (traceId != null) {
      headers.set(TraceIdFilter.HEADER, traceId);
    }
    if (serviceToken != null && !serviceToken.isBlank()) {
      headers.set("X-Service-Token", serviceToken);
    }
  }
}
