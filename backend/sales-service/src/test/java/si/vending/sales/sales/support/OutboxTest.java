package si.vending.sales.sales.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.vending.sales.core.support.Http;

/**
 * Base for the outbox tests (issue #32). The worker is never started by a timer in tests ({@code
 * outbox.scheduler-enabled=false}); a test runs exactly one pass with {@link #runOnce()}.
 *
 * <p>PINNED NAMES (the implementer must use exactly these):
 *
 * <ul>
 *   <li>Table {@code sale_outbox}: columns {@code sale_id} (uuid, one row per sale), {@code
 *       attempts} (integer, number of failed or deferred deliveries so far, 0 for a new entry),
 *       {@code next_attempt_at} (timestamptz, the entry is due when this is not after now), {@code
 *       done_at} (timestamptz, null while the entry is open, set when it was delivered or given
 *       up).
 *   <li>Bean {@code outboxWorker} (class {@code si.vending.sales.sales.OutboxWorker}, public) with
 *       public no-argument method {@code runOnce()}: one pass over the due, open entries (at most
 *       {@code outbox.batch-size}), safe to run from several threads at once. Called by reflection
 *       so that the whole suite still compiles before the class exists.
 *   <li>Properties (prefix {@code outbox}, never {@code sales}): {@code outbox.scheduler-enabled}
 *       (default true; false: no timer), {@code outbox.initial-backoff} (Duration, delay after the
 *       first failure), {@code outbox.max-backoff} (Duration, cap, the delay doubles per attempt),
 *       {@code outbox.batch-size} (int). The token sent to Inventory is the existing {@code
 *       inventory-client.service-token} (env {@code SALES_SERVICE_TOKEN}).
 * </ul>
 *
 * <p>Isolation: the database is shared by the JVM, so every test starts with an empty outbox.
 */
public abstract class OutboxTest extends SalesReadTest {

  /** Sales' own token towards Inventory ({@code inventory-client.service-token}). */
  protected static final String SALES_TOKEN = "sales-secret-token";

  @DynamicPropertySource
  static void outboxProperties(DynamicPropertyRegistry registry) {
    registry.add("outbox.initial-backoff", () -> "PT2S");
    registry.add("outbox.max-backoff", () -> "PT5S");
    registry.add("inventory-client.service-token", () -> SALES_TOKEN);
  }

  @Autowired private ApplicationContext context;

  @BeforeEach
  void emptyOutbox() {
    // to_regclass keeps the individual tests failing on their own assertion while the table is new
    if (jdbc.queryForObject("SELECT to_regclass('sale_outbox')", String.class) != null) {
      jdbc.update("DELETE FROM sale_outbox");
    }
  }

  /** One pass of the worker. */
  protected void runOnce() {
    Object worker = context.getBean("outboxWorker");
    try {
      worker.getClass().getMethod("runOnce").invoke(worker);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException(e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Accepts a fresh sale through the API (201) and returns its saleId. */
  protected String accept(Setup setup) {
    return accept(sale(setup));
  }

  protected String accept(Map<String, Object> body) {
    Http.Response response = postSale(body);
    assertThat(response.status()).as(response.body()).isEqualTo(201);
    return (String) body.get("saleId");
  }

  /** The inventory {state, reason} of the sale as the operator sees it. */
  @SuppressWarnings("unchecked")
  protected Map<String, Object> inventoryOf(String saleId) {
    Http.Response response = asOperator("/v1/sales/" + saleId);
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    return (Map<String, Object>) response.json().get("inventory");
  }

  protected String stateOf(String saleId) {
    return (String) inventoryOf(saleId).get("state");
  }

  protected int outboxCount(String saleId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sale_outbox WHERE sale_id = ?::uuid", Integer.class, saleId);
  }

  protected Map<String, Object> outboxRow(String saleId) {
    return jdbc.queryForMap("SELECT * FROM sale_outbox WHERE sale_id = ?::uuid", saleId);
  }

  protected boolean isDone(String saleId) {
    return outboxRow(saleId).get("done_at") != null;
  }

  protected int attempts(String saleId) {
    return ((Number) outboxRow(saleId).get("attempts")).intValue();
  }

  protected Instant nextAttemptAt(String saleId) {
    return ((java.sql.Timestamp) outboxRow(saleId).get("next_attempt_at")).toInstant();
  }

  /** Makes the entry due now, as if its backoff had elapsed (no sleeping in tests). */
  protected void makeDue(String saleId) {
    jdbc.update(
        "UPDATE sale_outbox SET next_attempt_at = ? WHERE sale_id = ?::uuid",
        java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.SECONDS)),
        saleId);
  }
}
