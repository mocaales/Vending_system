package si.vending.sales.core.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Minimal HTTP client for black-box tests against the running service. */
public final class Http {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private Http() {}

  public record Response(int status, java.net.http.HttpHeaders headers, String body) {

    public String header(String name) {
      return headers.firstValue(name).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> json() {
      return JSON.readValue(body, Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<Object> items() {
      return (List<Object>) json().get("items");
    }
  }

  /** Sends a request; {@code headers} is a flat list of name, value pairs. */
  public static Response send(
      int port, String method, String path, String body, String... headers) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(20));
    for (int i = 0; i < headers.length; i += 2) {
      request.header(headers[i], headers[i + 1]);
    }
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    try {
      HttpResponse<String> response =
          CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
      return new Response(response.statusCode(), response.headers(), response.body());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  public static Response get(int port, String path, String... headers) {
    return send(port, "GET", path, null, headers);
  }

  public static Response post(int port, String path, String json, String... headers) {
    String[] all = new String[headers.length + 2];
    all[0] = "Content-Type";
    all[1] = "application/json";
    System.arraycopy(headers, 0, all, 2, headers.length);
    return send(port, "POST", path, json, all);
  }
}
