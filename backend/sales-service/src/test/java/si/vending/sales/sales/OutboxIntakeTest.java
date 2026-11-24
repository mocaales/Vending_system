package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.sales.support.DependencyStub.Mode;
import si.vending.sales.sales.support.OutboxTest;

/**
 * #32 acceptance 1: the sale and its outbox entry are written in one transaction; a rejected
 * request leaves no entry; intake itself never calls decrease (only the worker does).
 */
class OutboxIntakeTest extends OutboxTest {

  @Test
  void anAcceptedSaleHasExactlyOneOpenOutboxEntry() {
    String saleId = accept(knownSlot());

    assertThat(outboxCount(saleId)).isEqualTo(1);
    assertThat(isDone(saleId)).isFalse();
    assertThat(attempts(saleId)).isZero();
    assertThat(nextAttemptAt(saleId)).isBeforeOrEqualTo(java.time.Instant.now().plusSeconds(1));
  }

  @Test
  void aRepeatedPostDoesNotAddASecondEntry() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    String saleId = accept(body);

    assertThat(postSale(body).status()).isEqualTo(200);

    assertThat(outboxCount(saleId)).isEqualTo(1);
  }

  @Test
  void aSameIdDifferentBodyConflictLeavesNoSecondEntry() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    String saleId = accept(body);
    body.put("quantity", 2);

    assertError(postSale(body), 409, "SALE_ID_CONFLICT");

    assertThat(outboxCount(saleId)).isEqualTo(1);
  }

  @Test
  void aValidationRejectionCreatesNoEntry() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    body.put("quantity", 0);

    assertThat(postSale(body).status()).isEqualTo(422);

    assertThat(outboxCount((String) body.get("saleId"))).isZero();
  }

  @Test
  void anUnknownReferenceCreatesNoEntry() {
    Setup setup = new Setup(UUID.randomUUID().toString(), "A1", UUID.randomUUID().toString());
    Map<String, Object> body = sale(setup);

    assertThat(postSale(body).status()).isEqualTo(422);

    assertThat(outboxCount((String) body.get("saleId"))).isZero();
  }

  @Test
  void aDependencyOutageDuringIntakeCreatesNoEntry() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    INVENTORY.setMode(Mode.UNAVAILABLE);

    assertError(postSale(body), 503, "DEPENDENCY_UNAVAILABLE");

    assertThat(outboxCount((String) body.get("saleId"))).isZero();
  }

  @Test
  void aWrongMachineKeyCreatesNoEntry() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    var response =
        post(
            "/v1/sales",
            json(body),
            "X-Service-Token",
            GATEWAY_TOKEN,
            "X-Machine-Id",
            UUID.randomUUID().toString());

    assertError(response, 403, "MACHINE_KEY_MISMATCH");

    assertThat(outboxCount((String) body.get("saleId"))).isZero();
  }

  @Test
  void intakeNeverCallsDecrease() {
    String saleId = accept(knownSlot());

    assertThat(INVENTORY.decreaseCalls()).as("decrease is the worker's job").isEmpty();
    assertThat(stateOf(saleId)).isEqualTo("PENDING");
  }

  @Test
  void everyStoredSaleHasAnOutboxEntry() {
    // The sale row and the outbox row are one unit: every stored sale has an entry.
    String saleId = accept(knownSlot());

    Integer withoutEntry =
        jdbc.queryForObject(
            "SELECT count(*) FROM sales s WHERE s.sale_id = ?::uuid AND NOT EXISTS "
                + "(SELECT 1 FROM sale_outbox o WHERE o.sale_id = s.sale_id)",
            Integer.class,
            saleId);
    assertThat(withoutEntry).isZero();
  }
}
