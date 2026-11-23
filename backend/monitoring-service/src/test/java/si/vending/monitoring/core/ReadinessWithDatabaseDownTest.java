package si.vending.monitoring.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import si.vending.monitoring.core.support.AbstractApiTest;
import si.vending.monitoring.core.support.Http;

/**
 * /health/ready reflects the service's own database: 200 while it is reachable, 503
 * DEPENDENCY_UNAVAILABLE once it is stopped; /health/live stays 200. Uses its own container so the
 * shared one is not disturbed.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.datasource.hikari.connection-timeout=2000"})
@DirtiesContext
class ReadinessWithDatabaseDownTest {

  private static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine");

  static {
    DB.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DB::getJdbcUrl);
    registry.add("spring.datasource.username", DB::getUsername);
    registry.add("spring.datasource.password", DB::getPassword);
  }

  @Value("${local.server.port}")
  int port;

  @Test
  void readyTurns503WhenDatabaseStops() {
    assertThat(Http.get(port, "/health/ready").status()).isEqualTo(200);

    DB.stop();

    Http.Response ready = Http.get(port, "/health/ready");
    AbstractApiTest.assertError(ready, 503, "DEPENDENCY_UNAVAILABLE");
    assertThat(Http.get(port, "/health/live").status()).isEqualTo(200);
  }
}
