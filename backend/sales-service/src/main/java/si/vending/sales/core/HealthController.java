package si.vending.sales.core;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class HealthController {

  private final JdbcTemplate jdbc;

  public HealthController(DataSource dataSource) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.jdbc.setQueryTimeout(2);
  }

  @GetMapping("/health/live")
  public Map<String, String> live() {
    return Map.of("status", "UP");
  }

  @GetMapping("/health/ready")
  public Map<String, String> ready() {
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
    } catch (DataAccessException e) {
      throw new DependencyUnavailableException("Database is not reachable.");
    }
    return Map.of("status", "UP");
  }
}
