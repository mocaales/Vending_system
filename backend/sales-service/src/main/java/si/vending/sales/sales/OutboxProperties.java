package si.vending.sales.sales;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Outbox worker settings (prefix {@code outbox}). The backoff after the n-th failed delivery is
 * {@code initialBackoff * 2^(n-1)}, capped at {@code maxBackoff}. {@code lease} is how long a
 * claimed entry stays hidden from other workers while its HTTP call runs. When the failed
 * deliveries of one entry reach {@code alertAttempts}, the worker logs one ERROR line.
 */
@ConfigurationProperties("outbox")
public record OutboxProperties(
    @DefaultValue("true") boolean schedulerEnabled,
    @DefaultValue("PT1S") Duration pollInterval,
    @DefaultValue("PT5S") Duration initialBackoff,
    @DefaultValue("PT5M") Duration maxBackoff,
    @DefaultValue("100") int batchSize,
    @DefaultValue("PT30S") Duration lease,
    @DefaultValue("12") int alertAttempts) {}
