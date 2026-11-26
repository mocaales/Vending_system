package si.vending.monitoring.alarm;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The UTC clock used by the alarm rules (tests replace it) and the switch for the scheduler. */
@Configuration
public class JobsConfig {

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** {@code monitoring.jobs.enabled=false} turns the periodic job off (default on). */
  @Configuration
  @EnableScheduling
  @ConditionalOnProperty(
      name = "monitoring.jobs.enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class SchedulingSwitch {}
}
