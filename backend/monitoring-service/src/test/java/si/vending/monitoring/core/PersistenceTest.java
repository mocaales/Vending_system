package si.vending.monitoring.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import si.vending.monitoring.core.support.AbstractApiTest;

/** Flyway runs scripts from db/migration/ at startup; HikariCP pool is 5. */
class PersistenceTest extends AbstractApiTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  @Test
  void flywayAppliedMigrationsAtStartup() {
    Integer applied =
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
    assertThat(applied).isGreaterThanOrEqualTo(1);
    // table created by the test-only migration src/test/resources/db/migration/V0_1__...
    assertThat(
            jdbc.queryForObject(
                "SELECT to_regclass('public.core_test_marker')::text", String.class))
        .isEqualTo("core_test_marker");
  }

  @Test
  void connectionPoolHasFiveConnections() {
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(5);
  }
}
