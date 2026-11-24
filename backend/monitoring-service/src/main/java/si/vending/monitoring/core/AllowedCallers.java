package si.vending.monitoring.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which calling services may use a route, by caller name (for example {@code gateway},
 * {@code inventory}). A route carrying this annotation requires a valid {@code X-Service-Token}.
 *
 * <p>With {@link #operator()} or {@link #machine()} set, the gateway's token is also accepted when
 * it carries a valid UUID in {@code X-Operator-Id} or {@code X-Machine-Id} (set by the gateway
 * after it verified the JWT or machine key). The header alone never authenticates: without a known
 * token the request is 401 and with another service's token the identity headers are ignored and
 * the normal caller check applies.
 *
 * <p>With the gateway token:
 *
 * <ul>
 *   <li>both identity headers, one of them sent more than once (also as a comma-joined list), or a
 *       non-blank value that is not a canonical UUID: 401 UNAUTHENTICATED;
 *   <li>no identity header of a kind the route accepts (a single blank header counts as absent):
 *       the caller list decides, so 403 CALLER_NOT_ALLOWED unless {@code gateway} is listed.
 * </ul>
 *
 * <p>The accepted caller is stored as {@link CallerIdentity} under {@link
 * CallerIdentity#ATTRIBUTE}; handlers read it with {@link CallerIdentity#require}, never the
 * headers.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AllowedCallers {

  String[] value();

  /** Also accept operators identified by the gateway-set {@code X-Operator-Id} header. */
  boolean operator() default false;

  /** Also accept machines identified by the gateway-set {@code X-Machine-Id} header. */
  boolean machine() default false;
}
