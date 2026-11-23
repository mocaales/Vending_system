package si.vending.machine.machine;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The {@code slotCode} values of a slot list are unique. */
@Documented
@Constraint(validatedBy = UniqueSlotCodes.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueSlotCodes {

  String message() default "slotCode values must be unique";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<UniqueSlotCodes, List<MachineDtos.SlotIn>> {

    @Override
    public boolean isValid(List<MachineDtos.SlotIn> slots, ConstraintValidatorContext context) {
      if (slots == null) {
        return true;
      }
      Set<String> seen = new HashSet<>();
      for (MachineDtos.SlotIn slot : slots) {
        if (slot != null && slot.slotCode() != null && !seen.add(slot.slotCode())) {
          return false;
        }
      }
      return true;
    }
  }
}
