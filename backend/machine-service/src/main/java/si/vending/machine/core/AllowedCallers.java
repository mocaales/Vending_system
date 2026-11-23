package si.vending.machine.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which calling services may use a route, by caller name (for example {@code gateway},
 * {@code inventory}). A route carrying this annotation requires a valid {@code X-Service-Token}.
 *
 * <p>With {@link #operator()} set, an operator identity is accepted only when the request carries
 * the gateway's {@code X-Service-Token} and a valid UUID in {@code X-Operator-Id} (set by the
 * gateway after it verified the operator JWT). The header alone never authenticates: without a
 * known token the request is 401, with another service's token the normal caller check applies, and
 * a malformed non-blank operator id with the gateway token is 401. A blank header counts as absent.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AllowedCallers {

  String[] value();

  /** Also accept operators identified by the gateway-set {@code X-Operator-Id} header. */
  boolean operator() default false;
}
