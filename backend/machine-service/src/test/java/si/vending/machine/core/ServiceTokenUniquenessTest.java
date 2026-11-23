package si.vending.machine.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * #17 security review, finding I3: two callers configured with the same non-blank service token
 * make the token identify the wrong caller, so startup must fail. Blank values (an unset env var
 * becomes an empty string) are not secrets and must not stop the service.
 *
 * <p>The failure message may name the callers but must never contain the token value.
 */
class ServiceTokenUniquenessTest {

  private static final String SHARED = "same-token-shared-by-two-callers";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(CoreConfig.class);

  @Test
  void startupFailsWhenTwoCallersShareOneToken() {
    runner
        .withPropertyValues(
            "core.service-tokens.gateway=" + SHARED, "core.service-tokens.sales=" + SHARED)
        .run(context -> {
          assertThat(context).as("context started with a shared token").hasFailed();
          assertThat(context.getStartupFailure()).isNotNull();
          assertThat(String.valueOf(context.getStartupFailure().getMessage()))
              .doesNotContain(SHARED);
        });
  }

  @Test
  void startupSucceedsWithDistinctTokens() {
    runner
        .withPropertyValues(
            "core.service-tokens.gateway=token-a", "core.service-tokens.sales=token-b")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void blankTokensOfUnconfiguredCallersDoNotStopStartup() {
    runner
        .withPropertyValues(
            "core.service-tokens.gateway=token-a",
            "core.service-tokens.sales=",
            "core.service-tokens.monitoring=")
        .run(context -> assertThat(context).hasNotFailed());
  }
}
