package si.vending.machine.core;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * The caller that {@link ServiceTokenInterceptor} resolved for a guarded route. It is stored as
 * request attribute {@link #ATTRIBUTE} and is the only identity a handler may trust; handlers never
 * read the identity headers themselves.
 *
 * @param service the calling service, from its {@code X-Service-Token} (for example {@code
 *     gateway})
 * @param kind {@link Kind#OPERATOR} or {@link Kind#MACHINE} when the gateway vouched for a user or
 *     a machine, {@link Kind#SERVICE} when the service itself is the caller
 * @param id the operator or machine id; {@code null} for {@link Kind#SERVICE}
 */
public record CallerIdentity(String service, Kind kind, UUID id) {

  /** Request attribute under which the interceptor stores the resolved identity. */
  public static final String ATTRIBUTE = CallerIdentity.class.getName();

  public enum Kind {
    SERVICE,
    OPERATOR,
    MACHINE
  }

  /**
   * The identity of this request if it is of the given kind; otherwise 403 CALLER_NOT_ALLOWED. A
   * missing attribute (a route the interceptor did not guard) is also 403, never a 500.
   */
  public static CallerIdentity require(HttpServletRequest request, Kind kind) {
    if (request.getAttribute(ATTRIBUTE) instanceof CallerIdentity identity
        && identity.kind() == kind
        && (kind == Kind.SERVICE || identity.id() != null)) {
      return identity;
    }
    throw new ApiException(
        HttpStatus.FORBIDDEN, "CALLER_NOT_ALLOWED", "Caller is not allowed to use this route.");
  }
}
