package si.vending.monitoring.threshold;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.vending.monitoring.core.support.AbstractApiTest;
import si.vending.monitoring.core.support.Http;

/** #24: Machine not reachable at all (connection refused) is 503 DEPENDENCY_UNAVAILABLE. */
class ThresholdMachineUnreachableTest extends AbstractApiTest {

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
  void getPutAndDeleteAre503() {
    String path = "/v1/thresholds/" + UUID.randomUUID();
    String json =
        "{\"maxTempC\":\"8.0\",\"resetTempC\":\"7.0\",\"consecutiveReadings\":2,"
            + "\"doorOpenMaxSeconds\":120,\"noDataMaxSeconds\":300}";
    String[] withJson = new String[OPERATOR.length + 2];
    withJson[0] = "Content-Type";
    withJson[1] = "application/json";
    System.arraycopy(OPERATOR, 0, withJson, 2, OPERATOR.length);

    assertError(get(path, OPERATOR), 503, "DEPENDENCY_UNAVAILABLE");
    assertError(Http.send(port, "PUT", path, json, withJson), 503, "DEPENDENCY_UNAVAILABLE");
    assertError(Http.send(port, "DELETE", path, null, OPERATOR), 503, "DEPENDENCY_UNAVAILABLE");
  }
}
