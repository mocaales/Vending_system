package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.ClosingTest;

/**
 * #55: DELETE /v1/sales/closings/{id} removes a DRAFT for good (204, repeat 404) and refuses a
 * CONFIRMED one (409 CLOSING_CONFIRMED). DELETE /v1/sales/closings?date= removes every DRAFT of the
 * date (all scopes), skips confirmed ones and answers 200 {deleted}.
 */
class ClosingDeleteTest extends ClosingTest {

  // --- by id

  @Test
  void aDraftIsDeletedWith204AndARepeatIs404() {
    Map<String, Object> created = created(UUID.randomUUID());

    Http.Response first = deleteClosing(created.get("id"), operator());

    assertThat(first.status()).isEqualTo(204);
    assertThat(first.body()).isEmpty();
    assertError(getClosing(created.get("id")), 404, "CLOSING_NOT_FOUND");
    assertError(deleteClosing(created.get("id"), operator()), 404, "CLOSING_NOT_FOUND");
  }

  @Test
  void aDeletedDraftFreesThePairForANewClosing() {
    UUID machine = UUID.randomUUID();
    Map<String, Object> created = created(machine);
    assertThat(deleteClosing(created.get("id"), operator()).status()).isEqualTo(204);

    Http.Response again = create(machine);

    assertThat(again.status()).as(again.body()).isEqualTo(201);
    assertThat(again.json().get("id")).isNotEqualTo(created.get("id"));
  }

  @Test
  void aConfirmedClosingCannotBeDeleted() {
    Map<String, Object> created = created(UUID.randomUUID());
    Map<String, Object> confirmed = confirm(created.get("id"), "done").json();

    assertError(deleteClosing(created.get("id"), operator()), 409, "CLOSING_CONFIRMED");
    assertThat(getClosing(created.get("id")).json()).isEqualTo(confirmed);
  }

  @Test
  void anUnknownIdIs404AndAMalformedIdIs400() {
    assertError(deleteClosing(UUID.randomUUID(), operator()), 404, "CLOSING_NOT_FOUND");
    assertError(deleteClosing("not-a-uuid", operator()), 400, "BAD_REQUEST");
  }

  @Test
  void deletingOneDraftLeavesTheOthers() {
    Map<String, Object> a = created(UUID.randomUUID());
    Map<String, Object> b = created(UUID.randomUUID());

    assertThat(deleteClosing(a.get("id"), operator()).status()).isEqualTo(204);

    assertThat(getClosing(b.get("id")).json()).isEqualTo(b);
  }

  // --- by date

  @Test
  void deletingByDateRemovesAllDraftsOfTheDayAndSkipsConfirmedOnes() {
    String day = yesterday().toString();
    Map<String, Object> draftA = created(UUID.randomUUID());
    Map<String, Object> draftB = created(UUID.randomUUID());
    Map<String, Object> draftAll = created(null);
    Map<String, Object> confirmed = created(UUID.randomUUID());
    Map<String, Object> confirmedOut = confirm(confirmed.get("id"), "done").json();

    Http.Response response = deleteDate(day, operator());

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    assertThat(response.json().keySet()).containsExactly("deleted");
    assertThat(response.json().get("deleted")).isEqualTo(3);
    assertError(getClosing(draftA.get("id")), 404, "CLOSING_NOT_FOUND");
    assertError(getClosing(draftB.get("id")), 404, "CLOSING_NOT_FOUND");
    assertError(getClosing(draftAll.get("id")), 404, "CLOSING_NOT_FOUND");
    assertThat(getClosing(confirmed.get("id")).json()).isEqualTo(confirmedOut);
  }

  @Test
  void repeatingTheDateDeleteReturns200WithZero() {
    created(UUID.randomUUID());
    String day = yesterday().toString();
    assertThat(deleteDate(day, operator()).json().get("deleted")).isEqualTo(1);

    Http.Response repeat = deleteDate(day, operator());

    assertThat(repeat.status()).isEqualTo(200);
    assertThat(repeat.json().get("deleted")).isEqualTo(0);
  }

  @Test
  void aDateWithoutClosingsDeletesNothingAndTouchesOtherDays() {
    Map<String, Object> draft = created(UUID.randomUUID());
    LocalDate today = LocalDate.now(ZoneOffset.UTC);

    for (LocalDate other :
        new LocalDate[] {today, yesterday().minusDays(3), LocalDate.of(2020, 1, 1)}) {
      Http.Response response = deleteDate(other.toString(), operator());
      assertThat(response.status()).as(other.toString()).isEqualTo(200);
      assertThat(response.json().get("deleted")).isEqualTo(0);
    }

    assertThat(getClosing(draft.get("id")).json()).isEqualTo(draft);
  }

  @Test
  void theDateIsRequired() {
    Map<String, Object> draft = created(UUID.randomUUID());

    assertInvalidField(deleteDate(null, operator()), "date");
    assertInvalidField(
        Http.send(port, "DELETE", "/v1/sales/closings?date=", null, operator()), "date");
    assertThat(getClosing(draft.get("id")).json()).isEqualTo(draft);
  }

  @Test
  void anUnparseableDateIs400Or422AndDeletesNothing() {
    Map<String, Object> draft = created(UUID.randomUUID());

    for (String bad :
        new String[] {"abc", "2026-13-45", "23.11.2026", yesterday() + "T00:00:00Z"}) {
      Http.Response response = deleteDate(bad, operator());
      assertThat(response.status()).as(bad).isIn(400, 422);
      assertThat(response.json().get("code")).as(bad).isIn("BAD_REQUEST", "VALIDATION_ERROR");
    }
    assertThat(getClosing(draft.get("id")).json()).isEqualTo(draft);
  }
}
