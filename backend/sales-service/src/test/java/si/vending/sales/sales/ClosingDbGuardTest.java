package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import si.vending.sales.sales.support.ClosingTest;

/**
 * #55 G6 M2, M3, L1 and L2: the database itself keeps a confirmed closing final, forbids reaching
 * CONFIRMED any other way than from a DRAFT without open reconciliations, freezes the identity of a
 * draft and refuses TRUNCATE. All writes go straight to tables {@code sales_closing} and {@code
 * sales_closing_total}.
 *
 * <p>Isolation: rows use a random old date (2001..2009) and a random machine, so a CONFIRMED row
 * that cannot be deleted never collides with the tests that work on yesterday.
 */
class ClosingDbGuardTest extends ClosingTest {

  private static final String CLOSING_COLUMNS =
      "id, closing_date, machine_id, status, note, open_reconciliations, created_at, created_by,"
          + " updated_at, confirmed_at, confirmed_by";

  @AfterEach
  void removeDrafts() {
    jdbc.update("DELETE FROM sales_closing WHERE status = 'DRAFT' AND closing_date < '2010-01-01'");
  }

  private UUID draft(int open) {
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2001, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(3000));
    jdbc.update(
        "INSERT INTO sales_closing (id, closing_date, machine_id, status, note,"
            + " open_reconciliations, created_at, created_by, updated_at)"
            + " VALUES (?, ?, ?, 'DRAFT', '', ?, now(), ?::uuid, now())",
        id,
        date,
        UUID.randomUUID(),
        open,
        OPERATOR_ID);
    return id;
  }

  private void total(UUID closing, String currency) {
    jdbc.update(
        "INSERT INTO sales_closing_total (closing_id, currency, sales_count, quantity, turnover)"
            + " VALUES (?, ?, 1, 1, 2.50)",
        closing,
        currency);
  }

  private void confirmInPlace(UUID id) {
    jdbc.update(
        "UPDATE sales_closing SET status = 'CONFIRMED', confirmed_at = now(),"
            + " confirmed_by = ?::uuid WHERE id = ?",
        OPERATOR_ID,
        id);
  }

  private UUID confirmed() {
    UUID id = draft(0);
    total(id, "EUR");
    confirmInPlace(id);
    return id;
  }

  private Map<String, Object> closingRow(UUID id) {
    return jdbc.queryForMap("SELECT " + CLOSING_COLUMNS + " FROM sales_closing WHERE id = ?", id);
  }

  private void assertRejected(UUID id, String set, Object... args) {
    Map<String, Object> before = closingRow(id);
    Object[] all = new Object[args.length + 1];
    System.arraycopy(args, 0, all, 0, args.length);
    all[args.length] = id;
    assertThatThrownBy(() -> jdbc.update("UPDATE sales_closing SET " + set + " WHERE id = ?", all))
        .as(set)
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(closingRow(id)).as("row unchanged after: " + set).isEqualTo(before);
  }

  // --- M3: the only way into CONFIRMED

  @Test
  void aClosingCannotBeInsertedAlreadyConfirmed() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO sales_closing (id, closing_date, machine_id, status, note,"
                    + " open_reconciliations, created_at, created_by, updated_at,"
                    + " confirmed_at, confirmed_by)"
                    + " VALUES (?, '2001-02-03', ?, 'CONFIRMED', '', 0, now(), ?::uuid,"
                    + " now(), now(), ?::uuid)",
                UUID.randomUUID(),
                UUID.randomUUID(),
                OPERATOR_ID,
                OPERATOR_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aDraftWithOpenReconciliationsCannotBecomeConfirmedEvenWithConfirmFieldsSet() {
    UUID id = draft(2);

    assertRejected(
        id, "status = 'CONFIRMED', confirmed_at = now(), confirmed_by = ?::uuid", OPERATOR_ID);
    assertThat(closingRow(id).get("status")).isEqualTo("DRAFT");
  }

  @Test
  void aDraftWithoutOpenReconciliationsBecomesConfirmed() {
    UUID id = draft(0);

    int updated =
        jdbc.update(
            "UPDATE sales_closing SET status = 'CONFIRMED', confirmed_at = now(),"
                + " confirmed_by = ?::uuid WHERE id = ?",
            OPERATOR_ID,
            id);

    assertThat(updated).isEqualTo(1);
    assertThat(closingRow(id).get("status")).isEqualTo("CONFIRMED");
  }

  @Test
  void theIdentityOfADraftCannotChange() {
    UUID id = draft(0);

    assertRejected(id, "closing_date = closing_date + 1");
    assertRejected(id, "machine_id = ?::uuid", UUID.randomUUID().toString());
    assertRejected(id, "machine_id = NULL");
    assertRejected(id, "created_at = created_at + interval '1 minute'");
    assertRejected(id, "created_by = ?::uuid", UUID.randomUUID().toString());
    assertRejected(id, "id = ?::uuid", UUID.randomUUID().toString());
  }

  @Test
  void noteOpenReconciliationsAndUpdatedAtOfADraftCanChange() {
    UUID id = draft(0);

    int updated =
        jdbc.update(
            "UPDATE sales_closing SET note = 'changed', open_reconciliations = 3,"
                + " updated_at = now() + interval '1 minute' WHERE id = ?",
            id);

    assertThat(updated).isEqualTo(1);
    assertThat(closingRow(id))
        .containsEntry("note", "changed")
        .containsEntry("open_reconciliations", 3);
  }

  // --- confirmed closings stay final (L2)

  @Test
  void aConfirmedClosingCannotBeUpdatedOrDeleted() {
    UUID id = confirmed();

    assertRejected(id, "note = 'edited'");
    assertRejected(id, "open_reconciliations = 1");
    assertRejected(id, "status = 'DRAFT', confirmed_at = NULL, confirmed_by = NULL");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM sales_closing WHERE id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(closingRow(id).get("status")).isEqualTo("CONFIRMED");
  }

  @Test
  void theTotalsOfAConfirmedClosingCannotBeInsertedUpdatedOrDeleted() {
    UUID id = confirmed();

    assertThatThrownBy(() -> total(id, "USD")).isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbc.update("UPDATE sales_closing_total SET turnover = 9 WHERE closing_id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM sales_closing_total WHERE closing_id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_closing_total WHERE closing_id = ?", Integer.class, id))
        .isEqualTo(1);
  }

  // --- M2: totals cannot be moved between closings

  @Test
  void aTotalCannotBeMovedOutOfAConfirmedClosingIntoADraft() {
    UUID confirmed = confirmed();
    UUID target = draft(0);

    assertThatThrownBy(() ->
            jdbc.update(
                "UPDATE sales_closing_total SET closing_id = ? WHERE closing_id = ?",
                target,
                confirmed))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_closing_total WHERE closing_id = ?",
                Integer.class,
                confirmed))
        .isEqualTo(1);
  }

  @Test
  void aTotalCannotBeMovedFromADraftIntoAConfirmedClosing() {
    UUID confirmed = confirmed();
    UUID source = draft(0);
    total(source, "USD");

    assertThatThrownBy(() ->
            jdbc.update(
                "UPDATE sales_closing_total SET closing_id = ? WHERE closing_id = ?",
                confirmed,
                source))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_closing_total WHERE closing_id = ?",
                Integer.class,
                source))
        .isEqualTo(1);
  }

  // --- L1: TRUNCATE

  /** The pool evicts a connection after some errors (0A000); the rollback is then moot. */
  private static void rollbackQuietly(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException evicted) {
      // the connection is gone together with its transaction
    }
  }

  private void assertTruncateRejected(String sql) throws SQLException {
    try (Connection connection = jdbc.getDataSource().getConnection()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        assertThatThrownBy(() -> statement.execute(sql)).as(sql).isInstanceOf(SQLException.class);
      } finally {
        rollbackQuietly(connection);
      }
    }
  }

  @Test
  void truncateOfTheClosingTablesIsRefused() throws SQLException {
    confirmed();

    assertTruncateRejected("TRUNCATE sales_closing");
    assertTruncateRejected("TRUNCATE sales_closing CASCADE");
    assertTruncateRejected("TRUNCATE sales_closing_total");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_closing", Integer.class))
        .isGreaterThan(0);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_closing_total", Integer.class))
        .isGreaterThan(0);
  }
}
