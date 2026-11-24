package si.vending.monitoring.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/** #23: POST /v1/measurements (docs/03 section 5). */
class MeasurementCreateTest extends MonitoringApiTest {

  @Test
  void firstPostIs201WithTheStoredMeasurement() {
    String machine = newMachine();
    String id = UUID.randomUUID().toString();
    Instant observed = ago(Duration.ofMinutes(1));

    Http.Response response =
        post("/v1/measurements", json(id, machine, observed, "4.2", "OPEN"), device(machine));

    assertThat(response.status()).isEqualTo(201);
    assertThat(response.json())
        .containsOnlyKeys(
            "measurementId", "machineId", "observedAt", "temperatureC", "doorState", "receivedAt");
    assertThat(response.json())
        .containsEntry("measurementId", id)
        .containsEntry("machineId", machine)
        .containsEntry("temperatureC", "4.2")
        .containsEntry("doorState", "OPEN");
    assertThat(Instant.parse((String) response.json().get("observedAt"))).isEqualTo(observed);
    assertThat(Instant.parse((String) response.json().get("receivedAt")))
        .isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(5));
  }

  @Test
  void negativeTemperatureKeepsItsDecimalStringForm() {
    String machine = newMachine();

    Http.Response response =
        postMeasurement(machine, UUID.randomUUID().toString(), ago(Duration.ofMinutes(1)), "-18.5");

    assertThat(response.status()).isEqualTo(201);
    assertThat(response.json()).containsEntry("temperatureC", "-18.5");
  }

  @Test
  void storedMeasurementIsListedForTheMachine() {
    String machine = newMachine();
    String id = UUID.randomUUID().toString();
    postMeasurement(machine, id, ago(Duration.ofMinutes(2)), "5.0");

    Http.Response list = listMeasurements(machine, "");

    assertThat(list.status()).isEqualTo(200);
    assertThat(list.json()).containsEntry("total", 1);
    assertThat(list.items()).hasSize(1);
  }

  // --- idempotency

  @Test
  void identicalRepeatIs200WithTheSameBodyAndStoresNothingNew() {
    String machine = newMachine();
    String id = UUID.randomUUID().toString();
    Instant observed = ago(Duration.ofMinutes(1));
    Http.Response first = postMeasurement(machine, id, observed, "4.2");

    Http.Response repeat = postMeasurement(machine, id, observed, "4.2");

    assertThat(first.status()).isEqualTo(201);
    assertThat(repeat.status()).isEqualTo(200);
    assertThat(repeat.json()).isEqualTo(first.json());
    assertThat(listMeasurements(machine, "").json()).containsEntry("total", 1);
  }

  @Test
  void sameIdWithDifferentTemperatureIs409AndKeepsTheOriginal() {
    String machine = newMachine();
    String id = UUID.randomUUID().toString();
    Instant observed = ago(Duration.ofMinutes(1));
    postMeasurement(machine, id, observed, "4.2");

    Http.Response conflict = postMeasurement(machine, id, observed, "9.9");

    assertError(conflict, 409, "MEASUREMENT_ID_CONFLICT");
    Http.Response list = listMeasurements(machine, "");
    assertThat(list.json()).containsEntry("total", 1);
    assertThat(list.items().get(0)).asString().contains("temperatureC=4.2");
  }

  @Test
  void sameIdWithDifferentDoorStateOrTimeIs409() {
    String machine = newMachine();
    String id = UUID.randomUUID().toString();
    Instant observed = ago(Duration.ofMinutes(5));
    post("/v1/measurements", json(id, machine, observed, "4.2", "CLOSED"), device(machine));

    Http.Response otherDoor =
        post("/v1/measurements", json(id, machine, observed, "4.2", "OPEN"), device(machine));
    Http.Response otherTime =
        post(
            "/v1/measurements",
            json(id, machine, observed.plusSeconds(30), "4.2", "CLOSED"),
            device(machine));

    assertError(otherDoor, 409, "MEASUREMENT_ID_CONFLICT");
    assertError(otherTime, 409, "MEASUREMENT_ID_CONFLICT");
  }

  // --- time window of observedAt

  @Test
  void observedAtMoreThanFiveMinutesInTheFutureIs422() {
    String machine = newMachine();

    Http.Response response =
        postMeasurement(machine, UUID.randomUUID().toString(), ahead(Duration.ofMinutes(6)), "4.2");

    assertError(response, 422, "TIMESTAMP_OUT_OF_RANGE");
    assertThat(listMeasurements(machine, "").json()).containsEntry("total", 0);
  }

  @Test
  void observedAtFourMinutesInTheFutureIsAccepted() {
    String machine = newMachine();

    Http.Response response =
        postMeasurement(machine, UUID.randomUUID().toString(), ahead(Duration.ofMinutes(4)), "4.2");

    assertThat(response.status()).isEqualTo(201);
  }

  @Test
  void aMeasurementFromThePastIsAccepted() {
    String machine = newMachine();

    Http.Response response =
        postMeasurement(machine, UUID.randomUUID().toString(), ago(Duration.ofDays(10)), "4.2");

    assertThat(response.status()).isEqualTo(201);
  }

  // --- machine identity and existence

  @Test
  void bodyMachineIdDifferentFromHeaderIs403AndNothingIsStored() {
    String mine = newMachine();
    String other = newMachine();
    String id = UUID.randomUUID().toString();

    Http.Response response =
        post(
            "/v1/measurements",
            json(id, other, ago(Duration.ofMinutes(1)), "4.2", "CLOSED"),
            device(mine));

    assertError(response, 403, "MACHINE_KEY_MISMATCH");
    assertThat(listMeasurements(other, "").json()).containsEntry("total", 0);
    assertThat(listMeasurements(mine, "").json()).containsEntry("total", 0);
  }

  @Test
  void unknownMachineIs422ReferenceNotFound() {
    String ghost = UUID.randomUUID().toString();

    Http.Response response =
        postMeasurement(ghost, UUID.randomUUID().toString(), ago(Duration.ofMinutes(1)), "4.2");

    assertError(response, 422, "REFERENCE_NOT_FOUND");
  }

  @Test
  void machineIsAskedForExistenceWithMonitoringTokenAndTraceId() {
    String machine = newMachine();
    String trace = "trace-" + UUID.randomUUID();

    post(
        "/v1/measurements",
        json(UUID.randomUUID().toString(), machine, ago(Duration.ofMinutes(1)), "4.2", "CLOSED"),
        "X-Service-Token",
        GATEWAY_TOKEN,
        "X-Machine-Id",
        machine,
        "X-Trace-Id",
        trace);

    assertThat(MACHINES.calls())
        .anySatisfy(call -> {
          assertThat(call.method()).isEqualTo("GET");
          assertThat(call.path()).isEqualTo("/v1/machines/" + machine);
          assertThat(call.serviceToken()).isEqualTo(MONITORING_TOKEN);
          assertThat(call.traceId()).isEqualTo(trace);
        });
  }

  // --- validation

  private void assertInvalid(String machine, String body) {
    assertError(post("/v1/measurements", body, device(machine)), 422, "VALIDATION_ERROR");
  }

  @Test
  void missingRequiredFieldsAre422() {
    String machine = newMachine();
    String id = UUID.randomUUID().toString();
    String t = Instant.now().minusSeconds(60).toString();

    assertInvalid(machine, "{}");
    assertInvalid(
        machine,
        "{\"machineId\":\"%s\",\"observedAt\":\"%s\",\"temperatureC\":\"4.2\",\"doorState\":\"OPEN\"}"
            .formatted(machine, t));
    assertInvalid(
        machine,
        "{\"measurementId\":\"%s\",\"observedAt\":\"%s\",\"temperatureC\":\"4.2\",\"doorState\":\"OPEN\"}"
            .formatted(id, t));
    assertInvalid(
        machine,
        "{\"measurementId\":\"%s\",\"machineId\":\"%s\",\"temperatureC\":\"4.2\",\"doorState\":\"OPEN\"}"
            .formatted(id, machine));
    assertInvalid(
        machine,
        "{\"measurementId\":\"%s\",\"machineId\":\"%s\",\"observedAt\":\"%s\",\"doorState\":\"OPEN\"}"
            .formatted(id, machine, t));
    assertInvalid(
        machine,
        "{\"measurementId\":\"%s\",\"machineId\":\"%s\",\"observedAt\":\"%s\",\"temperatureC\":\"4.2\"}"
            .formatted(id, machine, t));
  }

  @Test
  void badValuesAre422() {
    String machine = newMachine();
    String id = UUID.randomUUID().toString();
    String t = Instant.now().minusSeconds(60).toString();
    String tail =
        "\"observedAt\":\"%s\",\"temperatureC\":\"4.2\",\"doorState\":\"OPEN\"}".formatted(t);

    assertInvalid(machine, json(id, machine, Instant.now().minusSeconds(60), "warm", "OPEN"));
    assertInvalid(machine, json(id, machine, Instant.now().minusSeconds(60), "", "OPEN"));
    assertInvalid(machine, json(id, machine, Instant.now().minusSeconds(60), "4.2", "AJAR"));
    assertInvalid(machine, json(id, machine, Instant.now().minusSeconds(60), "4.2", "open"));
    assertInvalid(
        machine,
        "{\"measurementId\":\"not-a-uuid\",\"machineId\":\"%s\",".formatted(machine) + tail);
    assertInvalid(
        machine,
        "{\"measurementId\":\"%s\",\"machineId\":\"%s\",\"observedAt\":\"yesterday\",\"temperatureC\":\"4.2\",\"doorState\":\"OPEN\"}"
            .formatted(id, machine));
    assertInvalid(
        machine, "{\"measurementId\":null,\"machineId\":\"%s\",".formatted(machine) + tail);
  }

  @Test
  void unknownFieldIsRejected() {
    String machine = newMachine();
    String body =
        json(UUID.randomUUID().toString(), machine, Instant.now().minusSeconds(60), "4.2", "OPEN");

    assertInvalid(
        machine,
        body.substring(0, body.length() - 1) + ",\"receivedAt\":\"2026-01-01T00:00:00Z\"}");
    assertInvalid(machine, body.substring(0, body.length() - 1) + ",\"extra\":1}");
  }

  @Test
  void unreadableJsonIs400() {
    String machine = newMachine();

    assertError(post("/v1/measurements", "{not json", device(machine)), 400, "BAD_REQUEST");
  }
}
