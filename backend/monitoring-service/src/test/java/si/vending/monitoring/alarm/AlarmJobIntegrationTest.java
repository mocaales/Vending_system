package si.vending.monitoring.alarm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import si.vending.monitoring.alert.support.AlertApiTest;
import si.vending.monitoring.core.support.Http;

/**
 * Alarm rules end to end: measurements through the API, the periodic job against the Machine stub
 * and a simulated clock. The job is called directly; the scheduler is off in tests.
 */
class AlarmJobIntegrationTest extends AlertApiTest {

  /** A clock the test moves; measurements are stored relative to the real time at start. */
  static class MutableClock extends Clock {
    private volatile Instant now = Instant.now();

    void set(Instant instant) {
      now = instant;
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @TestConfiguration
  static class ClockConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired AlarmJob job;
  @Autowired MutableClock clock;
  @Autowired JdbcTemplate jdbc;

  private String machine;
  private Instant start;

  @BeforeEach
  void setUp() {
    machine = newMachine();
    MACHINES.restrictListTo(Set.of(machine));
    MACHINES.setListFails(false);
    start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(60);
    clock.set(start);
  }

  @AfterAll
  static void resetStub() {
    MACHINES.restrictListTo(null);
    MACHINES.setListFails(false);
  }

  private Http.Response send(Instant observedAt, String temperature, String door) {
    return post(
        "/v1/measurements",
        json(UUID.randomUUID().toString(), machine, observedAt, temperature, door),
        device(machine));
  }

  private List<Map<String, Object>> alerts(String type) {
    return jdbc.queryForList(
        "SELECT id, status, resolution, acknowledged_at FROM alert WHERE machine_id = ?::uuid"
            + " AND type = ? ORDER BY opened_at, id",
        machine,
        type);
  }

  private long active(String type) {
    return alerts(type).stream().filter(a -> "ACTIVE".equals(a.get("status"))).count();
  }

  @Test
  void temperatureOpensAfterTwoReadingsAndResolvesAutomatically() {
    send(start, "9.0", "CLOSED");
    assertThat(alerts("TEMPERATURE_HIGH")).isEmpty();
    send(start.plusSeconds(10), "9.5", "CLOSED");
    assertThat(active("TEMPERATURE_HIGH")).isEqualTo(1);

    send(start.plusSeconds(20), "9.9", "CLOSED"); // still high: no second alarm
    assertThat(alerts("TEMPERATURE_HIGH")).hasSize(1);

    send(start.plusSeconds(30), "7.5", "CLOSED"); // between reset and max: stays
    assertThat(active("TEMPERATURE_HIGH")).isEqualTo(1);

    send(start.plusSeconds(40), "6.9", "CLOSED");
    assertThat(alerts("TEMPERATURE_HIGH"))
        .singleElement()
        .satisfies(a -> {
          assertThat(a.get("status")).isEqualTo("RESOLVED");
          assertThat(a.get("resolution")).isEqualTo("AUTO");
        });
  }

  @Test
  void acknowledgementDoesNotResolveAndManualResolveIsFollowedByNewAlarm() {
    send(start, "9.0", "CLOSED");
    send(start.plusSeconds(10), "9.5", "CLOSED");
    String id = alerts("TEMPERATURE_HIGH").get(0).get("id").toString();

    assertThat(acknowledge(id).status()).isEqualTo(200);
    assertThat(active("TEMPERATURE_HIGH")).isEqualTo(1);

    assertThat(resolve(id, "{}").status()).isEqualTo(200);
    assertThat(active("TEMPERATURE_HIGH")).isZero();

    send(start.plusSeconds(20), "9.8", "CLOSED"); // condition still holds
    assertThat(alerts("TEMPERATURE_HIGH")).hasSize(2);
    assertThat(active("TEMPERATURE_HIGH")).isEqualTo(1);
  }

  @Test
  void jobOpensOfflineAndMeasurementResolvesIt() {
    send(start, "5.0", "CLOSED");
    job.run();
    assertThat(alerts("OFFLINE")).isEmpty();

    clock.set(start.plusSeconds(301));
    job.run();
    job.run(); // idempotent
    assertThat(alerts("OFFLINE")).hasSize(1);
    assertThat(active("OFFLINE")).isEqualTo(1);

    send(start.plusSeconds(300), "5.0", "CLOSED");
    assertThat(active("OFFLINE")).isZero();
    assertThat(alerts("OFFLINE"))
        .singleElement()
        .satisfies(a -> assertThat(a.get("resolution")).isEqualTo("AUTO"));
  }

  @Test
  void machineWithoutMeasurementsIsNotOffline() {
    clock.set(start.plusSeconds(3600));
    job.run();
    assertThat(alerts("OFFLINE")).isEmpty();
  }

  @Test
  void jobOpensDoorOpenAfterContinuousOpenAndClosedMeasurementResolves() {
    send(start, "5.0", "OPEN");
    send(start.plusSeconds(60), "5.0", "OPEN");
    clock.set(start.plusSeconds(100));
    job.run();
    assertThat(alerts("DOOR_OPEN")).isEmpty();

    send(start.plusSeconds(120), "5.0", "OPEN");
    clock.set(start.plusSeconds(121));
    job.run();
    assertThat(active("DOOR_OPEN")).isEqualTo(1);

    send(start.plusSeconds(125), "5.0", "CLOSED");
    assertThat(active("DOOR_OPEN")).isZero();
    assertThat(alerts("DOOR_OPEN"))
        .singleElement()
        .satisfies(a -> assertThat(a.get("resolution")).isEqualTo("AUTO"));
  }

  @Test
  void closedInBetweenRestartsTheOpenRun() {
    send(start, "5.0", "OPEN");
    send(start.plusSeconds(100), "5.0", "CLOSED");
    send(start.plusSeconds(110), "5.0", "OPEN");
    clock.set(start.plusSeconds(200)); // open only 90 s since the last close
    job.run();
    assertThat(alerts("DOOR_OPEN")).isEmpty();
  }

  @Test
  void manualResolveWhileOfflineOpensNewAlarmOnNextTick() {
    send(start, "5.0", "CLOSED");
    clock.set(start.plusSeconds(400));
    job.run();
    String id = alerts("OFFLINE").get(0).get("id").toString();
    assertThat(resolve(id, "{}").status()).isEqualTo(200);
    job.run();
    assertThat(alerts("OFFLINE")).hasSize(2);
    assertThat(active("OFFLINE")).isEqualTo(1);
  }

  @Test
  void machineServiceDownSkipsTheRunWithoutFailing() {
    send(start, "5.0", "CLOSED");
    clock.set(start.plusSeconds(400));
    MACHINES.setListFails(true);
    job.run();
    assertThat(alerts("OFFLINE")).isEmpty();
    MACHINES.setListFails(false);
    job.run();
    assertThat(active("OFFLINE")).isEqualTo(1);
  }

  @Test
  void alarmsOfMachinesThatLeftTheActiveListAreResolved() {
    send(start, "5.0", "CLOSED");
    clock.set(start.plusSeconds(400));
    job.run();
    assertThat(active("OFFLINE")).isEqualTo(1);

    MACHINES.restrictListTo(Set.of(UUID.randomUUID().toString())); // machine now in service
    job.run();
    assertThat(active("OFFLINE")).isZero();
    assertThat(alerts("OFFLINE"))
        .singleElement()
        .satisfies(a -> assertThat(a.get("resolution")).isEqualTo("AUTO"));
  }

  @Test
  void concurrentOpenNeverDuplicates() throws Exception {
    send(start, "5.0", "CLOSED");
    clock.set(start.plusSeconds(400));
    var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    try {
      var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 4; i++) {
        futures.add(pool.submit(job::run));
      }
      for (var f : futures) {
        f.get();
      }
    } finally {
      pool.shutdown();
    }
    assertThat(alerts("OFFLINE")).hasSize(1);
  }
}
