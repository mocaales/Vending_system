package si.vending.monitoring.alert;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response models of the alert routes; the request bodies are validated by hand. */
public final class AlertDtos {

  private AlertDtos() {}

  public record Notification(String status, int attempts) {}

  public record LowStockSlot(
      String slotCode, UUID productId, int quantity, int lowStockThreshold) {}

  /** {@code slots} is present only for LOW_STOCK alerts. */
  public record AlertOut(
      UUID id,
      UUID machineId,
      String type,
      String status,
      Instant openedAt,
      Instant acknowledgedAt,
      UUID acknowledgedBy,
      Instant resolvedAt,
      UUID resolvedBy,
      String resolution,
      String resolutionNote,
      Notification notification,
      @JsonInclude(JsonInclude.Include.NON_NULL) List<LowStockSlot> slots) {}

  public record LowStockReport(UUID machineId, long version, List<LowStockSlot> slots) {}

  public record ReportResult(String outcome, AlertOut alert) {}
}
