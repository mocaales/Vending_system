package si.vending.monitoring.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/**
 * An ambiguous caller identity is 401 UNAUTHENTICATED on every identity route: the gateway token
 * with both X-Operator-Id and X-Machine-Id, or with one identity header sent twice. A rejected
 * request stores or deletes nothing.
 */
class MeasurementIdentityTest extends MonitoringApiTest {

  private final String machine = newMachine();

  private static String[] both(String machineId) {
    return new String[] {
      "X-Service-Token", GATEWAY_TOKEN, "X-Machine-Id", machineId, "X-Operator-Id", OPERATOR_ID
    };
  }

  private static String[] machineTwice(String machineId) {
    return new String[] {
      "X-Service-Token",
      GATEWAY_TOKEN,
      "X-Machine-Id",
      machineId,
      "X-Machine-Id",
      UUID.randomUUID().toString()
    };
  }

  private static String[] operatorTwice() {
    return new String[] {
      "X-Service-Token",
      GATEWAY_TOKEN,
      "X-Operator-Id",
      OPERATOR_ID,
      "X-Operator-Id",
      UUID.randomUUID().toString()
    };
  }

  private static String[] sameOperatorTwice() {
    return new String[] {
      "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", OPERATOR_ID, "X-Operator-Id", OPERATOR_ID
    };
  }

  private void assertPostRejectedAndNothingStored(String... headers) {
    String id = UUID.randomUUID().toString();
    String body = json(id, machine, ago(Duration.ofMinutes(1)), "4.2", "CLOSED");

    assertError(post("/v1/measurements", body, headers), 401, "UNAUTHENTICATED");

    assertThat(listMeasurements(machine, "").json()).containsEntry("total", 0);
    assertThat(postMeasurement(machine, id, ago(Duration.ofMinutes(1)), "4.2").status())
        .as("first acceptance")
        .isEqualTo(201);
  }

  @Test
  void postWithBothOperatorAndMachineIdIs401() {
    assertPostRejectedAndNothingStored(both(machine));
  }

  @Test
  void postWithMachineIdTwiceIs401() {
    assertPostRejectedAndNothingStored(machineTwice(machine));
  }

  @Test
  void postWithTheSameMachineIdTwiceIs401() {
    assertPostRejectedAndNothingStored(
        "X-Service-Token", GATEWAY_TOKEN, "X-Machine-Id", machine, "X-Machine-Id", machine);
  }

  @Test
  void postWithOperatorIdTwiceAndMachineIdIs401() {
    assertPostRejectedAndNothingStored(
        "X-Service-Token",
        GATEWAY_TOKEN,
        "X-Machine-Id",
        machine,
        "X-Operator-Id",
        OPERATOR_ID,
        "X-Operator-Id",
        UUID.randomUUID().toString());
  }

  @Test
  void listWithAmbiguousIdentityIs401() {
    String path = "/v1/measurements/" + machine;

    assertError(get(path, both(machine)), 401, "UNAUTHENTICATED");
    assertError(get(path, operatorTwice()), 401, "UNAUTHENTICATED");
    assertError(get(path, sameOperatorTwice()), 401, "UNAUTHENTICATED");
    assertError(get(path, machineTwice(machine)), 401, "UNAUTHENTICATED");
  }

  @Test
  void statusWithAmbiguousIdentityIs401() {
    String path = "/v1/monitoring/" + machine + "/status";

    assertError(get(path, both(machine)), 401, "UNAUTHENTICATED");
    assertError(get(path, operatorTwice()), 401, "UNAUTHENTICATED");
  }

  @Test
  void purgeWithAmbiguousIdentityIs401AndDeletesNothing() {
    store(machine, ago(Duration.ofDays(10)), "1.0");
    String path = "/v1/measurements/" + machine + "?before=" + ago(Duration.ofDays(9));

    assertError(delete(path, both(machine)), 401, "UNAUTHENTICATED");
    assertError(delete(path, operatorTwice()), 401, "UNAUTHENTICATED");
    assertError(delete(path, sameOperatorTwice()), 401, "UNAUTHENTICATED");

    Http.Response purge = delete(path, operator());
    assertThat(purge.status()).isEqualTo(200);
    assertThat(purge.json()).as("the measurement was still there").containsEntry("deleted", 1);
  }
}
