package si.vending.monitoring.measurement;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import si.vending.monitoring.measurement.MeasurementDtos.DoorState;
import si.vending.monitoring.measurement.MeasurementDtos.MeasurementOut;

/** Parameterised SQL over the {@code measurement} table. */
@Repository
public class MeasurementRepository {

  private static final String COLUMNS =
      "measurement_id, machine_id, observed_at, temperature_c, door_state, received_at";

  private final JdbcClient jdbc;

  public MeasurementRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Inserts unless the id exists already; false means the id was taken (also under a race). */
  public boolean insertIfAbsent(MeasurementOut m) {
    return jdbc.sql(
                """
                INSERT INTO measurement (%s) VALUES (:id, :machine, :observed, :temp, :door, :received)
                ON CONFLICT (measurement_id) DO NOTHING
                """
                    .formatted(COLUMNS))
            .param("id", m.measurementId())
            .param("machine", m.machineId())
            .param("observed", Timestamp.from(m.observedAt()))
            .param("temp", new BigDecimal(m.temperatureC()))
            .param("door", m.doorState().name())
            .param("received", Timestamp.from(m.receivedAt()))
            .update()
        == 1;
  }

  public Optional<MeasurementOut> findById(UUID measurementId) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM measurement WHERE measurement_id = :id")
        .param("id", measurementId)
        .query(MeasurementRepository::map)
        .optional();
  }

  public Optional<MeasurementOut> findLatest(UUID machineId) {
    return jdbc.sql(
            "SELECT "
                + COLUMNS
                + " FROM measurement WHERE machine_id = :machine"
                + " ORDER BY observed_at DESC, received_at DESC LIMIT 1")
        .param("machine", machineId)
        .query(MeasurementRepository::map)
        .optional();
  }

  public long count(UUID machineId, Instant from, Instant to) {
    return jdbc.sql(
            """
            SELECT count(*) FROM measurement
            WHERE machine_id = :machine AND observed_at >= :from AND observed_at < :to
            """)
        .param("machine", machineId)
        .param("from", Timestamp.from(from))
        .param("to", Timestamp.from(to))
        .query(Long.class)
        .single();
  }

  public List<MeasurementOut> list(
      UUID machineId, Instant from, Instant to, int limit, int offset) {
    return jdbc.sql(
            "SELECT "
                + COLUMNS
                + """
                 FROM measurement
                WHERE machine_id = :machine AND observed_at >= :from AND observed_at < :to
                ORDER BY observed_at ASC, measurement_id ASC
                LIMIT :limit OFFSET :offset
                """)
        .param("machine", machineId)
        .param("from", Timestamp.from(from))
        .param("to", Timestamp.from(to))
        .param("limit", limit)
        .param("offset", offset)
        .query(MeasurementRepository::map)
        .list();
  }

  public long deleteBefore(UUID machineId, Instant before) {
    return jdbc.sql("DELETE FROM measurement WHERE machine_id = :machine AND observed_at < :before")
        .param("machine", machineId)
        .param("before", Timestamp.from(before))
        .update();
  }

  private static MeasurementOut map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new MeasurementOut(
        rs.getObject("measurement_id", UUID.class),
        rs.getObject("machine_id", UUID.class),
        rs.getTimestamp("observed_at").toInstant(),
        rs.getBigDecimal("temperature_c").toPlainString(),
        DoorState.valueOf(rs.getString("door_state")),
        rs.getTimestamp("received_at").toInstant());
  }
}
