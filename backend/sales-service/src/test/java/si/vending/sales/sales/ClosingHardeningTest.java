package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.ClosingTest;

/**
 * #55 G6 M1, L3 and L4: the largest possible totals never overflow the totals column, a note is
 * counted in code points and refuses lone surrogates, and the list refuses absurd date bounds.
 *
 * <p>Choices: closings allow an empty note and a whitespace-only note (openapi.yaml {@code
 * ClosingIn.note} has maxLength 500 and no minLength or pattern).
 */
class ClosingHardeningTest extends ClosingTest {

  private static final String MAX_PRICE = "9999999999.99";

  // --- M1: turnover of the largest legal sales (2 x 100 x 9999999999.99)

  @Test
  void theLargestPossibleTurnoverIsStoredExactlyOnCreateAndRecompute() {
    UUID machine = UUID.randomUUID();
    saleOnYesterday(machine, 100, MAX_PRICE, "EUR");
    saleOnYesterday(machine, 100, MAX_PRICE, "EUR");

    Http.Response created = create(machine);

    assertThat(created.status()).as(created.body()).isEqualTo(201);
    assertThat(eur(created.json()))
        .containsEntry("salesCount", 2)
        .containsEntry("quantity", 200)
        .containsEntry("turnover", "1999999999998.00");

    saleOnYesterday(machine, 1, "0.01", "EUR");
    Http.Response recomputed = recompute(created.json().get("id"), null);

    assertThat(recomputed.status()).as(recomputed.body()).isEqualTo(200);
    assertThat(eur(recomputed.json())).containsEntry("turnover", "1999999999998.01");
    assertThat(getClosing(created.json().get("id")).json()).isEqualTo(recomputed.json());
  }

  // --- L3: note

  private static String raw(String date, UUID machine, String escapedNote) {
    return "{\"date\":\""
        + date
        + "\",\"machineId\":\""
        + machine
        + "\",\"note\":\""
        + escapedNote
        + "\"}";
  }

  @Test
  void aLoneSurrogateInTheNoteOfAPostIs422Not500() {
    for (String escaped : List.of("a\\ud800b", "\\udc00", "x\\ud800")) {
      UUID machine = UUID.randomUUID();
      Http.Response response =
          post("/v1/sales/closings", raw(yesterday().toString(), machine, escaped), operator());

      assertInvalidField(response, "note");
      assertThat(asOperator("/v1/sales/closings?machineId=" + machine).json().get("total"))
          .as(escaped)
          .isEqualTo(0);
    }
  }

  @Test
  void aLoneSurrogateInTheNoteOfAPutIs422AndKeepsTheStoredNote() {
    Map<String, Object> closing =
        createClosing(in(yesterday(), UUID.randomUUID(), "keep"), operator()).json();

    for (String escaped : List.of("a\\ud800b", "\\udc00")) {
      Http.Response response =
          putRaw(
              "/v1/sales/closings/" + closing.get("id"),
              "{\"note\":\"" + escaped + "\",\"status\":\"DRAFT\"}",
              operator());

      assertInvalidField(response, "note");
    }
    assertThat(getClosing(closing.get("id")).json().get("note")).isEqualTo("keep");
  }

  @Test
  void aNoteOf500CodePointsIsAcceptedAnd501CodePointsIs422() {
    String ok = "😀".repeat(500);
    Http.Response accepted = createClosing(in(yesterday(), UUID.randomUUID(), ok), operator());
    assertThat(accepted.status()).as(accepted.body()).isEqualTo(201);
    assertThat(accepted.json().get("note")).isEqualTo(ok);

    Http.Response updated = putClosing(accepted.json().get("id"), update("DRAFT", ok), operator());
    assertThat(updated.status()).as(updated.body()).isEqualTo(200);

    String tooLong = "😀".repeat(501);
    assertInvalidField(
        createClosing(in(yesterday(), UUID.randomUUID(), tooLong), operator()), "note");
    assertInvalidField(
        putClosing(accepted.json().get("id"), update("DRAFT", tooLong), operator()), "note");
    assertThat(getClosing(accepted.json().get("id")).json().get("note")).isEqualTo(ok);
  }

  @Test
  void anEmptyAndAWhitespaceOnlyNoteAreAllowed() {
    for (String note : List.of("", "   ", "\t \n")) {
      Http.Response response = createClosing(in(yesterday(), UUID.randomUUID(), note), operator());
      assertThat(response.status()).as(response.body()).isEqualTo(201);
      assertThat(response.json().get("note")).isEqualTo(note);
    }
  }

  // --- L4: list bounds

  @Test
  void aListBoundOutsideTheSupportedRangeIs422Not500() {
    for (String bad :
        List.of("1999-12-31", "0001-01-01", "%2B5874898-01-01", "2100-01-02", "9999-12-31")) {
      for (String param : List.of("from", "to")) {
        Http.Response response = asOperator("/v1/sales/closings?" + param + "=" + bad);

        assertError(response, 422, "VALIDATION_ERROR");
      }
    }
  }

  @Test
  void theSupportedRangeBoundsThemselvesAreAccepted() {
    for (String ok : List.of("2020-01-01", "2100-01-01")) {
      assertThat(asOperator("/v1/sales/closings?from=" + ok).status())
          .as("from " + ok)
          .isEqualTo(200);
      assertThat(asOperator("/v1/sales/closings?to=" + ok).status()).as("to " + ok).isEqualTo(200);
    }
    assertThat(asOperator("/v1/sales/closings?from=2020-01-01&to=2100-01-01").status())
        .isEqualTo(200);
  }
}
