package si.vending.monitoring.notification;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The e-mail outbox ({@code notification} table). {@link #enqueue} runs inside the transaction that
 * opens the alarm; the worker claims due rows with a lease and {@code SKIP LOCKED}, so two
 * instances never send the same row at the same time.
 */
@Component
public class NotificationOutbox {

  /** A claimed row: the alert to announce and how many attempts were already made. */
  public record Claimed(UUID id, UUID alertId, int attempts, Instant leaseUntil) {}

  private final JdbcClient jdbc;

  public NotificationOutbox(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Writes the PENDING row for a newly opened alarm (one per alarm, duplicates are ignored). */
  public void enqueue(UUID alertId, Instant now) {
    jdbc.sql(
            "INSERT INTO notification (id, alert_id, status, attempts, next_attempt_at, created_at)"
                + " VALUES (:id, :alert, 'PENDING', 0, :now, :now)"
                + " ON CONFLICT (alert_id) DO NOTHING")
        .param("id", UUID.randomUUID())
        .param("alert", alertId)
        .param("now", Timestamp.from(now))
        .update();
  }

  /**
   * Claims up to {@code limit} due PENDING rows for {@code lease}; each row only once at a time.
   */
  public List<Claimed> claimDue(Instant now, Instant leaseUntil, int limit) {
    return jdbc.sql(
            "UPDATE notification SET lease_until = :lease WHERE id IN ("
                + " SELECT id FROM notification WHERE status = 'PENDING' AND next_attempt_at <= :now"
                + " AND (lease_until IS NULL OR lease_until < :now)"
                + " ORDER BY next_attempt_at, id LIMIT :limit FOR UPDATE SKIP LOCKED)"
                + " RETURNING id, alert_id, attempts, lease_until")
        .param("lease", Timestamp.from(leaseUntil))
        .param("now", Timestamp.from(now))
        .param("limit", limit)
        .query((rs, i) ->
            new Claimed(
                rs.getObject("id", UUID.class),
                rs.getObject("alert_id", UUID.class),
                rs.getInt("attempts"),
                rs.getTimestamp("lease_until").toInstant()))
        .list();
  }

  /** Marks the row SENT (one more attempt) and mirrors the state into the alert. */
  public void markSent(Claimed row, Instant now) {
    finish(row, "SENT", now, now, null);
  }

  /** One more failed attempt: retry at {@code nextAttemptAt}, or FAILED for good if null. */
  public void markFailedAttempt(Claimed row, Instant nextAttemptAt, Instant now, String errorCode) {
    finish(row, nextAttemptAt == null ? "FAILED" : "PENDING", nextAttemptAt, null, errorCode);
  }

  /** Only the holder of the current lease may finish a row; otherwise this is a no-op. */
  private void finish(Claimed row, String status, Instant next, Instant sentAt, String error) {
    jdbc.sql(
            "WITH n AS (UPDATE notification SET status = :status, attempts = attempts + 1,"
                + " next_attempt_at = COALESCE(:next, next_attempt_at), lease_until = NULL,"
                + " sent_at = :sent, last_error = :error WHERE id = :id AND status = 'PENDING'"
                + " AND lease_until = :lease"
                + " RETURNING alert_id, status, attempts)"
                + " UPDATE alert a SET notification_status = n.status,"
                + " notification_attempts = n.attempts FROM n WHERE a.id = n.alert_id")
        .param("status", status)
        .param("next", next == null ? null : Timestamp.from(next))
        .param("sent", sentAt == null ? null : Timestamp.from(sentAt))
        .param("error", error)
        .param("id", row.id())
        .param("lease", Timestamp.from(row.leaseUntil()))
        .update();
  }
}
