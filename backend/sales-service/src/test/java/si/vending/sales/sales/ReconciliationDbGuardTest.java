package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import si.vending.sales.sales.support.ReconciliationTest;

/**
 * #35 G6 M1 and L3: the database itself guarantees that the reconciliation record exists exactly
 * when the sale is RESOLVED, that RESOLVED is reached only from NEEDS_RECONCILIATION, and that a
 * resolved sale is final. All writes here go straight to table {@code sales}.
 */
class ReconciliationDbGuardTest extends ReconciliationTest {

  private static final String RECORD =
      "resolution = 'ADJUSTED', note = 'by hand', resolved_at = now(), resolved_by = ?::uuid";

  private Map<String, Object> row(Fx sale) {
    return jdbc.queryForMap("SELECT * FROM sales WHERE sale_id = ?", sale.saleId());
  }

  private void assertRejected(Fx sale, String set, Object... args) {
    Map<String, Object> before = row(sale);
    Object[] all = new Object[args.length + 1];
    System.arraycopy(args, 0, all, 0, args.length);
    all[args.length] = sale.saleId();
    assertThatThrownBy(() -> jdbc.update("UPDATE sales SET " + set + " WHERE sale_id = ?", all))
        .as(set)
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(row(sale)).as("row unchanged after: " + set).isEqualTo(before);
  }

  private Fx resolvedSale() {
    return insert(UUID.randomUUID(), UUID.randomUUID(), now().minusSeconds(60), "RESOLVED");
  }

  // --- the only way into RESOLVED

  @Test
  void needsReconciliationToResolvedWithAFullRecordSucceeds() {
    Fx sale = open();

    int updated =
        jdbc.update(
            "UPDATE sales SET inventory_state = 'RESOLVED', " + RECORD + " WHERE sale_id = ?",
            OPERATOR_ID,
            sale.saleId());

    assertThat(updated).isEqualTo(1);
    assertThat(row(sale).get("inventory_state")).isEqualTo("RESOLVED");
    assertThat(row(sale).get("resolution")).isEqualTo("ADJUSTED");
  }

  @Test
  void anAppliedSaleCannotBecomeResolvedEvenWithAFullRecord() {
    Fx sale = insert(UUID.randomUUID(), UUID.randomUUID(), now().minusSeconds(60), "APPLIED");
    assertRejected(sale, "inventory_state = 'RESOLVED', " + RECORD, OPERATOR_ID);
  }

  @Test
  void aPendingSaleCannotBecomeResolvedEvenWithAFullRecord() {
    Fx sale = insert(UUID.randomUUID(), UUID.randomUUID(), now().minusSeconds(60), "PENDING");
    assertRejected(sale, "inventory_state = 'RESOLVED', " + RECORD, OPERATOR_ID);
  }

  // --- record present if and only if RESOLVED

  @Test
  void aRecordCannotBeSetWhileTheStateStaysNeedsReconciliation() {
    assertRejected(open(), RECORD, OPERATOR_ID);
  }

  @Test
  void aRecordCannotBeSetWhileTheStateStaysApplied() {
    Fx sale = insert(UUID.randomUUID(), UUID.randomUUID(), now().minusSeconds(60), "APPLIED");
    assertRejected(sale, RECORD, OPERATOR_ID);
  }

  @Test
  void aRecordCannotBeSetWhileTheStateStaysPending() {
    Fx sale = insert(UUID.randomUUID(), UUID.randomUUID(), now().minusSeconds(60), "PENDING");
    assertRejected(sale, RECORD, OPERATOR_ID);
  }

  @Test
  void aPartialRecordIsRejected() {
    Fx sale = open();
    assertRejected(sale, "inventory_state = 'RESOLVED', resolution = 'ADJUSTED'");
    assertRejected(sale, "resolution = 'WRITTEN_OFF', note = 'only two'");
  }

  @Test
  void resolvedWithoutARecordIsRejectedOnUpdate() {
    assertRejected(open(), "inventory_state = 'RESOLVED'");
  }

  @Test
  void resolvedWithoutARecordIsRejectedOnInsert() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO sales (sale_id, machine_id, slot_code, product_id, quantity,"
                    + " unit_price_paid, currency, payment_method, occurred_at,"
                    + " inventory_state) VALUES (?, ?, 'A1', ?, 1, 2.50, 'EUR', 'CARD', now(),"
                    + " 'RESOLVED')",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- a resolved sale is final

  @Test
  void aResolvedSaleKeepsItsNoteResolutionAuthorAndTime() {
    Fx sale = resolvedSale();
    assertRejected(sale, "note = 'rewritten'");
    assertRejected(sale, "resolution = 'WRITTEN_OFF'");
    assertRejected(sale, "resolved_by = ?::uuid", OTHER_OPERATOR_ID);
    assertRejected(sale, "resolved_at = resolved_at + interval '1 hour'");
  }

  @Test
  void aResolvedSaleCannotLeaveTheResolvedState() {
    Fx sale = resolvedSale();
    assertRejected(sale, "inventory_state = 'PENDING'");
    assertRejected(sale, "inventory_state = 'NEEDS_RECONCILIATION'");
    assertRejected(sale, "inventory_state = 'APPLIED'");
  }

  @Test
  void aResolvedSaleCannotBeReopenedByClearingTheRecord() {
    Fx sale = resolvedSale();
    String clear = "resolution = NULL, note = NULL, resolved_at = NULL, resolved_by = NULL";
    assertRejected(sale, clear);
    assertRejected(sale, "inventory_state = 'NEEDS_RECONCILIATION', " + clear);
    assertRejected(sale, "inventory_state = 'PENDING', " + clear);
  }

  @Test
  void aResolvedSaleKeepsItsInventoryReason() {
    assertRejected(resolvedSale(), "inventory_reason = 'INSUFFICIENT_STOCK'");
  }

  @Test
  void aResolvedSaleStaysUnchangedWhenTheSameValuesAreWrittenBack() {
    Fx sale = resolvedSale();
    assertThat(jdbc.update("UPDATE sales SET note = note WHERE sale_id = ?", sale.saleId()))
        .isEqualTo(1);
  }

  // --- earlier guards still hold

  @Test
  void deleteTruncateAndFactChangesStillFail() {
    List<Fx> sales = List.of(open(), resolvedSale());
    for (Fx sale : sales) {
      assertThatThrownBy(() -> jdbc.update("DELETE FROM sales WHERE sale_id = ?", sale.saleId()))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertRejected(sale, "quantity = 9");
    }
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE sales"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    for (Fx sale : sales) {
      assertThat(row(sale)).isNotEmpty();
    }
  }
}
