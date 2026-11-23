package si.vending.machine.machine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import si.vending.machine.core.DependencyUnavailableException;
import si.vending.machine.core.TraceIdFilter;

/** Reads stock from the Inventory service ({@code GET /v1/inventory/{machineId}}, docs/03 s. 7). */
@Component
public class InventoryClient {

  private static final Logger LOG = LoggerFactory.getLogger(InventoryClient.class);
  private static final int PAGE_SIZE = 200;
  private static final int MAX_PAGES = 5;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  @JsonIgnoreProperties(ignoreUnknown = true)
  record StockPage(List<StockItem> items, long total) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record StockItem(String slot, Integer quantity) {}

  private final RestClient client;
  private final String serviceToken;

  public InventoryClient(InventoryProperties properties) {
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(READ_TIMEOUT);
    this.client =
        RestClient.builder()
            .baseUrl(String.valueOf(properties.baseUrl()))
            .requestFactory(factory)
            .build();
    this.serviceToken = properties.serviceToken();
  }

  /**
   * Whether any slot of the machine holds more than 0 items. Pages through the whole list; stops at
   * the first slot with stock.
   *
   * @throws DependencyUnavailableException if Inventory does not answer correctly in time
   */
  public boolean hasStock(UUID machineId) {
    try {
      int offset = 0;
      for (int pageNo = 0; pageNo < MAX_PAGES; pageNo++) {
        StockPage page = fetch(machineId, offset);
        if (page == null || page.items() == null) {
          throw new DependencyUnavailableException("Inventory returned an unreadable answer.");
        }
        for (StockItem item : page.items()) {
          if (item.quantity() != null && item.quantity() > 0) {
            return true;
          }
        }
        offset += page.items().size();
        if (page.items().isEmpty() || offset >= page.total()) {
          return false;
        }
      }
      throw new DependencyUnavailableException("Inventory returned too many pages.");
    } catch (HttpClientErrorException.NotFound e) {
      return false; // Inventory knows no stock for this machine
    } catch (RestClientException e) {
      LOG.warn("Inventory call failed: {}", e.getMessage());
      throw new DependencyUnavailableException("Inventory is not reachable.");
    }
  }

  private StockPage fetch(UUID machineId, int offset) {
    return client
        .get()
        .uri("/v1/inventory/{id}?limit={limit}&offset={offset}", machineId, PAGE_SIZE, offset)
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
        .body(StockPage.class);
  }
}
