package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.ReconciliationTest;

/**
 * #35 G6 findings on the note: lone surrogates (L1), invisible or bidi-spoofing format characters
 * (L2), the length counted in code points (L4) and whitespace-only notes (L5). Raw JSON escapes are
 * sent so that the wire form is exactly what the test names.
 */
class ReconciliationNoteTest extends ReconciliationTest {

  private static final String EMOJI = "😀";

  /**
   * Request body with the note given as already JSON-escaped text (for example {@code \\uD800}).
   */
  private static String rawNote(String escapedNote) {
    return "{\"resolution\":\"ADJUSTED\",\"note\":\"" + escapedNote + "\"}";
  }

  private void assertRejected(String escapedNote) {
    Fx sale = open();
    Http.Response response = put(path(sale.saleId()), rawNote(escapedNote), operator());
    assertInvalidField(response, "note");
    assertThat(stateOf(sale.saleId().toString())).as(escapedNote).isEqualTo("NEEDS_RECONCILIATION");
  }

  private void assertAccepted(String note) {
    Fx sale = open();
    Map<String, Object> in = body("ADJUSTED", note);
    Map<String, Object> record = reconciliationOf(resolve(sale, in, operator()));
    assertThat(record.get("note")).isEqualTo(note);
    assertThat(reconciliationOf(asOperator("/v1/sales/" + sale.saleId())).get("note"))
        .isEqualTo(note);
  }

  // --- L1: lone surrogates

  @Test
  void aLoneHighSurrogateInTheNoteIs422() {
    assertRejected("a\\uD800b");
  }

  @Test
  void aLoneLowSurrogateInTheNoteIs422() {
    assertRejected("\\uDC00");
    assertRejected("x\\uDFFF");
  }

  @Test
  void aReversedSurrogatePairInTheNoteIs422() {
    assertRejected("\\uDE00\\uD83D");
  }

  // --- L2: invisible and direction-changing characters

  @Test
  void c1ControlCharactersInTheNoteAre422() {
    assertRejected("a\\u0085b");
    assertRejected("a\\u009Bb");
    assertRejected("a\\u0080b");
    assertRejected("a\\u009Fb");
  }

  @Test
  void lineAndParagraphSeparatorsInTheNoteAre422() {
    assertRejected("a\\u2028b");
    assertRejected("a\\u2029b");
  }

  @Test
  void bidiEmbeddingAndOverrideCharactersInTheNoteAre422() {
    for (char c = '‪'; c <= '‮'; c++) {
      assertRejected("a\\u" + Integer.toHexString(c).toUpperCase() + "b");
    }
  }

  @Test
  void bidiIsolateCharactersInTheNoteAre422() {
    for (char c = '⁦'; c <= '⁩'; c++) {
      assertRejected("a\\u" + Integer.toHexString(c).toUpperCase() + "b");
    }
  }

  @Test
  void newlineAndTabInTheNoteStayAccepted() {
    assertAccepted("line one\nline two\twith a tab");
  }

  @Test
  void ordinaryUnicodeInTheNoteStaysAccepted() {
    assertAccepted("Popravek zaloge: čšž ČŠŽ ćđ, 5 €");
    assertAccepted("Done " + EMOJI + " " + "🥚");
  }

  // --- L4: the length is counted in code points

  @Test
  void fiveHundredEmojiAreAcceptedAndStoredIntact() {
    String note = EMOJI.repeat(500);
    assertThat(note.length()).isEqualTo(1000);
    assertThat(note.codePointCount(0, note.length())).isEqualTo(500);

    Fx sale = open();
    Http.Response response = resolve(sale, body("ADJUSTED", note), operator());

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    assertThat(reconciliationOf(response).get("note")).isEqualTo(note);
    assertThat(
            jdbc.queryForObject(
                "SELECT note FROM sales WHERE sale_id = ?", String.class, sale.saleId()))
        .isEqualTo(note);
  }

  @Test
  void fiveHundredAndOneEmojiAre422() {
    Fx sale = open();
    Http.Response response = resolve(sale, body("ADJUSTED", EMOJI.repeat(501)), operator());

    assertInvalidField(response, "note");
    assertThat(stateOf(sale.saleId().toString())).isEqualTo("NEEDS_RECONCILIATION");
  }

  @Test
  void fiveHundredPlainCharactersStayAcceptedAndFiveHundredAndOneAreRejected() {
    assertAccepted("z".repeat(500));
    assertRejected("z".repeat(501));
  }

  // --- L5: whitespace only

  @Test
  void aWhitespaceOnlyNoteIs422() {
    assertRejected("   ");
    assertRejected("\\n\\t");
    assertRejected(" \\t \\n ");
  }

  @Test
  void aNoteWithTextAroundWhitespaceStaysAccepted() {
    assertAccepted("  padded  ");
  }
}
