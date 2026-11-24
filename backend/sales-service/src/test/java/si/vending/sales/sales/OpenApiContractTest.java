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

  /** "METHOD path operationId [codes]" of one operation, or a "no ..." marker. */
  @SuppressWarnings("unchecked")
  private static String operation(Map<String, Object> spec, String path, String method) {
    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) spec.get("paths");
    Map<String, Object> item = paths == null ? null : paths.get(path);
    Map<String, Object> op = item == null ? null : (Map<String, Object>) item.get(method);
    if (op == null) {
      return "no " + method.toUpperCase() + " " + path;
    }
    Map<String, Object> responses = (Map<String, Object>) op.get("responses");
    return method.toUpperCase()
        + " "
        + path
        + " "
        + op.get("operationId")
        + " "
        + new TreeSet<>(responses.keySet()).stream().filter(c -> !c.equals("default")).toList();
  }

  /** #27 criterion: the four read operations are documented at runtime as in the committed file. */
  @Test
  void runtimeSpecDocumentsTheReadOperationsLikeTheCommittedFile() throws IOException {
    Map<String, Object> runtime = get("/openapi.json").json();
    Map<String, Object> committed = committed();

    for (String path :
        new String[] {
          "/v1/sales", "/v1/sales/{saleId}", "/v1/sales/stats", "/v1/sales/stats/{machineId}"
        }) {
      String expected = operation(committed, path, "get");
      assertThat(expected).as("committed " + path).doesNotStartWith("no ");
      assertThat(operation(runtime, path, "get")).isEqualTo(expected);
    }
  }

  @Test
  void runtimeSpecDocumentsTheSameIntakeOperationAsTheCommittedFile() throws IOException {
    Map<String, Object> runtime = get("/openapi.json").json();

    assertThat(intake(runtime)).isEqualTo(intake(committed()));
    assertThat(Set.of(intake(runtime))).doesNotContain("no POST /v1/sales");
  }

  /**
   * #35 criterion: the reconciliation operation is documented at runtime as in the committed file.
   */
  @Test
  void runtimeSpecDocumentsTheReconciliationOperationLikeTheCommittedFile() throws IOException {
    Map<String, Object> runtime = get("/openapi.json").json();
    Map<String, Object> committed = committed();
    String path = "/v1/sales/{saleId}/reconciliation";

    String expected = operation(committed, path, "put");
    assertThat(expected).as("committed " + path).doesNotStartWith("no ");
    assertThat(expected).contains("resolveReconciliation");
    assertThat(operation(runtime, path, "put")).isEqualTo(expected);
  }

  /**
   * #55 criterion: the six closing operations are documented at runtime as in the committed file
   * (operationIds createClosing, listClosings, deleteClosingsByDate, getClosing, updateClosing,
   * deleteClosing and their response codes).
   */
  @Test
  void runtimeSpecDocumentsTheClosingOperationsLikeTheCommittedFile() throws IOException {
    Map<String, Object> runtime = get("/openapi.json").json();
    Map<String, Object> committed = committed();
    String list = "/v1/sales/closings";
    String one = "/v1/sales/closings/{id}";
    String[][] operations = {
      {list, "post", "createClosing"},
      {list, "get", "listClosings"},
      {list, "delete", "deleteClosingsByDate"},
      {one, "get", "getClosing"},
      {one, "put", "updateClosing"},
      {one, "delete", "deleteClosing"}
    };

    for (String[] op : operations) {
      String expected = operation(committed, op[0], op[1]);
      assertThat(expected).as("committed " + op[1] + " " + op[0]).doesNotStartWith("no ");
      assertThat(expected).contains(op[2]);
      assertThat(operation(runtime, op[0], op[1])).isEqualTo(expected);
    }
  }
}
