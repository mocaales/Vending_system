package si.vending.monitoring.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which calling services may use a route, by caller name (for example {@code gateway},
 * {@code inventory}). A route carrying this annotation requires a valid {@code X-Service-Token}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AllowedCallers {

  String[] value();
}
