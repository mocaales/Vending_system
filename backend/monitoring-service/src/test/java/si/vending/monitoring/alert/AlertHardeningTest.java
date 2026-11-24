package si.vending.monitoring.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import si.vending.monitoring.alert.support.AlertApiTest;
import si.vending.monitoring.core.support.Http;

/** G6 findings of #25: bounds and characters that must never reach the database or the log. */
class AlertHardeningTest extends AlertApiTest {

  private final String machine = newMachine();
  private final String product = newProduct();

  private Http.Response raw(String json) {
    return post("/v1/alerts", json, inventory());
  }

  private void assertRejectedAndNothingStored(String json, String field) {
    Http.Response response = raw(json);
    assertError(response, 422, "VALIDATION_ERROR");
    assertThat(response.body()).contains(field);
    assertThat(alertsOf(machine)).isEmpty();
  }

  // --- M1: version stays within the range a JSON client can represent exactly

  @Test
  void versionAbove2Pow53MinusOneIsRejectedAndDoesNotAdvanceTheVersion() {
    assertRejectedAndNothingStored(
        reportJson(machine, 9007199254740992L, slot("A1", product, 1, 3)), "version");

    // had the huge version been stored, this lower one would be IGNORED
    Http.Response next = report(machine, 1, slot("A1", product, 1, 3));
    assertThat(next.status()).isEqualTo(201);
    assertThat(next.json()).containsEntry("outcome", "OPENED");
  }

  @Test
  void version2Pow53MinusOneIsAccepted() {
    assertThat(report(machine, 9007199254740991L, slot("A1", product, 1, 3)).status())
        .isEqualTo(201);
  }

  // --- M2: slot codes and notes are plain text

  @Test
  void slotCodeOutsideTheAllowedAlphabetIsRejected() {
    for (String code : new String[] {"..", "A 1", "A\\n1", "A\\u00001", "A/1", "A1\\u00e9"}) {
      assertRejectedAndNothingStored(reportJson(machine, 1, slot(code, product, 1, 3)), "slotCode");
    }
  }

  @Test
  void slotCodeWithLettersDigitsUnderscoreAndHyphenIsAccepted() {
    assertThat(report(machine, 1, slot("A_1-b", product, 1, 3)).status()).isEqualTo(201);
    assertThat(report(machine, 2, slot("12345678", product, 1, 3)).status()).isEqualTo(200);
  }

  @Test
  void aNoteWithControlCharactersIs422AndTheAlertStaysActive() {
    for (String note : new String[] {"a\\u0000b", "a\\u0001b", "a\\u0007b", "a\\u001bb"}) {
      String id = seedActive(machine, "OFFLINE");

      Http.Response response = resolve(id, "{\"note\":\"" + note + "\"}");

      assertError(response, 422, "VALIDATION_ERROR");
      assertThat(response.body()).contains("note");
      assertThat(find(machine, id)).containsEntry("status", "ACTIVE");
      jdbc.update("DELETE FROM alert WHERE id = ?::uuid", id);
    }
  }

  @Test
  void aNoteWithNewlineAndTabIsAccepted() {
    String id = seedActive(machine, "DOOR_OPEN");

    Http.Response response = resolve(id, "{\"note\":\"line one\\n\\tline two\"}");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().get("resolutionNote")).isEqualTo("line one\n\tline two");
  }

  // --- M3: one entry per slot

  @Test
  void aDuplicateSlotCodeInOneReportIs422() {
    assertRejectedAndNothingStored(
        reportJson(machine, 1, slot("A1", product, 1, 3), slot("A1", newProduct(), 2, 3)), "slots");
  }

  // --- M4: counts are bounded

  @Test
  void quantityAbove1000IsRejected() {
    assertRejectedAndNothingStored(
        reportJson(machine, 1, slot("A1", product, 1001, 3)), "quantity");
  }

  @Test
  void lowStockThresholdAbove1000IsRejected() {
    assertRejectedAndNothingStored(
        reportJson(machine, 1, slot("A1", product, 1, 1001)), "lowStockThreshold");
  }

  @Test
  void quantityAndThresholdOf1000AreAccepted() {
    assertThat(report(machine, 1, slot("A1", product, 1000, 1000)).status()).isEqualTo(201);
  }
}
