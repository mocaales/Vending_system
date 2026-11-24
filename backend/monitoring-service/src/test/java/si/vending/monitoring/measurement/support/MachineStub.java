package si.vending.monitoring.measurement.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Stand-in for the Machine service: {@code GET /v1/machines/{id}} (backend/machine-service/
 * openapi.yaml). Monitoring only needs to know whether a machine exists, so the stub answers 200
 * with a Machine body for registered machines, 404 {@code MACHINE_NOT_FOUND} for all others and 500
 * for machines put in {@link Mode#SERVER_ERROR}.
 *
 * <p>Assumed configuration contract: property {@code machine.base-url} holds the Machine base URL
 * (docker-compose sets it from {@code MACHINE_URL}); Monitoring authenticates with its own service
 * token in {@code X-Service-Token} and forwards {@code X-Trace-Id}.
 */
public final class MachineStub {

  public enum Mode {
    NORMAL,
    SERVER_ERROR
  }

  public record Call(String method, String path, String serviceToken, String traceId) {}

  private final HttpServer server;
  private final Set<String> machines = ConcurrentHashMap.newKeySet();
  private final Map<String, Mode> modes = new ConcurrentHashMap<>();
  private volatile String errorBody;
  private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

  private MachineStub(HttpServer server) {
    this.server = server;
  }

  public static MachineStub start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      MachineStub stub = new MachineStub(server);
      server.createContext("/v1/machines/", stub::handle);
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

  public void register(String machineId) {
    machines.add(machineId);
  }

  public void setMode(String machineId, Mode mode) {
    modes.put(machineId, mode);
  }

  /** Replaces the body of the 500 answer (to prove that a client never logs a dependency body). */
  public void setErrorBody(String body) {
    this.errorBody = body;
  }

  public List<Call> calls() {
    return List.copyOf(calls);
  }

  private void handle(HttpExchange exchange) throws IOException {
    try {
      String path = exchange.getRequestURI().getPath();
      String id = path.substring("/v1/machines/".length());
      calls.add(
          new Call(
              exchange.getRequestMethod(),
              path,
              exchange.getRequestHeaders().getFirst("X-Service-Token"),
              exchange.getRequestHeaders().getFirst("X-Trace-Id")));
      if (modes.getOrDefault(id, Mode.NORMAL) == Mode.SERVER_ERROR) {
        reply(
            exchange,
            500,
            errorBody != null
                ? errorBody
                : "{\"code\":\"INTERNAL_ERROR\",\"message\":\"boom\",\"traceId\":\"stub\"}");
      } else if (machines.contains(id)) {
        reply(
            exchange,
            200,
            """
            {"id":"%s","code":"M-%s","name":"Stub machine","address":"Trg republike 1, Maribor",\
            "latitude":46.5547,"longitude":15.6459,"status":"ACTIVE",\
            "createdAt":"2026-11-01T08:00:00Z","updatedAt":"2026-11-01T08:00:00Z"}\
            """
                .formatted(id, id.substring(0, 8)));
      } else {
        reply(
            exchange,
            404,
            "{\"code\":\"MACHINE_NOT_FOUND\",\"message\":\"No such machine.\",\"traceId\":\"stub\"}");
      }
    } finally {
      exchange.close();
    }
  }

  private static void reply(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }
}
