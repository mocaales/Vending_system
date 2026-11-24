package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import si.vending.sales.sales.support.DependencyStub.Mode;
import si.vending.sales.sales.support.DependencyStub.Reply;
import si.vending.sales.sales.support.OutboxTest;

/** G6 findings of #32: one bad answer or entry never breaks the pass or a final state. */
@ExtendWith(OutputCaptureExtension.class)
class OutboxHardeningTest extends OutboxTest {

  // --- S1: an answer the sale table cannot hold is a reconciliation without reason

  @Test
  void anUnknownReasonBecomesNeedsReconciliationWithoutReason() {
    String saleId = accept(knownSlot());
    INVENTORY.scriptDecrease(Reply.needsReconciliation("SOMETHING_ELSE"));

    assertThatCode(this::runOnce).doesNotThrowAnyException();

    var inventory = inventoryOf(saleId);
    assertThat(inventory.get("state")).isEqualTo("NEEDS_RECONCILIATION");
    assertThat(inventory.get("reason")).isNull();
    assertThat(isDone(saleId)).isTrue();
  }

  @Test
  void aReasonLongerThan24CharactersBecomesNeedsReconciliationWithoutReason() {
    String saleId = accept(knownSlot());
    INVENTORY.scriptDecrease(Reply.needsReconciliation("X".repeat(25)));

    assertThatCode(this::runOnce).doesNotThrowAnyException();

    var inventory = inventoryOf(saleId);
    assertThat(inventory.get("state")).isEqualTo("NEEDS_RECONCILIATION");
    assertThat(inventory.get("reason")).isNull();
    assertThat(isDone(saleId)).isTrue();
  }

  @Test
  void aBadAnswerInTheMiddleDoesNotStopTheRestOfThePass() {
    List<String> saleIds = acceptThree();
    // the first delivered entry gets an answer that used to blow up the pass
    INVENTORY.scriptDecrease(
        Reply.needsReconciliation("SOMETHING_ELSE"), Reply.applied(), Reply.applied());

    assertThatCode(this::runOnce).doesNotThrowAnyException();

    assertThat(INVENTORY.decreaseCalls()).hasSize(3);
    assertThat(saleIds).allMatch(this::isDone);
    assertThat(saleIds.stream().filter(id -> stateOf(id).equals("APPLIED")).count()).isEqualTo(2);
  }

  @Test
  void anUnreadableAnswerIsRescheduledAndTheOtherEntriesOfThePassAreDelivered() {
    List<String> saleIds = acceptThree();
    Instant before = Instant.now();
    INVENTORY.scriptDecrease(new Reply(201, "not json", false), Reply.applied(), Reply.applied());

    assertThatCode(this::runOnce).doesNotThrowAnyException();

    assertThat(INVENTORY.decreaseCalls()).hasSize(3);
    List<String> open = saleIds.stream().filter(id -> !isDone(id)).toList();
    assertThat(open).hasSize(1);
    String bad = open.get(0);
    assertThat(stateOf(bad)).isEqualTo("PENDING");
    assertThat(attempts(bad)).isEqualTo(1);
    assertThat(nextAttemptAt(bad)).isAfter(before.plusMillis(500));
    saleIds.stream()
        .filter(id -> !id.equals(bad))
        .forEach(id -> assertThat(stateOf(id)).isEqualTo("APPLIED"));
  }

  // --- S2: a late result never overwrites a final state

  @Test
  void aLateAppliedAnswerDoesNotOverwriteNeedsReconciliation() {
    String saleId = accept(knownSlot());
    setSaleState(saleId, "NEEDS_RECONCILIATION");
    INVENTORY.scriptDecrease(Reply.applied());

    runOnce();

    assertThat(stateOf(saleId)).isEqualTo("NEEDS_RECONCILIATION");
    assertThat(isDone(saleId)).as("the entry is closed anyway").isTrue();
  }

  @Test
  void aLateAnswerDoesNotOverwriteResolved() {
    String saleId = accept(knownSlot());
    setSaleState(saleId, "RESOLVED");
    INVENTORY.scriptDecrease(Reply.needsReconciliation("INSUFFICIENT_STOCK"));

    runOnce();

    var inventory = inventoryOf(saleId);
    assertThat(inventory.get("state")).isEqualTo("RESOLVED");
    assertThat(inventory.get("reason")).isNull();
    assertThat(isDone(saleId)).isTrue();
  }

  @Test
  void aLatePermanentRejectionDoesNotOverwriteApplied() {
    String saleId = accept(knownSlot());
    setSaleState(saleId, "APPLIED");
    INVENTORY.scriptDecrease(Reply.error(404, "SLOT_NOT_FOUND"));

    runOnce();

    assertThat(stateOf(saleId)).isEqualTo("APPLIED");
    assertThat(isDone(saleId)).isTrue();
  }

  // --- S3: a long-failing entry is loud

  @Test
  void theTwelfthFailedDeliveryLogsAnErrorNamingInventoryAndTheSale(CapturedOutput output) {
    String saleId = accept(knownSlot());
    jdbc.update("UPDATE sale_outbox SET attempts = 11 WHERE sale_id = ?::uuid", saleId);
    INVENTORY.setMode(Mode.SERVER_ERROR);

    runOnce();

    assertThat(attempts(saleId)).isEqualTo(12);
    assertThat(errorLinesAbout(output, saleId))
        .as("one ERROR line with Inventory and the saleId")
        .hasSize(1)
        .allMatch(line -> line.contains("Inventory"));
  }

  @Test
  void belowTheThresholdTheFailureIsOnlyAWarning(CapturedOutput output) {
    String saleId = accept(knownSlot());
    jdbc.update("UPDATE sale_outbox SET attempts = 10 WHERE sale_id = ?::uuid", saleId);
    INVENTORY.setMode(Mode.SERVER_ERROR);

    runOnce();

    assertThat(attempts(saleId)).isEqualTo(11);
    assertThat(errorLinesAbout(output, saleId)).isEmpty();
  }

  // --- S4: a pass stops after the first connectivity failure

  @Test
  void aPassStopsAfterTheFirstConnectivityFailureAndLeavesTheRestDue() {
    List<String> saleIds = acceptThree();
    INVENTORY.setMode(Mode.SERVER_ERROR);

    runOnce();

    assertThat(INVENTORY.decreaseCalls()).as("one call, then the pass stops").hasSize(1);
    List<String> untouched = saleIds.stream().filter(id -> attempts(id) == 0).toList();
    assertThat(untouched).hasSize(2);
    for (String id : untouched) {
      assertThat(isDone(id)).isFalse();
      assertThat(stateOf(id)).isEqualTo("PENDING");
      assertThat(nextAttemptAt(id))
          .as("not hidden behind the claim lease")
          .isBeforeOrEqualTo(Instant.now().plusSeconds(2));
    }

    INVENTORY.setMode(Mode.NORMAL);
    runOnce();
    assertThat(INVENTORY.decreaseCalls()).hasSize(3);
    assertThat(untouched).allMatch(id -> stateOf(id).equals("APPLIED"));
  }

  @Test
  void aTimeoutAlsoStopsThePass() {
    acceptThree();
    INVENTORY.scriptDecrease(Reply.dropConnection());

    runOnce();

    assertThat(INVENTORY.decreaseCalls()).hasSize(1);
  }

  @Test
  void aPermanentRejectionDoesNotStopThePass() {
    List<String> saleIds = acceptThree();
    INVENTORY.scriptDecrease(Reply.error(404, "SLOT_NOT_FOUND"), Reply.applied(), Reply.applied());

    runOnce();

    assertThat(INVENTORY.decreaseCalls()).hasSize(3);
    assertThat(saleIds).allMatch(this::isDone);
  }

  // --- helpers

  private List<String> acceptThree() {
    List<String> saleIds = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      saleIds.add(accept(knownSlot()));
    }
    return saleIds;
  }

  private void setSaleState(String saleId, String state) {
    jdbc.update(
        "UPDATE sales SET inventory_state = ?, inventory_reason = NULL WHERE sale_id = ?::uuid",
        state,
        saleId);
  }

  private static List<String> errorLinesAbout(CapturedOutput output, String saleId) {
    return output.getAll().lines().filter(l -> l.contains("ERROR") && l.contains(saleId)).toList();
  }
}
