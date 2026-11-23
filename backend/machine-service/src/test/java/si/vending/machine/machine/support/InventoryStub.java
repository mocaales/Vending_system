package si.vending.machine.machine.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Stand-in for the Inventory service ({@code GET /v1/inventory/{machineId}}, docs/03 section 7).
 *
 * <p>SHARED CONTRACT for the response shape (docs/03 does not spell it out; the Inventory contract
 * #13 must match this): a standard page {@code {items, total, limit, offset}} where each item is a
 * slot {@code {machineId, slot, productId (or null), quantity}}. Empty slots have quantity 0.
 * {@code limit} defaults to 50 (max 200) and {@code offset} to 0, as everywhere in the system, so a
 * client that does not page through or ask for {@code limit=200} sees only part of the stock.
 */
public final class InventoryStub {

  public enum Mode {
    /** Answers with the configured stock. */
    NORMAL,
    /** Answers 500 with an error body. */
    SERVER_ERROR,
    /** Answers correctly, but only after 12 s (longer than any sensible client timeout). */
    SLOW,
    /** Answers 404: Inventory never saw this machine (no stock records at all). */
    NOT_FOUND,
    /**
     * Answers every page with non-empty, zero-quantity slots and a huge {@code total}, so a client
     * that trusts {@code total} pages forever.
     */
    ENDLESS_ZERO_PAGES
  }

  public record Call(String method, String query, String traceId) {}

  private final HttpServer server;
  private final Map<String, Map<String, Integer>> stock = new ConcurrentHashMap<>();
  private final Map<String, Mode> modes = new ConcurrentHashMap<>();
  private final Map<String, List<Call>> calls = new ConcurrentHashMap<>();

  private InventoryStub(HttpServer server) {
    this.server = server;
  }

  public static InventoryStub start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      InventoryStub stub = new InventoryStub(server);
      server.createContext("/v1/inventory/", stub::handle);
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

  /** Sets the quantity of one slot of a machine (insertion order is slot order). */
  public void setQuantity(String machineId, String slot, int quantity) {
    stock
        .computeIfAbsent(machineId, k -> Collections.synchronizedMap(new LinkedHashMap<>()))
        .put(slot, quantity);
  }

  public void setMode(String machineId, Mode mode) {
    modes.put(machineId, mode);
  }

  public List<Call> calls(String machineId) {
    return List.copyOf(calls.getOrDefault(machineId, List.of()));
  }

  private void handle(HttpExchange exchange) throws IOException {
    try {
      String path = exchange.getRequestURI().getPath();
      String machineId = path.substring("/v1/inventory/".length());
      String query = exchange.getRequestURI().getRawQuery();
      calls
          .computeIfAbsent(machineId, k -> Collections.synchronizedList(new ArrayList<>()))
          .add(
              new Call(
                  exchange.getRequestMethod(),
                  query == null ? "" : query,
                  exchange.getRequestHeaders().getFirst("X-Trace-Id")));
      Mode mode = modes.getOrDefault(machineId, Mode.NORMAL);
      if (mode == Mode.SERVER_ERROR) {
        reply(
            exchange,
            500,
            "{\"code\":\"INTERNAL_ERROR\",\"message\":\"boom\",\"traceId\":\"stub\"}");
        return;
      }
      if (mode == Mode.NOT_FOUND) {
        reply(
            exchange,
            404,
            "{\"code\":\"NOT_FOUND\",\"message\":\"No stock for this machine.\",\"traceId\":\"stub\"}");
        return;
      }
      if (mode == Mode.SLOW) {
        Thread.sleep(12_000);
      }
      if (mode == Mode.ENDLESS_ZERO_PAGES) {
        reply(exchange, 200, endlessZeroPage(machineId, query));
        return;
      }
      reply(exchange, 200, page(machineId, query));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException | RuntimeException e) {
      // the client may already have given up (timeout); nothing to answer
    } finally {
      exchange.close();
    }
  }

  private String page(String machineId, String query) {
    int limit = 50;
    int offset = 0;
    if (query != null) {
      for (String pair : query.split("&")) {
        if (pair.startsWith("limit=")) {
          limit = Math.min(200, Integer.parseInt(pair.substring(6)));
        } else if (pair.startsWith("offset=")) {
          offset = Integer.parseInt(pair.substring(7));
        }
      }
    }
    List<Map.Entry<String, Integer>> all =
        new ArrayList<>(stock.getOrDefault(machineId, Map.of()).entrySet());
    StringBuilder items = new StringBuilder();
    for (int i = offset; i < Math.min(all.size(), offset + limit); i++) {
      Map.Entry<String, Integer> slot = all.get(i);
      if (items.length() > 0) {
        items.append(',');
      }
      String productId = slot.getValue() > 0 ? "\"c3a8f7d4-12ab-4e55-b6c9-5d0e8a1f2b73\"" : "null";
      items
          .append("{\"machineId\":\"")
          .append(machineId)
          .append("\",\"slot\":\"")
          .append(slot.getKey())
          .append("\",\"productId\":")
          .append(productId)
          .append(",\"quantity\":")
          .append(slot.getValue())
          .append('}');
    }
    return "{\"items\":[%s],\"total\":%d,\"limit\":%d,\"offset\":%d}"
        .formatted(items, all.size(), limit, offset);
  }

  /** A page of zero-quantity slots whatever the offset; {@code total} claims 1 000 000 slots. */
  private static String endlessZeroPage(String machineId, String query) {
    int limit = 50;
    int offset = 0;
    if (query != null) {
      for (String pair : query.split("&")) {
        if (pair.startsWith("limit=")) {
          limit = Math.min(200, Integer.parseInt(pair.substring(6)));
        } else if (pair.startsWith("offset=")) {
          offset = Integer.parseInt(pair.substring(7));
        }
      }
    }
    StringBuilder items = new StringBuilder();
    for (int i = 0; i < limit; i++) {
      if (items.length() > 0) {
        items.append(',');
      }
      items
          .append("{\"machineId\":\"")
          .append(machineId)
          .append("\",\"slot\":\"Z")
          .append(offset + i)
          .append("\",\"productId\":null,\"quantity\":0}");
    }
    return "{\"items\":[%s],\"total\":1000000,\"limit\":%d,\"offset\":%d}"
        .formatted(items, limit, offset);
  }

  private static void reply(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }
}
