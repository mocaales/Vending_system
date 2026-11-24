package si.vending.monitoring.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.vending.monitoring.core.support.AbstractApiTest;
import si.vending.monitoring.core.support.Http;

/**
 * #23: Machine not reachable at all (connection refused). Every route that has to check that the
 * machine exists answers 503 DEPENDENCY_UNAVAILABLE (docs/01 section 3: the caller retries); the
 * measurement is not stored.
 */
class MachineUnreachableTest extends AbstractApiTest {

  @DynamicPropertySource
  static void unreachableMachine(DynamicPropertyRegistry registry) {
    registry.add("machine.base-url", () -> "http://localhost:" + closedPort());
    registry.add("core.service-tokens.monitoring", () -> "monitoring-secret-token");
  }

  private static int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final String[] OPERATOR = {
    "X-Service-Token", GATEWAY_TOKEN, "X-Operator-Id", "2f0e3c1a-7b9d-4c55-8a1e-6d4b9f0c3e21"
  };

  @Test
  void postGetStatusAndDeleteAre503() {
    String machine = UUID.randomUUID().toString();
    String body =
        """
        {"measurementId":"%s","machineId":"%s","observedAt":"%s","temperatureC":"4.2","doorState":"CLOSED"}\
        """
            .formatted(
                UUID.randomUUID(), machine, java.time.Instant.now().minus(Duration.ofMinutes(1)));

    assertError(
        post("/v1/measurements", body, "X-Service-Token", GATEWAY_TOKEN, "X-Machine-Id", machine),
        503,
        "DEPENDENCY_UNAVAILABLE");
    assertError(get("/v1/measurements/" + machine, OPERATOR), 503, "DEPENDENCY_UNAVAILABLE");
    assertError(
        get("/v1/monitoring/" + machine + "/status", OPERATOR), 503, "DEPENDENCY_UNAVAILABLE");
    Http.Response purge =
        Http.send(
            port,
            "DELETE",
            "/v1/measurements/"
                + machine
                + "?before="
                + java.time.Instant.now().minus(Duration.ofDays(9)),
            null,
            OPERATOR);
    assertError(purge, 503, "DEPENDENCY_UNAVAILABLE");
    assertThat(purge.header("X-Trace-Id")).isNotBlank();
  }
}
