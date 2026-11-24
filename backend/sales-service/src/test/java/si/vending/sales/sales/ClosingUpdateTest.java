package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.ClosingTest;

/**
 * #55: PUT /v1/sales/closings/{id}. On a DRAFT it replaces the note and recomputes the totals;
 * status CONFIRMED confirms (terminal) after a last recomputation, only while no sale in scope is
 * NEEDS_RECONCILIATION. A confirmed closing accepts the identical body (200, unchanged) and nothing
 * else (409 CLOSING_CONFIRMED); later sales never change its totals.
 */
class ClosingUpdateTest extends ClosingTest {

  private static final String RECONCILE_NOTE = "Stock corrected by hand";

  // --- DRAFT: replace note, recompute

  @Test
  void aDraftUpdateReplacesTheNoteAndRecomputesTheTotals() {
    UUID machine = UUID.randomUUID();
    saleOnYesterday(machine, 1, "2.50", "EUR");
    Http.Response createdResponse = createClosing(in(yesterday(), machine, "first"), operator());
    assertThat(createdResponse.status()).as(createdResponse.body()).isEqualTo(201);
    Map<String, Object> created = createdResponse.json();
    assertThat(eur(created)).containsEntry("salesCount", 1);
    saleOnYesterday(machine, 3, "1.00", "EUR"); // arrives after the closing was created

    Http.Response response = recompute(created.get("id"), "second");

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    Map<String, Object> out = response.json();
    assertClosingShape(out);
    assertThat(out.get("status")).isEqualTo("DRAFT");
    assertThat(out.get("note")).isEqualTo("second");
    assertThat(eur(out))
        .containsEntry("salesCount", 2)
        .containsEntry("quantity", 4)
        .containsEntry("turnover", "5.50");
    assertThat(out.get("id")).isEqualTo(created.get("id"));
    assertThat(out.get("date")).isEqualTo(created.get("date"));
    assertThat(out.get("machineId")).isEqualTo(machine.toString());
    assertThat(out.get("createdAt")).isEqualTo(created.get("createdAt"));
    assertThat(out.get("createdBy")).isEqualTo(OPERATOR_ID);
    assertThat(Instant.parse((String) out.get("updatedAt")))
        .isAfterOrEqualTo(Instant.parse((String) created.get("updatedAt")));
    assertThat(out.get("confirmedAt")).isNull();
    assertThat(getClosing(created.get("id")).json()).isEqualTo(out);
  }

  @Test
  void aDraftUpdateRecomputesOpenReconciliations() {
    UUID machine = UUID.randomUUID();
    Map<String, Object> created = created(machine);
    assertThat(created.get("openReconciliations")).isEqualTo(0);
    needsReconciliationOnYesterday(machine);

    Map<String, Object> out = recompute(created.get("id"), "again").json();

    assertThat(out.get("openReconciliations")).isEqualTo(1);
    assertThat(out.get("status")).isEqualTo("DRAFT");
  }

  @Test
  void aDraftUpdateCanBeRepeated() {
    Map<String, Object> created = created(UUID.randomUUID());

    assertThat(recompute(created.get("id"), "a").status()).isEqualTo(200);
    Http.Response again = recompute(created.get("id"), "b");

    assertThat(again.status()).isEqualTo(200);
    assertThat(again.json().get("note")).isEqualTo("b");
  }

  // --- confirm

  @Test
  void confirmingSetsConfirmedAtAndConfirmedByAfterALastRecompute() {
    UUID machine = UUID.randomUUID();
    saleOnYesterday(machine, 1, "2.50", "EUR");
    Map<String, Object> created = created(machine);
    saleOnYesterday(machine, 2, "1.25", "EUR"); // after the draft, before the confirmation

    Http.Response response = confirm(created.get("id"), "final");

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    Map<String, Object> out = response.json();
    assertClosingShape(out);
    assertThat(out.get("status")).isEqualTo("CONFIRMED");
    assertThat(out.get("note")).isEqualTo("final");
    assertThat(out.get("confirmedBy")).isEqualTo(OPERATOR_ID);
    assertThat(Instant.parse((String) out.get("confirmedAt")))
        .isBetween(Instant.now().minusSeconds(30), Instant.now().plusSeconds(5));
    assertThat(eur(out))
        .containsEntry("salesCount", 2)
        .containsEntry("quantity", 3)
        .containsEntry("turnover", "5.00");
    assertThat(out.get("openReconciliations")).isEqualTo(0);
    assertThat(getClosing(created.get("id")).json()).isEqualTo(out);
  }

  @Test
  void confirmingWithoutANoteKeepsTheClosingValid() {
    Map<String, Object> created = created(UUID.randomUUID());

    Http.Response response = confirm(created.get("id"), null);

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    assertThat(response.json().get("status")).isEqualTo("CONFIRMED");
  }

  @Test
  void confirmingWithAnOpenReconciliationIs409AndTheClosingStaysADraft() {
    UUID machine = UUID.randomUUID();
    needsReconciliationOnYesterday(machine);
    Map<String, Object> created = created(machine);
    assertThat(created.get("openReconciliations")).isEqualTo(1);

    Http.Response response = confirm(created.get("id"), "try");

    assertError(response, 409, "CLOSING_HAS_OPEN_RECONCILIATION");
    Map<String, Object> after = getClosing(created.get("id")).json();
    assertThat(after.get("status")).isEqualTo("DRAFT");
    assertThat(after.get("confirmedAt")).isNull();
    assertThat(after.get("confirmedBy")).isNull();
    assertThat(after.get("note")).isEqualTo(created.get("note"));
  }

  @Test
  void theOpenCheckUsesTheRecomputeNotTheStoredCount() {
    UUID machine = UUID.randomUUID();
    Map<String, Object> created = created(machine);
    assertThat(created.get("openReconciliations")).isEqualTo(0);
    needsReconciliationOnYesterday(machine); // appears after the draft was made

    assertError(confirm(created.get("id"), null), 409, "CLOSING_HAS_OPEN_RECONCILIATION");
    assertThat(getClosing(created.get("id")).json().get("status")).isEqualTo("DRAFT");
  }

  @Test
  void aReconciliationOfTheLastOpenSaleLetsTheConfirmationThrough() {
    UUID machine = UUID.randomUUID();
    Fx open = needsReconciliationOnYesterday(machine);
    Map<String, Object> created = created(machine);
    assertError(confirm(created.get("id"), null), 409, "CLOSING_HAS_OPEN_RECONCILIATION");

    Http.Response resolved =
        putRaw(
            "/v1/sales/" + open.saleId() + "/reconciliation",
            "{\"resolution\":\"ADJUSTED\",\"note\":\"" + RECONCILE_NOTE + "\"}",
            operator());
    assertThat(resolved.status()).as(resolved.body()).isEqualTo(200);
    Http.Response response = confirm(created.get("id"), null);

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    assertThat(response.json().get("status")).isEqualTo("CONFIRMED");
    assertThat(response.json().get("openReconciliations")).isEqualTo(0);
    assertThat(eur(response.json())).containsEntry("salesCount", 1);
  }

  // --- confirmed is final

  @Test
  void theIdenticalBodyOnAConfirmedClosingIs200AndChangesNothing() {
    UUID machine = UUID.randomUUID();
    saleOnYesterday(machine, 1, "2.50", "EUR");
    Map<String, Object> created = created(machine);
    Map<String, Object> confirmed = confirm(created.get("id"), "done").json();

    Http.Response repeat = confirm(created.get("id"), "done");

    assertThat(repeat.status()).as(repeat.body()).isEqualTo(200);
    assertThat(repeat.json()).isEqualTo(confirmed);
    assertThat(getClosing(created.get("id")).json()).isEqualTo(confirmed);
  }

  @Test
  void anyOtherBodyOnAConfirmedClosingIs409ClosingConfirmed() {
    Map<String, Object> created = created(UUID.randomUUID());
    Map<String, Object> confirmed = confirm(created.get("id"), "done").json();

    assertError(confirm(created.get("id"), "other note"), 409, "CLOSING_CONFIRMED");
    assertError(recompute(created.get("id"), "done"), 409, "CLOSING_CONFIRMED");
    assertError(recompute(created.get("id"), "other note"), 409, "CLOSING_CONFIRMED");
    assertThat(getClosing(created.get("id")).json()).isEqualTo(confirmed);
  }

  @Test
  void aLaterSaleNeverChangesConfirmedTotals() {
    UUID machine = UUID.randomUUID();
    saleOnYesterday(machine, 1, "2.50", "EUR");
    Map<String, Object> created = created(machine);
    Map<String, Object> confirmed = confirm(created.get("id"), "done").json();

    saleOnYesterday(machine, 5, "9.00", "EUR");
    needsReconciliationOnYesterday(machine);

    Map<String, Object> after = getClosing(created.get("id")).json();
    assertThat(after).isEqualTo(confirmed);
    assertThat(eur(after)).containsEntry("salesCount", 1).containsEntry("turnover", "2.50");
    assertThat(after.get("openReconciliations")).isEqualTo(0);
    // neither does an identical repeat recompute it
    assertThat(confirm(created.get("id"), "done").json()).isEqualTo(confirmed);
    assertThat(asOperator("/v1/sales/closings?machineId=" + machine).items()).hasSize(1);
  }

  // --- lookup and validation

  @Test
  void anUnknownClosingIs404() {
    assertError(
        putClosing(UUID.randomUUID(), update("DRAFT", null), operator()), 404, "CLOSING_NOT_FOUND");
  }

  @Test
  void aMalformedIdIs400() {
    assertError(
        putRaw("/v1/sales/closings/not-a-uuid", "{\"status\":\"DRAFT\"}", operator()),
        400,
        "BAD_REQUEST");
  }

  @Test
  void aMissingOrUnknownStatusIs422() {
    Map<String, Object> created = created(UUID.randomUUID());
    String path = "/v1/sales/closings/" + created.get("id");

    assertInvalidField(putRaw(path, "{\"note\":\"x\"}", operator()), "status");
    assertInvalidField(putRaw(path, "{\"status\":\"DONE\"}", operator()), "status");
    assertInvalidField(putRaw(path, "{\"status\":null}", operator()), "status");
    assertThat(getClosing(created.get("id")).json()).isEqualTo(created);
  }

  @Test
  void theNoteIsLimitedTo500CharactersAndNulIs422Not500() {
    Map<String, Object> created = created(UUID.randomUUID());
    String path = "/v1/sales/closings/" + created.get("id");

    assertInvalidField(
        putClosing(created.get("id"), update("DRAFT", "n".repeat(501)), operator()), "note");
    assertInvalidField(
        putClosing(created.get("id"), update("DRAFT", "a\u0000b"), operator()), "note");
    assertThat(getClosing(created.get("id")).json()).isEqualTo(created);

    Http.Response ok = putClosing(created.get("id"), update("DRAFT", "n".repeat(500)), operator());
    assertThat(ok.status()).as(path).isEqualTo(200);
  }

  @Test
  void unknownFieldsAndUnreadableJsonAreRejected() {
    Map<String, Object> created = created(UUID.randomUUID());
    String path = "/v1/sales/closings/" + created.get("id");
    Map<String, Object> body = update("DRAFT", null);
    body.put("totals", java.util.List.of());

    assertInvalidField(putClosing(created.get("id"), body, operator()), "totals");
    for (String raw : new String[] {"{", "not json", "[]", ""}) {
      assertThat(putRaw(path, raw, operator()).status()).as(raw).isEqualTo(400);
    }
    assertThat(getClosing(created.get("id")).json()).isEqualTo(created);
  }

  @Test
  void validationIsCheckedBeforeTheConfirmedState() {
    Map<String, Object> created = created(UUID.randomUUID());
    confirm(created.get("id"), "done");

    assertInvalidField(
        putClosing(created.get("id"), update("CONFIRMED", "n".repeat(501)), operator()), "note");
  }
}
