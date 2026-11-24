package si.vending.monitoring.measurement.support;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.vending.monitoring.core.support.AbstractApiTest;
import si.vending.monitoring.core.support.Http;

/**
 * Base for the measurement tests (issue #23). Starts the {@link MachineStub} and points the service
 * at it.
 *
 * <p>Assumed contracts (see docs/03 sections 1 and 5):
 *
 * <ul>
 *   <li>{@code machine.base-url} holds the Machine base URL; {@code core.service-tokens.monitoring}
 *       is the token Monitoring presents to Machine.
 *   <li>A machine request ({@code POST /v1/measurements}) carries the gateway {@code
 *       X-Service-Token} plus {@code X-Machine-Id}; an operator request carries the gateway token
 *       plus {@code X-Operator-Id}. As in Machine, the gateway token without the matching identity
 *       header is 403 CALLER_NOT_ALLOWED.
 *   <li>All instants are relative to {@code now}; they are truncated to whole seconds.
 * </ul>
 */
public abstract class MonitoringApiTest extends AbstractApiTest {

  public static final String MONITORING_TOKEN = "monitoring-secret-token";
  public static final String OPERATOR_ID = "2f0e3c1a-7b9d-4c55-8a1e-6d4b9f0c3e21";

  protected static final MachineStub MACHINES = MachineStub.start();

  @DynamicPropertySource
  static void measurementProperties(DynamicPropertyRegistry registry) {
    registry.add("machine.base-url", MACHINES::baseUrl);
    registry.add("core.service-tokens.monitoring", () -> MONITORING_TOKEN);
  }

  /** A machine that exists in the Machine stub. */
  protected static String newMachine() {
    String id = UUID.randomUUID().toString();
    MACHINES.register(id);
    return id;
  }

  protected static String[] device(String machineId) {
    return new String[] {"X-Service-Token", GATEWAY_TOKEN, "X-Machine-Id", machineId};
  }

  protected static String[] operator() {
    return new String[] {"X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", OPERATOR_ID};
  }

  protected static Instant ago(Duration duration) {
    return Instant.now().minus(duration).truncatedTo(ChronoUnit.SECONDS);
  }

  protected static Instant ahead(Duration duration) {
    return Instant.now().plus(duration).truncatedTo(ChronoUnit.SECONDS);
  }

  protected static String json(
      String measurementId, String machineId, Instant observedAt, String temperature, String door) {
    return """
    {"measurementId":"%s","machineId":"%s","observedAt":"%s","temperatureC":"%s","doorState":"%s"}\
    """
        .formatted(measurementId, machineId, observedAt, temperature, door);
  }

  protected Http.Response postMeasurement(
      String machineId, String measurementId, Instant observedAt, String temperature) {
    return post(
        "/v1/measurements",
        json(measurementId, machineId, observedAt, temperature, "CLOSED"),
        device(machineId));
  }

  /** Stores a measurement and fails fast if it was not accepted. */
  protected void store(String machineId, Instant observedAt, String temperature) {
    Http.Response response =
        postMeasurement(machineId, UUID.randomUUID().toString(), observedAt, temperature);
    if (response.status() != 201) {
      throw new AssertionError(
          "fixture POST /v1/measurements expected 201 but got "
              + response.status()
              + ": "
              + response.body());
    }
  }

  protected Http.Response delete(String path, String... headers) {
    return Http.send(port, "DELETE", path, null, headers);
  }

  protected Http.Response listMeasurements(String machineId, String query) {
    return get("/v1/measurements/" + machineId + (query.isEmpty() ? "" : "?" + query), operator());
  }
}
