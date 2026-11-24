package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.ClosingTest;

/**
 * #55: POST /v1/sales/closings creates a DRAFT closing of the UTC day before today, with totals of
 * the sales whose occurredAt lies in that day [00:00:00.000, next 00:00:00.000), for one machine or
 * all machines. Sales does not call Machine: an unknown machineId is a DRAFT with empty totals.
 */
class ClosingCreateTest extends ClosingTest {

  @Test
  void createsADraftWithTheTotalsOfTheMachineOnTheDay() {
    UUID machine = UUID.randomUUID();
    saleOnYesterday(machine, 2, "2.50", "EUR");
    saleOnYesterday(machine, 1, "3.10", "EUR");
    saleOnYesterday(machine, 3, "1.00", "USD");

    Http.Response response = createClosing(in(yesterday(), machine, "end of day"), operator());

    assertThat(response.status()).as(response.body()).isEqualTo(201);
    Map<String, Object> out = response.json();
    assertClosingShape(out);
    assertThat(out.get("status")).isEqualTo("DRAFT");
    assertThat(out.get("date")).isEqualTo(yesterday().toString());
    assertThat(out.get("machineId")).isEqualTo(machine.toString());
    assertThat(out.get("note")).isEqualTo("end of day");
    assertThat(out.get("createdBy")).isEqualTo(OPERATOR_ID);
    assertThat(Instant.parse((String) out.get("createdAt")))
        .isBetween(Instant.now().minusSeconds(30), Instant.now().plusSeconds(5));
    assertThat(out.get("confirmedAt")).isNull();
    assertThat(out.get("confirmedBy")).isNull();
    assertThat(out.get("openReconciliations")).isEqualTo(0);
    assertThat(totalsOf(out)).hasSize(2);
    assertThat(eur(out))
        .containsEntry("salesCount", 2)
        .containsEntry("quantity", 3)
        .containsEntry("turnover", "8.10");
    Map<String, Object> usd =
        totalsOf(out).stream()
            .filter(t -> "USD".equals(t.get("currency")))
            .findFirst()
            .orElseThrow();
    assertThat(usd)
        .containsEntry("salesCount", 1)
        .containsEntry("quantity", 3)
        .containsEntry("turnover", "3.00");
    assertThat(getClosing(out.get("id")).json()).isEqualTo(out);
  }

  @Test
  void noteDefaultsToAnEmptyString() {
    Map<String, Object> out = created(UUID.randomUUID());

    assertThat(out.get("note")).isEqualTo("");
  }

  @Test
  void theDayIsAUtcDayWithInclusiveStartAndExclusiveEnd() {
    UUID machine = UUID.randomUUID();
    LocalDate day = yesterday();
    saleAt(machine, dayStart(day).minusMillis(1)); // before: out
    saleAt(machine, dayStart(day)); // 00:00:00.000: in
    saleAt(machine, dayEnd(day).minusMillis(1)); // 23:59:59.999: in
    saleAt(machine, dayEnd(day)); // next day 00:00:00.000: out

    Map<String, Object> out = created(machine);

    assertThat(eur(out)).containsEntry("salesCount", 2).containsEntry("turnover", "5.00");
  }

  @Test
  void otherMachinesAndOtherDaysAreNotCounted() {
    UUID machine = UUID.randomUUID();
    saleOnYesterday(UUID.randomUUID(), 1, "9.00", "EUR");
    saleAt(machine, dayStart(yesterday()).minus(java.time.Duration.ofDays(1)).plusSeconds(60));
    saleOnYesterday(machine, 1, "2.50", "EUR");

    Map<String, Object> out = created(machine);

    assertThat(totalsOf(out)).hasSize(1);
    assertThat(eur(out)).containsEntry("salesCount", 1).containsEntry("turnover", "2.50");
  }

  @Test
  void salesOfEveryInventoryStateAreCountedAndOnlyOpenOnesAreOpenReconciliations() {
    UUID machine = UUID.randomUUID();
    Instant at = dayStart(yesterday()).plusSeconds(5000);
    insert(machine, UUID.randomUUID(), 1, "1.00", "EUR", at, "APPLIED", null);
    insert(
        machine,
        UUID.randomUUID(),
        1,
        "1.00",
        "EUR",
        at,
        "NEEDS_RECONCILIATION",
        "INSUFFICIENT_STOCK");
    insert(
        machine,
        UUID.randomUUID(),
        1,
        "1.00",
        "EUR",
        at,
        "NEEDS_RECONCILIATION",
        "PRODUCT_MISMATCH");
    insert(machine, UUID.randomUUID(), 1, "1.00", "EUR", at, "RESOLVED", "INSUFFICIENT_STOCK");
    insert(machine, UUID.randomUUID(), 1, "1.00", "EUR", at, "PENDING", null);
    // outside the scope: another day and another machine
    insert(
        machine,
        UUID.randomUUID(),
        1,
        "1.00",
        "EUR",
        dayEnd(yesterday()),
        "NEEDS_RECONCILIATION",
        "INSUFFICIENT_STOCK");
    needsReconciliationOnYesterday(UUID.randomUUID());

    Map<String, Object> out = created(machine);

    assertThat(out.get("openReconciliations")).isEqualTo(2);
    assertThat(eur(out)).containsEntry("salesCount", 5).containsEntry("turnover", "5.00");
  }

  @Test
  void aMachineWithoutSalesGivesAnEmptyDraftAndSalesDoesNotCallMachine() {
    UUID unknownMachine = UUID.randomUUID();

    Http.Response response = create(unknownMachine);

    assertThat(response.status()).as(response.body()).isEqualTo(201);
    Map<String, Object> out = response.json();
    assertThat(out.get("status")).isEqualTo("DRAFT");
    assertThat(out.get("machineId")).isEqualTo(unknownMachine.toString());
    assertThat(totalsOf(out)).isEmpty();
    assertThat(out.get("openReconciliations")).isEqualTo(0);
    assertThat(PRODUCT.calls()).isEmpty();
    assertThat(INVENTORY.calls()).isEmpty();
  }

  @Test
  void withoutMachineIdTheClosingCoversAllMachines() {
    saleOnYesterday(UUID.randomUUID(), 2, "2.50", "EUR");
    saleOnYesterday(UUID.randomUUID(), 1, "4.00", "EUR");
    needsReconciliationOnYesterday(UUID.randomUUID());

    Http.Response response = create(null);

    assertThat(response.status()).as(response.body()).isEqualTo(201);
    Map<String, Object> out = response.json();
    assertClosingShape(out);
    assertThat(out.get("machineId")).isNull();
    Map<String, List<Object>> expected = oracle(yesterday());
    assertThat(totalsOf(out)).hasSameSizeAs(expected.keySet());
    for (Map<String, Object> total : totalsOf(out)) {
      List<Object> e = expected.get((String) total.get("currency"));
      assertThat(e).isNotNull();
      assertThat(total.get("salesCount")).isEqualTo(e.get(0));
      assertThat(total.get("quantity")).isEqualTo(e.get(1));
      assertThat(total.get("turnover")).isEqualTo(e.get(2));
    }
    assertThat((Integer) eur(out).get("salesCount")).isGreaterThanOrEqualTo(3);
    assertThat(out.get("openReconciliations")).isEqualTo(oracleOpen(yesterday()));
    assertThat((Integer) out.get("openReconciliations")).isGreaterThanOrEqualTo(1);
  }

  @Test
  void anExplicitNullMachineIdMeansAllMachines() {
    Map<String, Object> body = in(yesterday(), null, null);
    body.put("machineId", null);

    Http.Response response = createClosing(body, operator());

    assertThat(response.status()).as(response.body()).isEqualTo(201);
    assertThat(response.json().get("machineId")).isNull();
  }

  // --- one closing per (date, machineId)

  @Test
  void aSecondClosingOfTheSameMachineAndDateIs409AndNotIdempotent() {
    UUID machine = UUID.randomUUID();
    Map<String, Object> first = created(machine);

    Http.Response second = create(machine);

    assertError(second, 409, "CLOSING_EXISTS");
    assertThat(getClosing(first.get("id")).json()).isEqualTo(first);
    assertThat(asOperator("/v1/sales/closings?machineId=" + machine).json().get("total"))
        .isEqualTo(1);
  }

  @Test
  void aSecondAllMachinesClosingOfTheSameDateIs409() {
    created(null);

    assertError(create(null), 409, "CLOSING_EXISTS");
  }

  @Test
  void allMachinesAndASpecificMachineAreDifferentPairs() {
    UUID machine = UUID.randomUUID();
    UUID other = UUID.randomUUID();

    created(null);
    created(machine);
    created(other);

    assertError(create(null), 409, "CLOSING_EXISTS");
    assertError(create(machine), 409, "CLOSING_EXISTS");
  }

  @Test
  void aConfirmedClosingStillBlocksANewOneForThePair() {
    UUID machine = UUID.randomUUID();
    Map<String, Object> first = created(machine);
    assertThat(confirm(first.get("id"), null).status()).isEqualTo(200);

    assertError(create(machine), 409, "CLOSING_EXISTS");
  }

  // --- date rules

  @Test
  void onlyYesterdayIsAcceptedTodayTwoDaysAgoAndTheFutureAre422() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (LocalDate bad :
        List.of(
            today,
            today.minusDays(2),
            today.minusYears(1),
            today.plusDays(1),
            today.plusYears(1),
            LocalDate.of(2099, 12, 31))) {
      UUID machine = UUID.randomUUID();
      Http.Response response = createClosing(in(bad, machine, null), operator());
      assertInvalidField(response, "date");
      assertThat(asOperator("/v1/sales/closings?machineId=" + machine).json().get("total"))
          .as(bad.toString())
          .isEqualTo(0);
    }
  }

  @Test
  void aMissingOrNullDateIs422() {
    UUID machine = UUID.randomUUID();
    assertInvalidField(
        post("/v1/sales/closings", "{\"machineId\":\"" + machine + "\"}", operator()), "date");
    assertInvalidField(
        post("/v1/sales/closings", "{\"date\":null,\"machineId\":\"" + machine + "\"}", operator()),
        "date");
  }

  @Test
  void anUnparseableDateIs400Or422AndCreatesNothing() {
    UUID machine = UUID.randomUUID();
    for (String bad :
        new String[] {
          "\"yesterday\"",
          "\"2026-13-45\"",
          "\"23.11.2026\"",
          "\"" + yesterday() + "T00:00:00Z\"",
          "20261123",
          "true"
        }) {
      Http.Response response =
          post(
              "/v1/sales/closings",
              "{\"date\":" + bad + ",\"machineId\":\"" + machine + "\"}",
              operator());
      assertThat(response.status()).as(bad).isIn(400, 422);
      assertThat(response.json().get("code")).as(bad).isIn("BAD_REQUEST", "VALIDATION_ERROR");
    }
    assertThat(asOperator("/v1/sales/closings?machineId=" + machine).json().get("total"))
        .isEqualTo(0);
  }

  // --- other fields

  @Test
  void aNoteOf500CharactersIsAcceptedAnd501Is422() {
    String ok = "n".repeat(500);
    Http.Response accepted = createClosing(in(yesterday(), UUID.randomUUID(), ok), operator());
    assertThat(accepted.status()).as(accepted.body()).isEqualTo(201);
    assertThat(accepted.json().get("note")).isEqualTo(ok);

    UUID machine = UUID.randomUUID();
    assertInvalidField(
        createClosing(in(yesterday(), machine, "n".repeat(501)), operator()), "note");
    assertThat(asOperator("/v1/sales/closings?machineId=" + machine).json().get("total"))
        .isEqualTo(0);
  }

  @Test
  void aNulCharacterInTheNoteIs422Not500() {
    UUID machine = UUID.randomUUID();

    assertInvalidField(
        createClosing(in(yesterday(), machine, "bad\u0000note"), operator()), "note");
    assertThat(asOperator("/v1/sales/closings?machineId=" + machine).json().get("total"))
        .isEqualTo(0);
  }

  @Test
  void aMalformedMachineIdIs400Or422() {
    Http.Response response =
        post(
            "/v1/sales/closings",
            "{\"date\":\"" + yesterday() + "\",\"machineId\":\"not-a-uuid\"}",
            operator());

    assertThat(response.status()).isIn(400, 422);
  }

  @Test
  void unknownFieldsAreRejected() {
    UUID machine = UUID.randomUUID();
    Map<String, Object> body = in(yesterday(), machine, null);
    body.put("status", "CONFIRMED");

    assertInvalidField(createClosing(body, operator()), "status");
    assertThat(asOperator("/v1/sales/closings?machineId=" + machine).json().get("total"))
        .isEqualTo(0);
  }

  @Test
  void unreadableJsonIs400() {
    for (String raw : new String[] {"{", "not json", "[]", ""}) {
      assertThat(post("/v1/sales/closings", raw, operator()).status()).as(raw).isEqualTo(400);
    }
  }

  @Test
  void createdByComesFromTheOperatorHeaderNotFromTheBody() {
    Map<String, Object> body = in(yesterday(), UUID.randomUUID(), null);
    body.put("createdBy", UUID.randomUUID().toString());

    assertInvalidField(createClosing(body, operator()), "createdBy");
    Map<String, Object> out = created(UUID.randomUUID());
    assertThat(out.get("createdBy")).isEqualTo(OPERATOR_ID);
  }

  @Test
  void aWholeClosingFlowMakesNoOutgoingCall() {
    UUID machine = UUID.randomUUID();
    saleOnYesterday(machine, 1, "2.50", "EUR");

    Map<String, Object> out = created(machine);
    asOperator("/v1/sales/closings");
    getClosing(out.get("id"));
    recompute(out.get("id"), "n");
    confirm(out.get("id"), "n");
    deleteClosing(out.get("id"), operator());
    deleteDate(yesterday().toString(), operator());

    assertThat(PRODUCT.calls()).isEmpty();
    assertThat(INVENTORY.calls()).isEmpty();
  }
}
