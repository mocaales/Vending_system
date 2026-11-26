package si.vending.monitoring.alarm;

import static org.assertj.core.api.Assertions.assertThat;
import static si.vending.monitoring.alarm.AlarmRules.Verdict.KEEP;
import static si.vending.monitoring.alarm.AlarmRules.Verdict.OPEN;
import static si.vending.monitoring.alarm.AlarmRules.Verdict.RESOLVE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import si.vending.monitoring.measurement.MeasurementDtos.DoorState;
import si.vending.monitoring.measurement.ThresholdService;
import si.vending.monitoring.measurement.ThresholdService.Thresholds;

/**
 * The pure rules with simulated time: defaults 2 readings / 8 C / 7 C, 120 s door, 300 s offline.
 */
class AlarmRulesTest {

  private static final Thresholds T = ThresholdService.DEFAULTS;
  private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

  private static List<BigDecimal> temps(String... newestFirst) {
    return java.util.Arrays.stream(newestFirst).map(BigDecimal::new).toList();
  }

  @Test
  void temperatureOpensOnlyAfterConsecutiveReadingsAboveMax() {
    assertThat(AlarmRules.temperature(temps("8.1"), T)).isEqualTo(KEEP);
    assertThat(AlarmRules.temperature(temps("8.1", "8.5"), T)).isEqualTo(OPEN);
    assertThat(AlarmRules.temperature(temps("8.0", "9"), T)).isEqualTo(KEEP);
    assertThat(AlarmRules.temperature(temps("9", "7.5"), T)).isEqualTo(KEEP);
  }

  @Test
  void temperatureResolvesAtResetAndHoldsInBetween() {
    assertThat(AlarmRules.temperature(temps("7.0", "9"), T)).isEqualTo(RESOLVE);
    assertThat(AlarmRules.temperature(temps("7.5", "9"), T)).isEqualTo(KEEP);
  }

  @Test
  void temperatureUsesMachineThresholds() {
    Thresholds three = new Thresholds("5.0", "4.0", 3, 120, 300);
    assertThat(AlarmRules.temperature(temps("6", "6"), three)).isEqualTo(KEEP);
    assertThat(AlarmRules.temperature(temps("6", "6", "5.1"), three)).isEqualTo(OPEN);
  }

  @Test
  void offlineAfterNoDataMaxSecondsOnly() {
    assertThat(AlarmRules.offline(NOW.minusSeconds(300), NOW, T)).isEqualTo(RESOLVE);
    assertThat(AlarmRules.offline(NOW.minusSeconds(301), NOW, T)).isEqualTo(OPEN);
    assertThat(AlarmRules.offline(null, NOW, T)).isEqualTo(KEEP);
  }

  @Test
  void doorOpenAfterDoorOpenMaxSecondsOfContinuousOpen() {
    assertThat(AlarmRules.doorOpen(DoorState.OPEN, NOW.minusSeconds(120), NOW, T))
        .isEqualTo(RESOLVE);
    assertThat(AlarmRules.doorOpen(DoorState.OPEN, NOW.minusSeconds(121), NOW, T)).isEqualTo(OPEN);
    assertThat(AlarmRules.doorOpen(DoorState.CLOSED, null, NOW, T)).isEqualTo(RESOLVE);
  }
}
