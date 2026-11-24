package si.vending.sales.sales;

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
import si.vending.sales.core.support.AbstractApiTest;

/**
 * #26 criterion: the committed {@code openapi.yaml} equals the runtime {@code /openapi.json} for
 * the sale intake operation (operation, operationId and documented response codes).
 */
class OpenApiContractTest extends AbstractApiTest {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> committed() throws IOException {
    try (Reader reader = Files.newBufferedReader(Path.of("openapi.yaml"))) {
      return new Yaml().load(reader);
    }
  }

  @SuppressWarnings("unchecked")
  private static String intake(Map<String, Object> spec) {
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) spec.get("paths");
    Map<String, Object> item = paths == null ? null : paths.get("/v1/sales");
    Map<String, Object> post = item == null ? null : (Map<String, Object>) item.get("post");
    if (post == null) {
      return "no POST /v1/sales";
    }
    Map<String, Object> responses = (Map<String, Object>) post.get("responses");
    return "POST /v1/sales "
        + post.get("operationId")
        + " "
        + new TreeSet<>(responses.keySet()).stream().filter(c -> !c.equals("default")).toList();
  }

  @Test
  void runtimeSpecDocumentsTheSameIntakeOperationAsTheCommittedFile() throws IOException {
    Map<String, Object> runtime = get("/openapi.json").json();

    assertThat(intake(runtime)).isEqualTo(intake(committed()));
    assertThat(Set.of(intake(runtime))).doesNotContain("no POST /v1/sales");
  }
}
