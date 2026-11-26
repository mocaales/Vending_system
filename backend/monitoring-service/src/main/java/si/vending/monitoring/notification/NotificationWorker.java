package si.vending.monitoring.notification;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import si.vending.monitoring.alert.AlertDtos.AlertOut;
import si.vending.monitoring.alert.AlertRepository;
import si.vending.monitoring.core.TraceIdFilter;
import si.vending.monitoring.notification.NotificationComposer.Mail;
import si.vending.monitoring.notification.NotificationOutbox.Claimed;

/**
 * Sends the claimed outbox rows by SMTP. A failure never touches the alarm: the row gets one more
 * attempt and a later {@code next_attempt_at} (exponential backoff), and is FAILED after {@code
 * notification.max-attempts}. Scheduling is switched on by {@code monitoring.jobs.enabled}
 * (JobsConfig); {@link #processDue()} can be called directly. Delivery is at least once.
 */
@Component
public class NotificationWorker {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationWorker.class);
  private static final int BATCH = 5;
  private static final Duration LEASE = Duration.ofMinutes(2);
  private static final Duration MAX_DELAY = Duration.ofMinutes(30);

  /** A failure with a fixed code; its message is never used. */
  private static final class RowFailure extends RuntimeException {
    private final String code;

    RowFailure(String code) {
      super(null, null, false, false);
      this.code = code;
    }
  }

  private final NotificationOutbox outbox;
  private final AlertRepository alerts;
  private final NotificationComposer composer;
  private final JavaMailSender mailSender;
  private final Clock clock;
  private final String from;
  private final List<String> recipients;
  private final int maxAttempts;
  private final Duration baseDelay;

  public NotificationWorker(
      NotificationOutbox outbox,
      AlertRepository alerts,
      NotificationComposer composer,
      JavaMailSender mailSender,
      Clock clock,
      @Value("${notification.mail.from}") String from,
      @Value("${notification.mail.to:}") String to,
      @Value("${notification.max-attempts:8}") int maxAttempts,
      @Value("${notification.base-delay:PT30S}") Duration baseDelay) {
    this.outbox = outbox;
    this.alerts = alerts;
    this.composer = composer;
    this.mailSender = mailSender;
    this.clock = clock;
    this.from = from;
    this.recipients =
        Arrays.stream(to.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    this.maxAttempts = maxAttempts;
    this.baseDelay = baseDelay;
  }

  @PostConstruct
  void warnWithoutRecipients() {
    if (recipients.isEmpty()) {
      LOG.warn("ALERT_EMAIL_TO is empty: alarm e-mails cannot be sent (recipients=0).");
    }
  }

  @Scheduled(
      fixedDelayString = "${notification.interval:PT10S}",
      initialDelayString = "${notification.initial-delay:PT15S}")
  public void run() {
    MDC.put(TraceIdFilter.MDC_KEY, UUID.randomUUID().toString());
    try {
      processDue();
    } catch (RuntimeException e) {
      LOG.warn("Notification pass failed: error={}", e.getClass().getSimpleName());
    } finally {
      MDC.remove(TraceIdFilter.MDC_KEY);
    }
  }

  /** One pass: claims the due rows and sends them. Returns the number of rows handled. */
  public int processDue() {
    Instant now = now();
    List<Claimed> due = outbox.claimDue(now, now.plus(LEASE), BATCH);
    for (Claimed row : due) {
      try {
        // the lease may have run out during earlier rows: another instance owns the row now
        if (now().isAfter(row.leaseUntil())) {
          continue;
        }
        handle(row);
      } catch (RuntimeException e) {
        LOG.warn("Notification handling failed: error={}", e.getClass().getSimpleName());
      }
    }
    if (!due.isEmpty()) {
      LOG.debug("Notification pass handled {} rows", due.size());
    }
    return due.size();
  }

  private void handle(Claimed row) {
    try {
      if (recipients.isEmpty()) {
        throw new RowFailure("NO_RECIPIENTS");
      }
      Optional<AlertOut> alert = alerts.find(row.alertId(), false);
      if (alert.isEmpty()) {
        throw new RowFailure("ALERT_MISSING");
      }
      Mail mail = composer.compose(alert.get());
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setFrom(from);
      helper.setTo(recipients.toArray(String[]::new));
      helper.setSubject(mail.subject());
      helper.setText(mail.body(), false);
      mailSender.send(message);
      outbox.markSent(row, now());
    } catch (RuntimeException | jakarta.mail.MessagingException e) {
      fail(row, e);
    }
  }

  private void fail(Claimed row, Exception e) {
    int attempt = row.attempts() + 1;
    Instant now = now();
    boolean last = attempt >= maxAttempts;
    Instant next = last ? null : now.plus(backoff(attempt));
    String code = e instanceof RowFailure f ? f.code : e.getClass().getSimpleName();
    if (code.length() > 100) {
      code = code.substring(0, 100);
    }
    // error class only: SMTP messages can contain addresses
    LOG.warn(
        "Notification attempt failed: notification={} attempt={} error={} final={}",
        row.id(),
        attempt,
        code,
        last);
    outbox.markFailedAttempt(row, next, now, code);
  }

  /** Exponential: base, 2x, 4x ... capped at 30 minutes. */
  Duration backoff(int attempt) {
    long factor = 1L << Math.min(attempt - 1, 20);
    Duration delay = baseDelay.multipliedBy(factor);
    return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
