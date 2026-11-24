package si.vending.sales.sales;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Inventory {@code POST /v1/inventory/{machineId}/{slot}/decrease} (docs/03 s. 7). */
@Component
public class InventoryDecreaseClient {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  /** Reasons the {@code sales.inventory_reason} column accepts (V2 check constraint). */
  static final Set<String> KNOWN_REASONS = Set.of("PRODUCT_MISMATCH", "INSUFFICIENT_STOCK");

  /** What the worker does with the entry after one delivery attempt. */
  public sealed interface Outcome {
    /** Inventory answered with a result: APPLIED or NEEDS_RECONCILIATION (+ reason). */
    record Answered(String state, String reason) implements Outcome {}

    /** A permanent refusal (404, 409, 422): the operator has to reconcile. */
    record Rejected() implements Outcome {}

    /**
     * Inventory is not reachable (transport error, timeout, 5xx, rejected token): try again later
     * and stop the pass, the other entries would fail the same way. {@code detail} is the status or
     * the exception class, never a body.
     */
    record Retry(String detail) implements Outcome {}

    /**
     * Inventory answered, but the answer cannot be read (not JSON, unknown result): try this entry
     * again later; the pass goes on.
     */
    record Unreadable(String detail) implements Outcome {}
  }

  private record DecreaseIn(UUID saleId, UUID productId, int quantity) {}

  private record DecreaseOut(String result, String reason) {}

  private final RestClient client;
  private final String serviceToken;

  public InventoryDecreaseClient(DependencyProperties.Inventory properties) {
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

  public Outcome decrease(OutboxRepository.Claimed entry, String traceId) {
    try {
      DecreaseOut out =
          client
              .post()
              .uri("/v1/inventory/{machineId}/{slot}/decrease", entry.machineId(), entry.slotCode())
              .headers(h -> addHeaders(h, traceId))
              .body(new DecreaseIn(entry.saleId(), entry.productId(), entry.quantity()))
              .retrieve()
              .body(DecreaseOut.class);
      if (out != null && "APPLIED".equals(out.result())) {
        return new Outcome.Answered("APPLIED", null);
      }
      if (out != null && "NEEDS_RECONCILIATION".equals(out.result())) {
        // an unknown or too long reason is dropped: the sale table cannot hold it
        String reason =
            out.reason() != null && KNOWN_REASONS.contains(out.reason()) ? out.reason() : null;
        return new Outcome.Answered("NEEDS_RECONCILIATION", reason);
      }
      return new Outcome.Unreadable("unknown result");
    } catch (RestClientResponseException e) {
      int status = e.getStatusCode().value();
      return status == 404 || status == 409 || status == 422
          ? new Outcome.Rejected()
          : new Outcome.Retry("HTTP " + status);
    } catch (ResourceAccessException e) {
      return new Outcome.Retry(e.getClass().getSimpleName());
    } catch (RestClientException e) {
      return new Outcome.Unreadable(e.getClass().getSimpleName());
    }
  }

  private void addHeaders(HttpHeaders headers, String traceId) {
    headers.set("X-Trace-Id", traceId);
    if (serviceToken != null && !serviceToken.isBlank()) {
      headers.set("X-Service-Token", serviceToken);
    }
  }
}
