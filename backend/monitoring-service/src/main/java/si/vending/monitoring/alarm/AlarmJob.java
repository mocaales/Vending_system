package si.vending.monitoring.alarm;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import si.vending.monitoring.core.DependencyUnavailableException;
import si.vending.monitoring.core.TraceIdFilter;
import si.vending.monitoring.measurement.MachineClient;

/**
 * Periodic OFFLINE and DOOR_OPEN evaluation of every ACTIVE machine. Scheduling is switched on by
 * {@link JobsConfig}; {@link #run()} can be called directly. Several instances may run at once:
 * opening is idempotent (partial unique index), so no alarm is doubled.
 */
@Component
public class AlarmJob {

  private static final Logger LOG = LoggerFactory.getLogger(AlarmJob.class);

  private final MachineClient machines;
  private final AlarmRules rules;

  public AlarmJob(MachineClient machines, AlarmRules rules) {
    this.machines = machines;
    this.rules = rules;
  }

  @Scheduled(
      fixedDelayString = "${monitoring.jobs.interval:PT30S}",
      initialDelayString = "${monitoring.jobs.initial-delay:PT30S}")
  public void run() {
    MDC.put(TraceIdFilter.MDC_KEY, UUID.randomUUID().toString());
    try {
      evaluateAll();
    } finally {
      MDC.remove(TraceIdFilter.MDC_KEY);
    }
  }

  private void evaluateAll() {
    List<UUID> active;
    try {
      active = machines.listActiveIds();
    } catch (DependencyUnavailableException e) {
      LOG.warn("Alarm job skipped: Machine service is not available.");
      return;
    } catch (RuntimeException e) {
      LOG.warn("Alarm job skipped: {}", e.getClass().getSimpleName());
      return;
    }
    try {
      rules.resolveForInactiveMachines(active);
    } catch (DataAccessException e) {
      LOG.warn("Alarm job stopped: database error {}", e.getClass().getSimpleName());
      return;
    }
    int failed = 0;
    for (UUID machineId : active) {
      try {
        rules.evaluateTimeRules(machineId);
      } catch (DataAccessException e) {
        LOG.warn("Alarm job stopped: database error {}", e.getClass().getSimpleName());
        return;
      } catch (RuntimeException e) {
        failed++;
        LOG.warn(
            "Alarm evaluation failed: machine={} error={}",
            machineId,
            e.getClass().getSimpleName());
      }
    }
    LOG.debug("Alarm job evaluated {} machines, {} failed", active.size(), failed);
  }
}
