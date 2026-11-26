package si.vending.monitoring.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.sun.net.httpserver.HttpServer;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.vending.monitoring.alert.support.AlertApiTest;

/**
 * #38: the e-mail outbox. A newly opened alarm (any type) writes one {@code notification} row in
 * the same transaction; the worker sends it through SMTP (GreenMail), retries after an SMTP outage
 * and never changes the alarm. The worker is called directly; the scheduler is off in tests.
 */
class NotificationOutboxTest extends AlertApiTest {

  private static final int SMTP_PORT = freePort();
  private static final GreenMail SMTP =
      new GreenMail(new ServerSetup(SMTP_PORT, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
  private static final HttpServer PRODUCT;
  private static final List<String> PRODUCT_TOKENS = new CopyOnWriteArrayList<>();
  private static final java.util.concurrent.atomic.AtomicInteger PRODUCT_CALLS =
      new java.util.concurrent.atomic.AtomicInteger();
  private static final String MILK = UUID.randomUUID().toString();

  static {
    SMTP.start();
    try {
      PRODUCT = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    PRODUCT.createContext(
        "/v1/products/",
        exchange -> {
          PRODUCT_CALLS.incrementAndGet();
          PRODUCT_TOKENS.add(exchange.getRequestHeaders().getFirst("X-Service-Token"));
          boolean known = exchange.getRequestURI().getPath().endsWith(MILK);
          byte[] body =
              (known ? "{\"id\":\"" + MILK + "\",\"name\":\"Mleko 1L\"}" : "{\"code\":\"X\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(known ? 200 : 500, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    PRODUCT.setExecutor(Executors.newCachedThreadPool());
    PRODUCT.start();
  }

  @DynamicPropertySource
  static void mail(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", () -> "127.0.0.1");
    registry.add("spring.mail.port", () -> SMTP_PORT);
    registry.add("notification.mail.from", () -> "alarmi@vending.test");
    registry.add("notification.mail.to", () -> " ops@vending.test , second@vending.test ,");
    registry.add("notification.max-attempts", () -> 3);
    registry.add(
        "product-client.base-url", () -> "http://localhost:" + PRODUCT.getAddress().getPort());
  }

  @Autowired NotificationWorker worker;
  @Autowired NotificationOutbox outbox;

  private String machine;

  @BeforeEach
  void setUp() {
    if (SMTP.getSmtp() == null || !SMTP.getSmtp().isRunning()) {
      SMTP.start();
    }
    SMTP.reset();
    PRODUCT_CALLS.set(0);
    // rows left by other tests share the database; only this test's alarm may be sent
    jdbc.update("DELETE FROM notification");
    machine = newMachine();
  }

  @AfterAll
  static void stop() {
    SMTP.stop();
    PRODUCT.stop(0);
  }

  private void openTemperatureAlarm() {
    Instant t = ago(java.time.Duration.ofMinutes(5));
    postMeasurement(machine, UUID.randomUUID().toString(), t, "9.5");
    postMeasurement(machine, UUID.randomUUID().toString(), t.plusSeconds(10), "9.8");
  }

  private Map<String, Object> notification() {
    return jdbc.queryForMap(
        "SELECT n.status, n.attempts, n.next_attempt_at, a.status AS alert_status,"
            + " a.notification_status, a.notification_attempts FROM notification n"
            + " JOIN alert a ON a.id = n.alert_id WHERE a.machine_id = ?::uuid",
        machine);
  }

  private long notificationRows() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM notification n JOIN alert a ON a.id = n.alert_id"
            + " WHERE a.machine_id = ?::uuid",
        Long.class,
        machine);
  }

  @Test
  void openedTemperatureAlarmIsMailedToEveryRecipientOnce() throws Exception {
    openTemperatureAlarm();
    assertThat(notification()).containsEntry("status", "PENDING").containsEntry("attempts", 0);

    worker.processDue();
    worker.processDue(); // nothing left to send

    MimeMessage[] received = SMTP.getReceivedMessages();
    assertThat(received).hasSize(2); // one delivery per recipient mailbox
    assertThat(received[0].getSubject()).contains("TEMPERATURE_HIGH").contains("Stub machine");
    assertThat(received[0].getContent().toString()).contains(machine);
    assertThat(notification())
        .containsEntry("status", "SENT")
        .containsEntry("attempts", 1)
        .containsEntry("alert_status", "ACTIVE")
        .containsEntry("notification_status", "SENT")
        .containsEntry("notification_attempts", 1);
  }

  @Test
  void lowStockMailNamesProductsAndFallsBackToTheIdWhenProductFails() throws Exception {
    String unknown = newProduct();
    openLowStock(machine, 1, slot("A1", MILK, 1, 3), slot("B2", unknown, 0, 5));
    // an update of the open alarm and a repeated report are not new alarms
    report(machine, 2, slot("A1", MILK, 0, 3), slot("B2", unknown, 0, 5));
    report(machine, 2, slot("A1", MILK, 0, 3), slot("B2", unknown, 0, 5));
    assertThat(notificationRows()).isEqualTo(1);

    worker.processDue();

    MimeMessage[] received = SMTP.getReceivedMessages();
    assertThat(received).isNotEmpty();
    String text = received[0].getContent().toString();
    assertThat(text).contains("Mleko 1L").contains("A1");
    assertThat(text).contains(unknown).contains("B2"); // Product failed: id, mail not held back
    assertThat(PRODUCT_TOKENS).contains(MONITORING_TOKEN);
    assertThat(notification()).containsEntry("status", "SENT");
  }

  @Test
  void smtpDownRetriesWithBackoffAndSendsAfterRecoveryWithoutTouchingTheAlarm() {
    openTemperatureAlarm();
    SMTP.stop();

    worker.processDue();
    Map<String, Object> failed = notification();
    assertThat(failed)
        .containsEntry("status", "PENDING")
        .containsEntry("attempts", 1)
        .containsEntry("alert_status", "ACTIVE")
        .containsEntry("notification_status", "PENDING")
        .containsEntry("notification_attempts", 1);
    assertThat(((java.sql.Timestamp) failed.get("next_attempt_at")).toInstant())
        .isAfter(Instant.now().plusSeconds(10));
    assertThat(worker.processDue()).isZero(); // backing off: not due yet

    SMTP.start();
    makeDue();
    assertThat(worker.processDue()).isEqualTo(1);

    assertThat(SMTP.getReceivedMessages()).hasSize(2);
    assertThat(notification())
        .containsEntry("status", "SENT")
        .containsEntry("attempts", 2)
        .containsEntry("alert_status", "ACTIVE");
  }

  @Test
  void rowIsFailedAfterMaxAttempts() {
    openTemperatureAlarm();
    SMTP.stop();
    for (int i = 0; i < 3; i++) {
      makeDue();
      worker.processDue();
    }
    assertThat(notification())
        .containsEntry("status", "FAILED")
        .containsEntry("attempts", 3)
        .containsEntry("alert_status", "ACTIVE")
        .containsEntry("notification_status", "FAILED");
    makeDue();
    assertThat(worker.processDue()).isZero(); // FAILED is final
  }

  @Test
  void productLookupsPerMailAreCappedAndTheRestShownById() throws Exception {
    String[] slots = new String[12];
    String last = null;
    for (int i = 0; i < 12; i++) {
      last = newProduct();
      slots[i] = slot("S" + i, last, 1, 3);
    }
    openLowStock(machine, 1, slots);

    worker.processDue();

    assertThat(PRODUCT_CALLS.get()).isEqualTo(10);
    assertThat(SMTP.getReceivedMessages()[0].getContent().toString()).contains(last);
    assertThat(notification()).containsEntry("status", "SENT");
  }

  @Test
  void aStaleLeaseCannotFinishARow() {
    openTemperatureAlarm();
    Instant now = Instant.now().plus(1, ChronoUnit.MINUTES);
    NotificationOutbox.Claimed row =
        outbox.claimDue(now, now.plusSeconds(120), 100).stream()
            .filter(c ->
                jdbc.queryForObject(
                        "SELECT machine_id::text FROM alert WHERE id = ?::uuid",
                        String.class,
                        c.alertId().toString())
                    .equals(machine))
            .findFirst()
            .orElseThrow();
    NotificationOutbox.Claimed stale =
        new NotificationOutbox.Claimed(
            row.id(), row.alertId(), row.attempts(), row.leaseUntil().minusSeconds(60));
    outbox.markSent(stale, now);
    assertThat(notification()).containsEntry("status", "PENDING").containsEntry("attempts", 0);
    outbox.markSent(row, now);
    assertThat(notification()).containsEntry("status", "SENT");
  }

  @Test
  void aClaimedRowIsLeasedAndNotClaimedAgain() {
    openTemperatureAlarm();
    Instant now = Instant.now().plus(1, ChronoUnit.MINUTES);
    Set<UUID> first =
        Set.copyOf(
            outbox.claimDue(now, now.plusSeconds(120), 100).stream()
                .map(NotificationOutbox.Claimed::alertId)
                .toList());
    assertThat(first).isNotEmpty();
    assertThat(outbox.claimDue(now, now.plusSeconds(120), 100)).isEmpty();
    // after the lease expired another worker may take it over
    Instant later = now.plusSeconds(300);
    assertThat(outbox.claimDue(later, later.plusSeconds(120), 100)).isNotEmpty();
  }

  private void makeDue() {
    jdbc.update(
        "UPDATE notification SET next_attempt_at = now() - interval '1 second', lease_until = NULL"
            + " WHERE status = 'PENDING'");
  }

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
