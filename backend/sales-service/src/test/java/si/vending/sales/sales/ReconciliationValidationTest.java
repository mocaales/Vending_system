package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.ReconciliationTest;

/**
 * #35: ReconciliationIn is strict (resolution enum, note 1 to 500, no unknown fields). Invalid
 * input is 422 VALIDATION_ERROR naming the field, never 500; unreadable JSON is 400. A rejected
 * request leaves the sale in NEEDS_RECONCILIATION.
 */
class ReconciliationValidationTest extends ReconciliationTest {

  private void assertRejected(Fx sale, String rawJson, String field) {
    Http.Response response = put(path(sale.saleId()), rawJson, operator());
    assertInvalidField(response, field);
    assertThat(stateOf(sale.saleId().toString())).isEqualTo("NEEDS_RECONCILIATION");
  }

  @Test
  void resolutionOutsideTheEnumIs422() {
    Fx sale = open();
    for (String bad : new String[] {"RESOLVED", "adjusted", "", "NONE"}) {
      assertRejected(sale, json(body(bad, "note")), "resolution");
    }
  }

  @Test
  void missingOrNullResolutionIs422() {
    Fx sale = open();
    assertRejected(sale, "{\"note\":\"n\"}", "resolution");
    assertRejected(sale, "{\"resolution\":null,\"note\":\"n\"}", "resolution");
  }

  @Test
  void missingNullEmptyOrTooLongNoteIs422() {
    Fx sale = open();
    assertRejected(sale, "{\"resolution\":\"ADJUSTED\"}", "note");
    assertRejected(sale, "{\"resolution\":\"ADJUSTED\",\"note\":null}", "note");
    assertRejected(sale, json(body("ADJUSTED", "")), "note");
    assertRejected(sale, json(body("ADJUSTED", "z".repeat(501))), "note");
  }

  @Test
  void nonStringNoteOrResolutionIs422Not500() {
    Fx sale = open();
    Http.Response response =
        put(path(sale.saleId()), "{\"resolution\":\"ADJUSTED\",\"note\":123}", operator());
    assertThat(response.status()).isIn(400, 422);
    assertThat(stateOf(sale.saleId().toString())).isEqualTo("NEEDS_RECONCILIATION");
  }

  @Test
  void unknownFieldIs422() {
    Fx sale = open();
    Map<String, Object> in = adjusted();
    in.put("resolvedBy", OPERATOR_ID);
    assertRejected(sale, json(in), "resolvedBy");
  }

  @Test
  void nulCharacterInTheNoteIs422Not500() {
    Fx sale = open();
    assertRejected(sale, json(body("ADJUSTED", "bad\u0000note")), "note");
  }

  @Test
  void controlCharactersOtherThanNewlineAndTabAre422() {
    Fx sale = open();
    for (String c : new String[] {"\u0001", "\u0008", "\u001b", "\u007f", "\r"}) {
      assertRejected(sale, json(body("ADJUSTED", "a" + c + "b")), "note");
    }
  }

  @Test
  void unreadableJsonIs400() {
    Fx sale = open();
    for (String raw : new String[] {"{", "not json", "[]", ""}) {
      Http.Response response = put(path(sale.saleId()), raw, operator());
      assertThat(response.status()).as(raw).isEqualTo(400);
    }
    assertThat(stateOf(sale.saleId().toString())).isEqualTo("NEEDS_RECONCILIATION");
  }

  @Test
  void validationIsCheckedBeforeTheStateTransition() {
    Fx applied = insert(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), now(), "APPLIED");
    assertInvalidField(
        put(path(applied.saleId()), json(body("NOPE", "n")), operator()), "resolution");
  }
}
