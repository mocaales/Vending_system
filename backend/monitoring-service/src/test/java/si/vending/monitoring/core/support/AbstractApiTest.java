package si.vending.monitoring.core.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for black-box tests: the whole service runs on a random port against a real PostgreSQL
 * (Testcontainers, one shared container per test JVM).
 *
 * <p>Contract assumed for service tokens: property {@code core.service-tokens.<caller>} holds the
 * token of the calling service {@code <caller>}; in {@code application.yml} the backend team maps
 * the env vars {@code GATEWAY_SERVICE_TOKEN}, {@code MONITORING_SERVICE_TOKEN}, ... onto it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractApiTest {

  public static final String GATEWAY_TOKEN = "gateway-secret-token";
  public static final String INVENTORY_TOKEN = "inventory-secret-token";

  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("core.service-tokens.gateway", () -> GATEWAY_TOKEN);
    registry.add("core.service-tokens.inventory", () -> INVENTORY_TOKEN);
  }

  @Value("${local.server.port}")
  protected int port;

  protected Http.Response get(String path, String... headers) {
    return Http.get(port, path, headers);
  }

  protected Http.Response post(String path, String json, String... headers) {
    return Http.post(port, path, json, headers);
  }

  /** Asserts the uniform error body {@code {code, message, traceId}} (plus details if allowed). */
  public static void assertError(Http.Response response, int status, String code) {
    assertThat(response.status()).isEqualTo(status);
    Map<String, Object> body = response.json();
    assertThat(body.get("code")).isEqualTo(code);
    assertThat(body.get("message")).asString().isNotBlank();
    assertThat(body.get("traceId")).asString().isNotBlank();
    assertThat(body.get("traceId")).isEqualTo(response.header("X-Trace-Id"));
    Set<String> allowed =
        "VALIDATION_ERROR".equals(code)
            ? Set.of("code", "message", "traceId", "details")
            : Set.of("code", "message", "traceId");
    assertThat(body.keySet()).isSubsetOf(allowed);
  }
}
