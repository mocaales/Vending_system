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

  /** Temperatures of the newest {@code n} measurements of the machine, newest first. */
  public List<BigDecimal> latestTemperatures(UUID machineId, int n) {
    return jdbc.sql(
            "SELECT temperature_c FROM measurement WHERE machine_id = :machine"
                + " ORDER BY observed_at DESC, received_at DESC LIMIT :n")
        .param("machine", machineId)
        .param("n", n)
        .query(BigDecimal.class)
        .list();
  }

  /**
   * Start of the current uninterrupted run of OPEN measurements: the oldest OPEN observation newer
   * than the newest CLOSED one. Empty when there is no OPEN observation in that run.
   */
  public Optional<Instant> openRunStart(UUID machineId) {
    return jdbc
        .sql(
            """
            SELECT min(observed_at) FROM measurement
            WHERE machine_id = :machine AND door_state = 'OPEN'
              AND observed_at > COALESCE(
                  (SELECT max(observed_at) FROM measurement
                   WHERE machine_id = :machine AND door_state = 'CLOSED'),
                  'epoch'::timestamptz)
            """)
        .param("machine", machineId)
        .query((rs, row) -> {
          Timestamp t = rs.getTimestamp(1);
          return t == null ? null : t.toInstant();
        })
        .list()
        .stream()
        .findFirst()
        .flatMap(Optional::ofNullable);
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
