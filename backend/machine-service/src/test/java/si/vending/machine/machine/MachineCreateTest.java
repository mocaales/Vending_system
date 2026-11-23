package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineApiTest;
import si.vending.machine.machine.support.MachineFixtures;

/** #17 criterion 1: POST /v1/machines (contract: openapi.yaml createMachine, docs/03 section 4). */
class MachineCreateTest extends MachineApiTest {

  private static final String MACHINE_OUT_FIELDS =
      "id,code,name,address,latitude,longitude,status,slots,createdAt,updatedAt";

  @Test
  void createsActiveMachineWithSlotsAndReturnsKeyOnce() {
    String code = "T" + MachineFixtures.tag();
    String json =
        """
        {"code":"%s","name":"Ljubljana BTC","address":"Šmartinska 152","latitude":46.0658,\
        "longitude":14.5453,"slots":[{"slotCode":"A1","label":"Mleko"},{"slotCode":"B2"}]}\
        """
            .formatted(code);

    Http.Response response = createMachine(json);

    assertThat(response.status()).isEqualTo(201);
    Map<String, Object> body = response.json();
    assertThat(body.keySet())
        .containsExactlyInAnyOrder((MACHINE_OUT_FIELDS + ",machineKey,keyId").split(","));
    assertThat(UUID.fromString((String) body.get("id"))).isNotNull();
    assertThat(body.get("code")).isEqualTo(code);
    assertThat(body.get("name")).isEqualTo("Ljubljana BTC");
    assertThat(body.get("address")).isEqualTo("Šmartinska 152");
    assertThat(((Number) body.get("latitude")).doubleValue()).isEqualTo(46.0658);
    assertThat(((Number) body.get("longitude")).doubleValue()).isEqualTo(14.5453);
    assertThat(body.get("status")).isEqualTo("ACTIVE");
    assertThat((String) body.get("createdAt")).endsWith("Z");
    assertThat((String) body.get("updatedAt")).endsWith("Z");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> slots = (List<Map<String, Object>>) body.get("slots");
    assertThat(slots).hasSize(2);
    assertThat(slots.get(0)).containsEntry("slotCode", "A1").containsEntry("label", "Mleko");
    assertThat(slots.get(1)).containsEntry("slotCode", "B2");
    assertThat(slots.get(1).get("label")).isNull();
    assertThat((String) body.get("machineKey")).isNotBlank().doesNotContainAnyWhitespaces();
    assertThat(UUID.fromString((String) body.get("keyId"))).isNotNull();
  }

  @Test
  void keyIsNeverReturnedAgain() {
    Http.Response created =
        createMachine(MachineFixtures.createJson("T" + MachineFixtures.tag(), "A1"));
    String id = (String) created.json().get("id");

    Http.Response fetched = getMachine(id);

    assertThat(fetched.status()).isEqualTo(200);
    assertThat(fetched.json().keySet()).containsExactlyInAnyOrder(MACHINE_OUT_FIELDS.split(","));
    assertThat(fetched.body()).doesNotContain((String) created.json().get("machineKey"));
  }

  @Test
  void twoMachinesGetDifferentIdsAndKeys() {
    Http.Response a = createMachine(MachineFixtures.createJson("T" + MachineFixtures.tag(), "A1"));
    Http.Response b = createMachine(MachineFixtures.createJson("T" + MachineFixtures.tag(), "A1"));

    assertThat(a.json().get("id")).isNotEqualTo(b.json().get("id"));
    assertThat(a.json().get("machineKey")).isNotEqualTo(b.json().get("machineKey"));
    assertThat(a.json().get("keyId")).isNotEqualTo(b.json().get("keyId"));
  }

  @Test
  void duplicateCodeIsRejected() {
    String code = "T" + MachineFixtures.tag();
    assertThat(createMachine(MachineFixtures.createJson(code, "A1")).status()).isEqualTo(201);

    Http.Response second = createMachine(MachineFixtures.createJson(code, "B1"));

    assertError(second, 409, "MACHINE_CODE_TAKEN");
  }

  @Test
  void duplicateCodeDoesNotCreateAMachine() {
    String code = "T" + MachineFixtures.tag();
    createMachine(MachineFixtures.createJson(code, "A1"));
    createMachine(MachineFixtures.createJson(code, "B1"));

    Http.Response list = get("/v1/machines?q=" + code, operator());

    assertThat(list.json().get("total")).isEqualTo(1);
  }

  @ParameterizedTest(name = "invalid {0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "code|{\"code\":\"\",\"name\":\"n\",\"address\":\"a\",\"latitude\":1,\"longitude\":1,\"slots\":[{\"slotCode\":\"A1\"}]}",
        "name|{\"code\":\"c\",\"address\":\"a\",\"latitude\":1,\"longitude\":1,\"slots\":[{\"slotCode\":\"A1\"}]}",
        "address|{\"code\":\"c\",\"name\":\"n\",\"address\":\"\",\"latitude\":1,\"longitude\":1,\"slots\":[{\"slotCode\":\"A1\"}]}",
        "latitude|{\"code\":\"c\",\"name\":\"n\",\"address\":\"a\",\"latitude\":90.5,\"longitude\":1,\"slots\":[{\"slotCode\":\"A1\"}]}",
        "latitude|{\"code\":\"c\",\"name\":\"n\",\"address\":\"a\",\"latitude\":-90.1,\"longitude\":1,\"slots\":[{\"slotCode\":\"A1\"}]}",
        "longitude|{\"code\":\"c\",\"name\":\"n\",\"address\":\"a\",\"latitude\":1,\"longitude\":180.1,\"slots\":[{\"slotCode\":\"A1\"}]}",
        "longitude|{\"code\":\"c\",\"name\":\"n\",\"address\":\"a\",\"latitude\":1,\"longitude\":-181,\"slots\":[{\"slotCode\":\"A1\"}]}",
        "latitude|{\"code\":\"c\",\"name\":\"n\",\"address\":\"a\",\"longitude\":1,\"slots\":[{\"slotCode\":\"A1\"}]}",
        "slots|{\"code\":\"c\",\"name\":\"n\",\"address\":\"a\",\"latitude\":1,\"longitude\":1,\"slots\":[]}",
        "slots|{\"code\":\"c\",\"name\":\"n\",\"address\":\"a\",\"latitude\":1,\"longitude\":1}",
        "slots|{\"code\":\"c\",\"name\":\"n\",\"address\":\"a\",\"latitude\":1,\"longitude\":1,\"slots\":[{\"slotCode\":\"\"}]}",
        "slots|{\"code\":\"c\",\"name\":\"n\",\"address\":\"a\",\"latitude\":1,\"longitude\":1,\"slots\":[{\"slotCode\":\"123456789\"}]}",
        "slots|{\"code\":\"c\",\"name\":\"n\",\"address\":\"a\",\"latitude\":1,\"longitude\":1,\"slots\":[{\"slotCode\":\"A1\",\"label\":\"0123456789012345678901234567890123456789X\"}]}",
        "slots|{\"code\":\"c\",\"name\":\"n\",\"address\":\"a\",\"latitude\":1,\"longitude\":1,\"slots\":[{\"slotCode\":\"A1\"},{\"slotCode\":\"A1\"}]}",
      })
  void invalidBodyIsRejectedWithFieldDetails(String field, String json) {
    assertInvalidField(createMachine(json), field);
  }

  @Test
  void codeLongerThan32IsRejected() {
    String json = MachineFixtures.createJson("C".repeat(33), "A1");

    assertInvalidField(createMachine(json), "code");
  }

  @Test
  void nameLongerThan100AndAddressLongerThan200AreRejected() {
    String name =
        MachineFixtures.createJson("T" + MachineFixtures.tag(), "N".repeat(101), "a", 1, 1);
    String address =
        MachineFixtures.createJson("T" + MachineFixtures.tag(), "n", "A".repeat(201), 1, 1);

    assertInvalidField(createMachine(name), "name");
    assertInvalidField(createMachine(address), "address");
  }

  @Test
  void moreThan100SlotsAreRejected() {
    String[] codes = new String[101];
    for (int i = 0; i < codes.length; i++) {
      codes[i] = "S" + i;
    }

    assertInvalidField(
        createMachine(MachineFixtures.createJson("T" + MachineFixtures.tag(), codes)), "slots");
  }

  @Test
  void exactly100SlotsAreAccepted() {
    String[] codes = new String[100];
    for (int i = 0; i < codes.length; i++) {
      codes[i] = "S" + i;
    }

    Http.Response response =
        createMachine(MachineFixtures.createJson("T" + MachineFixtures.tag(), codes));

    assertThat(response.status()).isEqualTo(201);
    assertThat((List<?>) response.json().get("slots")).hasSize(100);
  }

  @Test
  void boundaryCoordinatesAreAccepted() {
    String json =
        MachineFixtures.createJson("T" + MachineFixtures.tag(), "Pole", "North pole", 90, 180);

    assertThat(createMachine(json).status()).isEqualTo(201);
  }

  @Test
  void unknownFieldsAreRejectedWithValidationError() {
    String json =
        """
        {"code":"%s","name":"n","address":"a","latitude":1,"longitude":1,\
        "slots":[{"slotCode":"A1"}],"status":"RETIRED"}\
        """
            .formatted("T" + MachineFixtures.tag());

    assertError(createMachine(json), 422, "VALIDATION_ERROR");
  }

  @Test
  void clientCannotChooseIdOrStatus() {
    String json =
        """
        {"code":"%s","name":"n","address":"a","latitude":1,"longitude":1,\
        "slots":[{"slotCode":"A1"}],"id":"%s"}\
        """
            .formatted("T" + MachineFixtures.tag(), UUID.randomUUID());

    assertError(createMachine(json), 422, "VALIDATION_ERROR");
  }

  @Test
  void malformedJsonIsBadRequest() {
    assertError(createMachine("{\"code\": "), 400, "BAD_REQUEST");
  }

  @Test
  void missingBodyIsBadRequest() {
    Http.Response response = Http.post(port, "/v1/machines", null, operator());

    assertThat(response.status()).isIn(400, 422);
  }

  @Test
  void failedValidationCreatesNothing() {
    String code = "T" + MachineFixtures.tag();
    createMachine(MachineFixtures.createJson(code, "A1", "A1"));

    assertThat(get("/v1/machines?q=" + code, operator()).json().get("total")).isEqualTo(0);
  }

  @Test
  void withoutCallerIdentityIsUnauthenticated() {
    Http.Response response =
        Http.post(
            port, "/v1/machines", MachineFixtures.createJson("T" + MachineFixtures.tag(), "A1"));

    assertError(response, 401, "UNAUTHENTICATED");
  }

  @Test
  void otherServiceIsNotAllowedToCreate() {
    Http.Response response =
        Http.post(
            port,
            "/v1/machines",
            MachineFixtures.createJson("T" + MachineFixtures.tag(), "A1"),
            "X-Service-Token",
            INVENTORY_TOKEN);

    assertError(response, 403, "CALLER_NOT_ALLOWED");
  }
}
