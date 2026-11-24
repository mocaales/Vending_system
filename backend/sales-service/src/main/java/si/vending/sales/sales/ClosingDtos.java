package si.vending.sales.sales;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import si.vending.sales.sales.SaleDtos.ReconciliationNote;
import si.vending.sales.sales.SaleDtos.Totals;

/**
 * Request and response models of the daily closings (openapi.yaml). Field limits are stated here
 * once; the frontend matches them.
 */
public final class ClosingDtos {

  private ClosingDtos() {}

  public enum ClosingStatus {
    DRAFT,
    CONFIRMED
  }

  /** Body of the POST; {@code date} is parsed by the service (strict {@code YYYY-MM-DD}). */
  public record ClosingIn(
      @NotNull @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date,
      UUID machineId,
      @ReconciliationNote(allowBlank = true) String note) {}

  /** Body of the PUT; an absent note keeps the stored one. */
  public record ClosingUpdate(
      @ReconciliationNote(allowBlank = true) String note, @NotNull ClosingStatus status) {}

  public record ClosingOut(
      UUID id,
      LocalDate date,
      UUID machineId,
      ClosingStatus status,
      String note,
      List<Totals> totals,
      int openReconciliations,
      Instant createdAt,
      UUID createdBy,
      Instant updatedAt,
      Instant confirmedAt,
      UUID confirmedBy) {}

  public record ClosingsDeleted(long deleted) {}
}
