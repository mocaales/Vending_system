package si.vending.machine.machine;

import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineApiTest;
import si.vending.machine.machine.support.MachineFixtures;

/**
 * #17 security review, findings F3 and F4 (input hardening of machine create and list).
 *
 * <p>F3: a {@code null} element inside {@code slots} must be a validation error on that element
 * ({@code slots[i]}), not an unhandled NullPointerException (500).
 *
 * <p>F4: control characters (including NUL, sent as the JSON escape {@code \u0000}) must be
 * rejected as validation errors. PostgreSQL refuses NUL in text, which Spring reports as a
 * DataIntegrityViolation; that must never be mapped to MACHINE_CODE_TAKEN (409).
 *
 * <p>Status for {@code q} with a control character: 422 VALIDATION_ERROR, the same status and code
 * as every other constraint violation on a query parameter (limit 0, offset -1, q longer than 100,
 * pinned in MachineReadTest and PaginationTest). 400 would be reserved for values that cannot be
 * parsed at all (BAD_REQUEST), which a string never is.
 */
class MachineInputHardeningTest extends MachineApiTest {

  private static String machineJson(String code, String name, String address, String slots) {
    return """
    {"code":"%s","name":"%s","address":"%s","latitude":46.5547,"longitude":15.6459,\
    "slots":%s}\
    """
        .formatted(code, name, address, slots);
  }

  // --- F3: null elements in slots

  @Test
  void nullSlotElementIsValidationErrorNotServerError() {
    String json = machineJson("T" + MachineFixtures.tag(), "Name", "Address", "[null]");

    Http.Response response = createMachine(json);

    assertError(response, 422, "VALIDATION_ERROR");
    assertInvalidField(response, "slots[0]");
  }

  @Test
  void nullSlotAmongValidSlotsIsValidationErrorOnItsIndex() {
    String json =
        machineJson("T" + MachineFixtures.tag(), "Name", "Address", "[{\"slotCode\":\"A1\"},null]");

    Http.Response response = createMachine(json);

    assertError(response, 422, "VALIDATION_ERROR");
    assertInvalidField(response, "slots[1]");
  }

  // --- F4: control characters in create

  @Test
  void nulInCodeIsValidationErrorNotCodeTaken() {
    String json =
        machineJson(
            "T\\u0000" + MachineFixtures.tag(), "Name", "Address", "[{\"slotCode\":\"A1\"}]");

    Http.Response response = createMachine(json);

    assertError(response, 422, "VALIDATION_ERROR");
    assertInvalidField(response, "code");
  }

  @Test
  void nulInNameIsValidationError() {
    String json =
        machineJson(
            "T" + MachineFixtures.tag(), "Name\\u0000x", "Address", "[{\"slotCode\":\"A1\"}]");

    Http.Response response = createMachine(json);

    assertError(response, 422, "VALIDATION_ERROR");
    assertInvalidField(response, "name");
  }

  @Test
  void nulInAddressIsValidationError() {
    String json =
        machineJson(
            "T" + MachineFixtures.tag(), "Name", "Address\\u0000x", "[{\"slotCode\":\"A1\"}]");

    Http.Response response = createMachine(json);

    assertError(response, 422, "VALIDATION_ERROR");
    assertInvalidField(response, "address");
  }

  @Test
  void nulInSlotLabelIsValidationError() {
    String json =
        machineJson(
            "T" + MachineFixtures.tag(),
            "Name",
            "Address",
            "[{\"slotCode\":\"A1\",\"label\":\"Mle\\u0000ko\"}]");

    Http.Response response = createMachine(json);

    assertError(response, 422, "VALIDATION_ERROR");
    assertInvalidField(response, "slots");
  }

  @Test
  void otherControlCharactersAreRejectedToo() {
    String json =
        machineJson(
            "T" + MachineFixtures.tag(), "Name\\u0007bell", "Address", "[{\"slotCode\":\"A1\"}]");

    assertError(createMachine(json), 422, "VALIDATION_ERROR");
  }

  // --- F4: control characters in the list query

  @Test
  void nulInQueryIsValidationErrorNotServerError() {
    Http.Response response = get("/v1/machines?q=a%00b", operator());

    assertError(response, 422, "VALIDATION_ERROR");
  }
}
