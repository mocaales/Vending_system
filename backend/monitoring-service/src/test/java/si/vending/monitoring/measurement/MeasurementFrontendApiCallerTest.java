package si.vending.monitoring.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.core.support.Http;
import si.vending.monitoring.measurement.support.MonitoringApiTest;

/**
 * Security-review leftover (a): docs/03 section 5 lists OPERATOR and frontend-api as callers of the
 * measurement list and the status. frontend-api is the gateway token WITHOUT an identity header
 * (public map data), so both reads are 200 for it; a machine identity stays 403, and the other
 * routes stay operator-only.
 */
class MeasurementFrontendApiCallerTest extends MonitoringApiTest {

  private static final String[] FRONTEND_API = {"X-Service-Token", GATEWAY_TOKEN};

  @Test
  void gatewayTokenWithoutIdentityCanListMeasurements() {
    String machine = newMachine();
    store(machine, ago(Duration.ofMinutes(1)), "4.2");

    Http.Response response = get("/v1/measurements/" + machine, FRONTEND_API);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.items()).hasSize(1);
  }

  @Test
  void gatewayTokenWithoutIdentityCanReadTheStatus() {
    String machine = newMachine();
    store(machine, ago(Duration.ofSeconds(20)), "4.2");

    Http.Response response = get("/v1/monitoring/" + machine + "/status", FRONTEND_API);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json()).containsEntry("reachability", "ONLINE");
  }

  @Test
  void aMachineIdentityStillCannotReadEither() {
    String machine = newMachine();

    assertError(get("/v1/measurements/" + machine, device(machine)), 403, "CALLER_NOT_ALLOWED");
    assertError(
        get("/v1/monitoring/" + machine + "/status", device(machine)), 403, "CALLER_NOT_ALLOWED");
  }

  @Test
  void frontendApiStillCannotPurgeOrPostMeasurements() {
    String machine = newMachine();

    assertError(
        delete("/v1/measurements/" + machine + "?before=" + ago(Duration.ofDays(9)), FRONTEND_API),
        403,
        "CALLER_NOT_ALLOWED");
    assertError(
        post(
            "/v1/measurements",
            json(
                java.util.UUID.randomUUID().toString(),
                machine,
                ago(Duration.ofMinutes(1)),
                "4.2",
                "CLOSED"),
            FRONTEND_API),
        403,
        "CALLER_NOT_ALLOWED");
  }

  @Test
  void frontendApiStillCannotUseThresholds() {
    String machine = newMachine();

    assertError(get("/v1/thresholds/" + machine, FRONTEND_API), 403, "CALLER_NOT_ALLOWED");
  }
}
