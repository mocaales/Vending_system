package si.vending.sales.sales;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import java.util.UUID;

/**
 * Request and response models of the sales API (openapi.yaml). Field limits are stated here once;
 * the frontend matches them.
 */
public final class SaleDtos {

  /** Earliest accepted {@code occurredAt}; the one place this bound is stated. */
  public static final Instant OCCURRED_AT_MIN = Instant.parse("2020-01-01T00:00:00Z");

  private SaleDtos() {}

  @Documented
  @Target({
    ElementType.RECORD_COMPONENT,
    ElementType.FIELD,
    ElementType.PARAMETER,
    ElementType.METHOD
  })
  @Retention(RetentionPolicy.RUNTIME)
  @Constraint(validatedBy = NotBeforeMinValidator.class)
  public @interface NotBeforeMin {
    String message() default "must not be before 2020-01-01T00:00:00Z";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
  }

  public static final class NotBeforeMinValidator
      implements ConstraintValidator<NotBeforeMin, Instant> {
    @Override
    public boolean isValid(Instant value, ConstraintValidatorContext context) {
      return value == null || !value.isBefore(OCCURRED_AT_MIN);
    }
  }

  public enum PaymentMethod {
    CASH,
    CARD,
    OTHER
  }

  public enum ReconciliationState {
    PENDING,
    APPLIED,
    NEEDS_RECONCILIATION,
    RESOLVED
  }

  /** A sale sent by a machine; every field is required. Money is a decimal string. */
  public record SaleEvent(
      @NotNull UUID saleId,
      @NotNull UUID machineId,
      @NotNull @Size(min = 1, max = 8) @Pattern(regexp = "^[A-Za-z0-9_-]{1,8}$") String slotId,
      @NotNull UUID productId,
      @NotNull @Min(1) @Max(100) Integer quantity,
      @NotNull @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,2})?$") String unitPricePaid,
      @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
      @NotNull PaymentMethod paymentMethod,
      @NotNull @NotBeforeMin Instant occurredAt) {}

  public record InventoryResult(ReconciliationState state, String reason) {}

  public record ReconciliationOut(
      String resolution, String note, Instant resolvedAt, UUID resolvedBy) {}

  public record SaleOut(
      UUID saleId,
      UUID machineId,
      String slotId,
      UUID productId,
      int quantity,
      String unitPricePaid,
      String currency,
      PaymentMethod paymentMethod,
      Instant occurredAt,
      Instant receivedAt,
      InventoryResult inventory,
      ReconciliationOut reconciliation) {

    static SaleOut of(Sale s) {
      String reason = s.inventoryReason();
      return new SaleOut(
          s.saleId(),
          s.machineId(),
          s.slotCode(),
          s.productId(),
          s.quantity(),
          s.unitPricePaid().setScale(2).toPlainString(),
          s.currency(),
          s.paymentMethod(),
          s.occurredAt(),
          s.receivedAt(),
          new InventoryResult(s.inventoryState(), reason),
          null);
    }
  }
}
