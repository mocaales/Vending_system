package si.vending.monitoring.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import si.vending.monitoring.core.support.AbstractApiTest;

/**
 * #23 and #25 criterion: the committed {@code openapi.yaml} equals the runtime {@code
 * /openapi.json} for the measurement, status, threshold and alert operations (method + path, {@code
 * operationId}, documented response codes). Later issues extend {@link #inScope}.
 */
class OpenApiContractTest extends AbstractApiTest {

  private static final Set<String> METHODS = Set.of("get", "put", "post", "delete");

  @SuppressWarnings("unchecked")
  private static Map<String, Object> committed() throws IOException {
    try (Reader reader = Files.newBufferedReader(Path.of("openapi.yaml"))) {
      return new Yaml().load(reader);
    }
  }

  private static boolean inScope(String path) {
    return path.equals("/v1/measurements")
        || path.equals("/v1/measurements/{machineId}")
        || path.equals("/v1/monitoring/{machineId}/status")
        || path.equals("/v1/thresholds/{machineId}")
        || path.equals("/v1/alerts")
        || path.equals("/v1/alerts/{id}/acknowledgement")
        || path.equals("/v1/alerts/{id}/resolution");
  }

  @SuppressWarnings("unchecked")
  private static Set<String> operations(Map<String, Object> spec, boolean withDetails) {
    Set<String> result = new TreeSet<>();
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) spec.get("paths");
    paths.entrySet().removeIf(e -> !inScope(e.getKey()));
    paths.forEach((path, item) ->
        item.forEach((method, operation) -> {
          if (!METHODS.contains(method)) {
            return;
          }
          Map<String, Object> op = (Map<String, Object>) operation;
          String line = method.toUpperCase() + " " + path;
          if (withDetails) {
            Map<String, Object> responses = (Map<String, Object>) op.get("responses");
            line += " " + op.get("operationId") + " " + new TreeSet<>(responses.keySet());
          }
          result.add(line);
        }));
    return result;
  }

  @Test
  void runtimeSpecHasAllMeasurementStatusThresholdAndAlertOperationsOfTheCommittedFile()
      throws IOException {
    Map<String, Object> runtime = get("/openapi.json").json();

    assertThat(operations(committed(), false)).hasSize(11);
    assertThat(operations(runtime, false)).isEqualTo(operations(committed(), false));
  }

  @Test
  void runtimeSpecHasTheSameOperationIdsAndResponseCodes() throws IOException {
    Map<String, Object> runtime = get("/openapi.json").json();

    assertThat(operations(runtime, true)).isEqualTo(operations(committed(), true));
  }
}
