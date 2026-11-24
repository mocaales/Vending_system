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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Request and response models of the sales API (openapi.yaml). Field limits are stated here once;
 * the frontend matches them.
 */
public final class SaleDtos {

  /** Earliest accepted {@code occurredAt}; the one place this bound is stated. */
  public static final Instant OCCURRED_AT_MIN = Instant.parse("2020-01-01T00:00:00Z");

  /** Latest accepted time in the read filters {@code from} and {@code to}, inclusive. */
  public static final Instant TIME_MAX = Instant.parse("2100-01-01T00:00:00Z");

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

  public enum Resolution {
    ADJUSTED,
    WRITTEN_OFF
  }

  /**
   * Characters refused in a reconciliation note (same pattern as in openapi.yaml): control
   * characters except tab and newline (C0, DEL, C1), line and paragraph separators and the bidi
   * embedding, override and isolate characters. Lone surrogates are refused separately.
   */
  public static final String NOTE_PATTERN =
      "^[^\\u0000-\\u0008\\u000B-\\u001F\\u007F-\\u009F\\u2028\\u2029\\u202A-\\u202E"
          + "\\u2066-\\u2069]*$";

  private static final java.util.regex.Pattern NOTE_REGEX =
      java.util.regex.Pattern.compile(NOTE_PATTERN);

  @Documented
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
  @Retention(RetentionPolicy.RUNTIME)
  @Constraint(validatedBy = ReconciliationNoteValidator.class)
  public @interface ReconciliationNote {
    String message() default
        "must be 1 to 500 characters of text without control, invisible or direction characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
  }

  /** Length in code points, no lone surrogates, not blank, no refused characters. */
  public static final class ReconciliationNoteValidator
      implements ConstraintValidator<ReconciliationNote, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) {
        return true;
      }
      int count = value.codePointCount(0, value.length());
      if (count < 1 || count > 500) {
        return false;
      }
      boolean blank = true;
      for (int i = 0; i < value.length(); ) {
        int cp = value.codePointAt(i);
        if (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE) {
          return false;
        }
        if (!Character.isWhitespace(cp)) {
          blank = false;
        }
        i += Character.charCount(cp);
      }
      return !blank && NOTE_REGEX.matcher(value).matches();
    }
  }

  /** Body of the reconciliation PUT. */
  public record ReconciliationIn(
      @NotNull Resolution resolution, @NotNull @ReconciliationNote String note) {}

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
      Resolution resolution, String note, Instant resolvedAt, UUID resolvedBy) {}

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
          s.resolution() == null
              ? null
              : new ReconciliationOut(
                  s.resolution(),
                  s.note(),
                  s.resolvedAt().truncatedTo(ChronoUnit.MICROS),
                  s.resolvedBy()));
    }
  }

  /** Sales of one currency in a window; {@code turnover} is a decimal string with two decimals. */
  public record Totals(String currency, long salesCount, long quantity, String turnover) {}

  public record TopProduct(UUID productId, long quantity, String turnover) {}

  public record SalesStatsOut(List<Totals> totals, List<TopProduct> topProducts) {}
}
