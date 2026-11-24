package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.sales.support.DependencyStub.Mode;
import si.vending.sales.sales.support.DependencyStub.Reply;
import si.vending.sales.sales.support.OutboxTest;
import tools.jackson.databind.json.JsonMapper;

/**
 * #32 acceptance 2 to 4: the worker sends decrease for the sale and maps DecreaseOut to the state
 * (docs/03 section 7, docs/01 section 6.3). Inventory is the stub of the contract in {@code
 * backend/inventory-service/openapi.yaml}.
 */
class OutboxWorkerTest extends OutboxTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  void sendsTheDecreaseRequestOfTheContract() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    body.put("quantity", 3);
    String saleId = accept(body);

    runOnce();

    var calls = INVENTORY.decreaseCalls();
    assertThat(calls).hasSize(1);
    var call = calls.get(0);
    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.path())
        .isEqualTo("/v1/inventory/" + setup.machineId() + "/" + setup.slot() + "/decrease");
    @SuppressWarnings("unchecked")
    Map<String, Object> sent = JSON.readValue(call.body(), Map.class);
    assertThat(sent.keySet()).containsExactlyInAnyOrder("saleId", "productId", "quantity");
    assertThat(sent.get("saleId")).isEqualTo(saleId);
    assertThat(sent.get("productId")).isEqualTo(setup.productId());
    assertThat(sent.get("quantity")).isEqualTo(3);
  }

  @Test
  void appliedBecomesAppliedWithoutReasonAndTheEntryIsDone() {
    String saleId = accept(knownSlot());
    INVENTORY.scriptDecrease(Reply.applied());

    runOnce();

    Map<String, Object> inventory = inventoryOf(saleId);
    assertThat(inventory.get("state")).isEqualTo("APPLIED");
    assertThat(inventory.get("reason")).isNull();
    assertThat(isDone(saleId)).isTrue();
  }

  @Test
  void productMismatchBecomesNeedsReconciliationWithTheReason() {
    String saleId = accept(knownSlot());
    INVENTORY.scriptDecrease(Reply.needsReconciliation("PRODUCT_MISMATCH"));

    runOnce();

    Map<String, Object> inventory = inventoryOf(saleId);
    assertThat(inventory.get("state")).isEqualTo("NEEDS_RECONCILIATION");
    assertThat(inventory.get("reason")).isEqualTo("PRODUCT_MISMATCH");
    assertThat(isDone(saleId)).isTrue();
  }

  @Test
  void insufficientStockBecomesNeedsReconciliationWithTheReason() {
    String saleId = accept(knownSlot());
    INVENTORY.scriptDecrease(Reply.needsReconciliation("INSUFFICIENT_STOCK"));

    runOnce();

    Map<String, Object> inventory = inventoryOf(saleId);
    assertThat(inventory.get("state")).isEqualTo("NEEDS_RECONCILIATION");
    assertThat(inventory.get("reason")).isEqualTo("INSUFFICIENT_STOCK");
  }

  @Test
  void reconciliationNeverRewritesTheSaleFacts() {
    Map<String, Object> body = sale(knownSlot());
    String saleId = accept(body);
    INVENTORY.scriptDecrease(Reply.needsReconciliation("INSUFFICIENT_STOCK"));

    runOnce();

    var out = asOperator("/v1/sales/" + saleId).json();
    assertThat(out.get("quantity")).isEqualTo(1);
    assertThat(out.get("unitPricePaid")).isEqualTo("2.50");
    assertThat(Instant.parse((String) out.get("occurredAt")))
        .isEqualTo(Instant.parse((String) body.get("occurredAt")));
  }

  @Test
  void aRepeatAnswer200IsHandledLikeA201() {
    String first = accept(knownSlot());
    String second = accept(knownSlot());
    INVENTORY.scriptDecrease(
        Reply.repeatApplied(), Reply.repeatNeedsReconciliation("PRODUCT_MISMATCH"));

    runOnce();

    // Which entry got which scripted answer depends on the order of delivery.
    assertThat(List.of(stateOf(first), stateOf(second)))
        .containsExactlyInAnyOrder("APPLIED", "NEEDS_RECONCILIATION");
    assertThat(isDone(first)).isTrue();
    assertThat(isDone(second)).isTrue();
  }

  @Test
  void aDoneEntryIsNeverDeliveredAgain() {
    String saleId = accept(knownSlot());

    runOnce();
    runOnce();
    makeDue(saleId);
    runOnce();

    assertThat(INVENTORY.decreaseCallsFor(saleId)).hasSize(1);
    assertThat(stateOf(saleId)).isEqualTo("APPLIED");
  }

  @Test
  void usesSalesOwnServiceTokenAndTheTraceIdOfTheIntakeRequest() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    String trace = "trace-" + UUID.randomUUID();
    var response =
        post(
            "/v1/sales",
            json(body),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Machine-Id",
            setup.machineId(),
            "X-Trace-Id",
            trace);
    assertThat(response.status()).isEqualTo(201);

    runOnce();

    var call = INVENTORY.decreaseCalls().get(0);
    assertThat(call.traceId()).isEqualTo(trace);
    assertThat(call.serviceToken()).isEqualTo(SALES_TOKEN).isNotEqualTo(GATEWAY_TOKEN);
  }

  @Test
  void theTraceIdOfEachEntryIsItsOwn() {
    Setup one = knownSlot();
    Setup two = knownSlot();
    String traceOne = "trace-" + UUID.randomUUID();
    String traceTwo = "trace-" + UUID.randomUUID();
    for (String[] c : new String[][] {{traceOne}, {traceTwo}}) {
      Setup s = c[0].equals(traceOne) ? one : two;
      Map<String, Object> body = sale(s);
      var r =
          post(
              "/v1/sales",
              json(body),
              "X-Service-Token",
              GATEWAY_TOKEN,
              "X-Machine-Id",
              s.machineId(),
              "X-Trace-Id",
              c[0]);
      assertThat(r.status()).isEqualTo(201);
    }

    runOnce();

    assertThat(INVENTORY.decreaseCalls())
        .anyMatch(c -> c.path().contains(one.machineId()) && traceOne.equals(c.traceId()))
        .anyMatch(c -> c.path().contains(two.machineId()) && traceTwo.equals(c.traceId()));
  }

  @Test
  void aTraceIdIsSentEvenWhenTheIntakeRequestHadNone() {
    String saleId = accept(knownSlot());

    runOnce();

    assertThat(INVENTORY.decreaseCallsFor(saleId).get(0).traceId()).isNotBlank();
  }

  @Test
  void anEmptyOutboxIsAHarmlessPass() {
    assertThatCode(this::runOnce).doesNotThrowAnyException();
    assertThat(INVENTORY.decreaseCalls()).isEmpty();
  }

  // --- unreachable Inventory: stays PENDING, retried with backoff

  @Test
  void serverErrorKeepsThePendingStateAndSchedulesARetry() {
    String saleId = accept(knownSlot());
    Instant before = Instant.now();
    INVENTORY.setMode(Mode.SERVER_ERROR);

    runOnce();

    assertThat(stateOf(saleId)).isEqualTo("PENDING");
    assertThat(isDone(saleId)).isFalse();
    assertThat(attempts(saleId)).isEqualTo(1);
    assertThat(nextAttemptAt(saleId))
        .isAfter(before.plusMillis(500))
        .isBeforeOrEqualTo(Instant.now().plus(Duration.ofSeconds(6)));
  }

  @Test
  void unavailableAnswer503KeepsThePendingState() {
    String saleId = accept(knownSlot());
    INVENTORY.setMode(Mode.UNAVAILABLE);

    runOnce();

    assertThat(stateOf(saleId)).isEqualTo("PENDING");
    assertThat(attempts(saleId)).isEqualTo(1);
    assertThat(isDone(saleId)).isFalse();
  }

  @Test
  void aDroppedConnectionKeepsThePendingState() {
    String saleId = accept(knownSlot());
    INVENTORY.scriptDecrease(Reply.dropConnection());

    runOnce();

    assertThat(stateOf(saleId)).isEqualTo("PENDING");
    assertThat(attempts(saleId)).isEqualTo(1);
    assertThat(isDone(saleId)).isFalse();
  }

  @Test
  void aTimeoutKeepsThePendingStateAndGivesUpBeforeTheStubAnswers() {
    String saleId = accept(knownSlot());
    INVENTORY.setMode(Mode.SLOW); // answers after 12 s

    long start = System.nanoTime();
    runOnce();
    long seconds = (System.nanoTime() - start) / 1_000_000_000L;

    assertThat(seconds).as("the call has a timeout shorter than 11 s").isLessThan(11);
    assertThat(stateOf(saleId)).isEqualTo("PENDING");
    assertThat(attempts(saleId)).isEqualTo(1);
    assertThat(isDone(saleId)).isFalse();
  }

  @Test
  void anEntryThatIsNotDueYetIsNotRetried() {
    String saleId = accept(knownSlot());
    INVENTORY.setMode(Mode.SERVER_ERROR);
    runOnce();
    INVENTORY.setMode(Mode.NORMAL); // Inventory is back, but the backoff has not elapsed

    runOnce();

    assertThat(INVENTORY.decreaseCallsFor(saleId)).as("only the failed first try").hasSize(1);
    assertThat(stateOf(saleId)).isEqualTo("PENDING");
    assertThat(attempts(saleId)).isEqualTo(1);
  }

  @Test
  void afterRecoveryTheSaleIsAppliedExactlyOnce() {
    String saleId = accept(knownSlot());
    INVENTORY.setMode(Mode.UNAVAILABLE);
    runOnce();
    makeDue(saleId);
    runOnce();
    assertThat(stateOf(saleId)).isEqualTo("PENDING");
    assertThat(attempts(saleId)).isEqualTo(2);

    INVENTORY.setMode(Mode.NORMAL);
    makeDue(saleId);
    runOnce();
    runOnce();
    makeDue(saleId);
    runOnce();

    assertThat(stateOf(saleId)).isEqualTo("APPLIED");
    assertThat(isDone(saleId)).isTrue();
    // two failed deliveries, one successful, nothing after the success
    assertThat(INVENTORY.decreaseCallsFor(saleId)).hasSize(3);
  }

  @Test
  void theBackoffGrowsAndIsCapped() {
    String saleId = accept(knownSlot());
    INVENTORY.setMode(Mode.SERVER_ERROR);

    runOnce();
    long first = delayMillis(saleId);
    for (int i = 0; i < 3; i++) {
      makeDue(saleId);
      runOnce();
    }
    long fourth = delayMillis(saleId);

    assertThat(attempts(saleId)).isEqualTo(4);
    assertThat(first).as("initial-backoff PT2S").isBetween(500L, 3_500L);
    assertThat(fourth).as("grown, capped by max-backoff PT5S").isBetween(first, 6_500L);
  }

  @Test
  void aLostAnswerIsHarmlessBecauseTheRepeatAnswers200WithTheSameBody() {
    // Inventory applied the first try but the answer got lost; the retry gets 200 + stored result.
    String saleId = accept(knownSlot());
    INVENTORY.scriptDecrease(Reply.dropConnection(), Reply.repeatApplied());

    runOnce();
    assertThat(stateOf(saleId)).isEqualTo("PENDING");
    makeDue(saleId);
    runOnce();

    assertThat(stateOf(saleId)).isEqualTo("APPLIED");
    var calls = INVENTORY.decreaseCallsFor(saleId);
    assertThat(calls).hasSize(2);
    assertThat(calls.get(0).body()).isEqualTo(calls.get(1).body());
  }

  // --- permanent 4xx answers: no endless retries

  @Test
  void slotNotFoundIsPermanentAndGoesToReconciliation() {
    String saleId = accept(knownSlot());
    INVENTORY.scriptDecrease(Reply.error(404, "SLOT_NOT_FOUND"));

    runOnce();

    assertPermanent(saleId);
  }

  @Test
  void validationErrorIsPermanentAndGoesToReconciliation() {
    String saleId = accept(knownSlot());
    INVENTORY.scriptDecrease(Reply.error(422, "VALIDATION_ERROR"));

    runOnce();

    assertPermanent(saleId);
  }

  @Test
  void saleIdConflictIsPermanentAndGoesToReconciliation() {
    String saleId = accept(knownSlot());
    INVENTORY.scriptDecrease(Reply.error(409, "SALE_ID_CONFLICT"));

    runOnce();

    assertPermanent(saleId);
  }

  @Test
  void aRejectedServiceTokenIsARecoverableConfigurationErrorAndStaysPending() {
    for (int status : new int[] {401, 403}) {
      String saleId = accept(knownSlot());
      INVENTORY.scriptDecrease(
          Reply.error(status, status == 401 ? "UNAUTHENTICATED" : "CALLER_NOT_ALLOWED"));

      runOnce();

      assertThat(stateOf(saleId)).as("HTTP " + status).isEqualTo("PENDING");
      assertThat(isDone(saleId)).as("HTTP " + status).isFalse();
      assertThat(attempts(saleId)).as("HTTP " + status).isEqualTo(1);
    }
  }

  /**
   * CONTRACT CHOICE (the docs are silent; flagged to the CoS): a 4xx that Inventory answers the
   * same way every time ends the entry. State NEEDS_RECONCILIATION (the operator resolves it,
   * docs/01 section 6.3), reason null (the reasons PRODUCT_MISMATCH and INSUFFICIENT_STOCK of
   * DecreaseOut do not apply), the entry done, no further calls.
   */
  private void assertPermanent(String saleId) {
    Map<String, Object> inventory = inventoryOf(saleId);
    assertThat(inventory.get("state")).isEqualTo("NEEDS_RECONCILIATION");
    assertThat(inventory.get("reason")).isNull();
    assertThat(isDone(saleId)).isTrue();

    makeDue(saleId);
    runOnce();
    assertThat(INVENTORY.decreaseCallsFor(saleId))
        .as("no retry after a permanent answer")
        .hasSize(1);
  }

  private long delayMillis(String saleId) {
    return Duration.between(Instant.now(), nextAttemptAt(saleId)).toMillis();
  }
}
