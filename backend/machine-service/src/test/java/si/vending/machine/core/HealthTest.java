package si.vending.machine.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.AbstractApiTest;

/** Health routes and the OpenAPI document. */
class HealthTest extends AbstractApiTest {

  @Test
  void liveReturns200() {
    assertThat(get("/health/live").status()).isEqualTo(200);
  }

  @Test
  void readyReturns200WithDatabaseUp() {
    assertThat(get("/health/ready").status()).isEqualTo(200);
  }

  @Test
  void openApiDocumentIsServedAsJson() {
    var response = get("/openapi.json");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.header("Content-Type")).contains("application/json");
    assertThat(response.json().get("openapi")).asString().startsWith("3.");
  }

  @Test
  @SuppressWarnings("unchecked")
  void healthRoutesAreNotInOpenApi() {
    Map<String, Object> paths = (Map<String, Object>) get("/openapi.json").json().get("paths");

    assertThat(paths).isNotNull();
    assertThat(paths.keySet()).noneMatch(path -> path.startsWith("/health"));
  }
}
