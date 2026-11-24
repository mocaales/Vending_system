package si.vending.sales.sales.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * HTTP stand-in for the two services Sales reads during intake.
 *
 * <p>SHARED CONTRACT (real shapes, taken from the committed OpenAPI files):
 *
 * <ul>
 *   <li>Product {@code GET /v1/products/{id}}: 200 {@code ProductOut} (the fields used here are
 *       {@code id, name, referencePrice, currency, status}); unknown id: 404 {@code
 *       PRODUCT_NOT_FOUND}. An archived product is still returned (status {@code ARCHIVED}).
 *   <li>Inventory {@code GET /v1/inventory/{machineId}/{slot}}: 200 {@code SlotStock}; an emptied
 *       slot is returned with {@code productId: null, quantity: 0}; a slot never set: 404 {@code
 *       SLOT_NOT_FOUND}.
 * </ul>
 */
public final class DependencyStub {

  public enum Mode {
    NORMAL,
    SERVER_ERROR,
    /** Answers correctly, but only after 12 s (longer than any sensible client timeout). */
    SLOW,
    /** Answers 503 DEPENDENCY_UNAVAILABLE. */
    UNAVAILABLE
  }

  public record Call(String method, String path, String traceId, String serviceToken) {}

  private final HttpServer server;
  private final String name;
  private final Map<String, String> bodies = new ConcurrentHashMap<>();
  private volatile Mode mode = Mode.NORMAL;
  private volatile String errorBody;
  private final List<Call> calls = new CopyOnWriteArrayList<>();

  private DependencyStub(HttpServer server, String name) {
    this.server = server;
    this.name = name;
  }

  /** Starts a stub serving everything below {@code prefix} (for example {@code /v1/products/}). */
  public static DependencyStub start(String name, String prefix) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      DependencyStub stub = new DependencyStub(server, name);
      server.createContext(prefix, stub::handle);
      server.setExecutor(Executors.newCachedThreadPool());
      server.start();
      return stub;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public void reset() {
    mode = Mode.NORMAL;
    errorBody = null;
    calls.clear();
  }

  /** Replaces the body of the 500 answer (to prove that a client never logs a dependency body). */
  public void setErrorBody(String body) {
    this.errorBody = body;
  }

  /** Registers the 200 body for an exact request path. Unregistered paths answer 404. */
  public void register(String path, String json) {
    bodies.put(path, json);
  }

  public List<Call> calls() {
    return Collections.unmodifiableList(calls);
  }

  public List<Call> callsTo(String pathPart) {
    return calls.stream().filter(c -> c.path().contains(pathPart)).toList();
  }

  private void handle(HttpExchange exchange) throws IOException {
    try {
      String path = exchange.getRequestURI().getPath();
      calls.add(
          new Call(
              exchange.getRequestMethod(),
              path,
              exchange.getRequestHeaders().getFirst("X-Trace-Id"),
              exchange.getRequestHeaders().getFirst("X-Service-Token")));
      switch (mode) {
        case SERVER_ERROR ->
            reply(
                exchange,
                500,
                errorBody != null
                    ? errorBody
                    : "{\"code\":\"INTERNAL_ERROR\",\"message\":\"boom\",\"traceId\":\"stub\"}");
        case UNAVAILABLE ->
            reply(
                exchange,
                503,
                "{\"code\":\"DEPENDENCY_UNAVAILABLE\",\"message\":\"down\",\"traceId\":\"stub\"}");
        case SLOW -> {
          Thread.sleep(12_000);
          answer(exchange, path);
        }
        default -> answer(exchange, path);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException | RuntimeException e) {
      // the client may already have given up (timeout); nothing to answer
    } finally {
      exchange.close();
    }
  }

  private void answer(HttpExchange exchange, String path) throws IOException {
    String body = bodies.get(path);
    if (body == null) {
      String code = name.equals("product") ? "PRODUCT_NOT_FOUND" : "SLOT_NOT_FOUND";
      reply(
          exchange,
          404,
          "{\"code\":\"" + code + "\",\"message\":\"not found\",\"traceId\":\"stub\"}");
    } else {
      reply(exchange, 200, body);
    }
  }

  private static void reply(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }
}
