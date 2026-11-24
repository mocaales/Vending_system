package si.vending.monitoring.threshold;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import si.vending.monitoring.measurement.ThresholdService.Thresholds;

/** Parameterised SQL over the {@code threshold} table. */
@Repository
public class ThresholdRepository {

  private final JdbcClient jdbc;

  public ThresholdRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Thresholds> find(UUID machineId) {
    return jdbc.sql(
            """
            SELECT max_temp_c, reset_temp_c, consecutive_readings, door_open_max_seconds,
                   no_data_max_seconds
            FROM threshold WHERE machine_id = :machine
            """)
        .param("machine", machineId)
        .query((rs, row) ->
            new Thresholds(
                rs.getBigDecimal("max_temp_c").toPlainString(),
                rs.getBigDecimal("reset_temp_c").toPlainString(),
                rs.getInt("consecutive_readings"),
                rs.getInt("door_open_max_seconds"),
                rs.getInt("no_data_max_seconds")))
        .optional();
  }

  public void upsert(UUID machineId, Thresholds t) {
    jdbc.sql(
            """
            INSERT INTO threshold (machine_id, max_temp_c, reset_temp_c, consecutive_readings,
                                   door_open_max_seconds, no_data_max_seconds, updated_at)
            VALUES (:machine, :max, :reset, :readings, :door, :noData, :now)
            ON CONFLICT (machine_id) DO UPDATE SET
                max_temp_c = EXCLUDED.max_temp_c,
                reset_temp_c = EXCLUDED.reset_temp_c,
                consecutive_readings = EXCLUDED.consecutive_readings,
                door_open_max_seconds = EXCLUDED.door_open_max_seconds,
                no_data_max_seconds = EXCLUDED.no_data_max_seconds,
                updated_at = EXCLUDED.updated_at
            """)
        .param("machine", machineId)
        .param("max", new BigDecimal(t.maxTempC()))
        .param("reset", new BigDecimal(t.resetTempC()))
        .param("readings", t.consecutiveReadings())
        .param("door", t.doorOpenMaxSeconds())
        .param("noData", t.noDataMaxSeconds())
        .param("now", Timestamp.from(Instant.now()))
        .update();
  }

  public void delete(UUID machineId) {
    jdbc.sql("DELETE FROM threshold WHERE machine_id = :machine")
        .param("machine", machineId)
        .update();
  }
}
