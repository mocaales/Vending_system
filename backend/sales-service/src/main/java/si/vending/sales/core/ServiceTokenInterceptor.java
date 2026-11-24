package si.vending.sales.core;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces {@link AllowedCallers}: identifies the caller by {@code X-Service-Token} and, for the
 * gateway, by at most one identity header. Stores the result as {@link CallerIdentity#ATTRIBUTE}.
 * Every rejection happens here, before the handler runs any I/O.
 */
public class ServiceTokenInterceptor implements HandlerInterceptor {

  public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
  public static final String OPERATOR_HEADER = "X-Operator-Id";
  public static final String MACHINE_HEADER = "X-Machine-Id";
  private static final String GATEWAY = "gateway";
  private static final Pattern CANONICAL_UUID =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  private final Map<String, String> tokens;

  public ServiceTokenInterceptor(ServiceTokens tokens) {
    this.tokens = tokens.serviceTokens();
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return true;
    }
    AllowedCallers allowed =
        AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), AllowedCallers.class);
    if (allowed == null) {
      allowed =
          AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), AllowedCallers.class);
    }
    if (allowed == null) {
      return true;
    }
    request.removeAttribute(CallerIdentity.ATTRIBUTE);
    List<String> presented = values(request, SERVICE_TOKEN_HEADER, false);
    String caller = presented.size() == 1 ? identify(presented.get(0)) : null;
    if (caller == null) {
      throw unauthenticated("Missing or invalid service token.");
    }
    CallerIdentity identity = null;
    if (GATEWAY.equals(caller)) {
      identity = gatewayIdentity(request, allowed);
    }
    if (identity == null && Arrays.asList(allowed.value()).contains(caller)) {
      identity = new CallerIdentity(caller, CallerIdentity.Kind.SERVICE, null);
    }
    if (identity == null) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "CALLER_NOT_ALLOWED", "Caller is not allowed to use this route.");
    }
    request.setAttribute(CallerIdentity.ATTRIBUTE, identity);
    return true;
  }

  /**
   * The operator or machine the gateway vouches for, if the route accepts that kind; {@code null}
   * if there is none (the caller list decides then). Ambiguous or malformed identity is 401: both
   * headers sent (even if one is blank), one header sent more than once or as a comma-joined list,
   * or a non-blank value that is not a canonical UUID. A single blank header counts as absent.
   */
  private static CallerIdentity gatewayIdentity(
      HttpServletRequest request, AllowedCallers allowed) {
    List<String> operators = values(request, OPERATOR_HEADER, true);
    List<String> machines = values(request, MACHINE_HEADER, true);
    if (!operators.isEmpty() && !machines.isEmpty()) {
      throw unauthenticated("Ambiguous caller identity.");
    }
    UUID operator = single(operators);
    UUID machine = single(machines);
    if (operator != null) {
      return new CallerIdentity(
          GATEWAY, CallerIdentity.Kind.OPERATOR, requireKind(operator, allowed.operator()));
    }
    if (machine != null) {
      return new CallerIdentity(
          GATEWAY, CallerIdentity.Kind.MACHINE, requireKind(machine, allowed.machine()));
    }
    return null;
  }

  /** The id if the route accepts this kind of identity; 403 otherwise, never a fallback. */
  private static UUID requireKind(UUID id, boolean accepted) {
    if (!accepted) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "CALLER_NOT_ALLOWED", "Caller is not allowed to use this route.");
    }
    return id;
  }

  /** Every value of a header: one entry per header line, and per comma part if {@code split}. */
  private static List<String> values(HttpServletRequest request, String name, boolean split) {
    List<String> result = new ArrayList<>();
    Enumeration<String> lines = request.getHeaders(name);
    while (lines != null && lines.hasMoreElements()) {
      String line = lines.nextElement();
      String value = line == null ? "" : line;
      if (split) {
        result.addAll(Arrays.asList(value.split(",", -1)));
      } else {
        result.add(value);
      }
    }
    return result;
  }

  /** The UUID of a header sent once; {@code null} if absent or blank; 401 otherwise. */
  private static UUID single(List<String> values) {
    if (values.isEmpty()) {
      return null;
    }
    if (values.size() > 1) {
      throw unauthenticated("Ambiguous caller identity.");
    }
    String value = values.get(0).strip();
    if (value.isEmpty()) {
      return null;
    }
    if (!CANONICAL_UUID.matcher(value).matches()) {
      throw unauthenticated("Invalid caller identity.");
    }
    return UUID.fromString(value);
  }

  private static ApiException unauthenticated(String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message);
  }

  private String identify(String presented) {
    if (presented == null || presented.isBlank()) {
      return null;
    }
    byte[] given = presented.getBytes(StandardCharsets.UTF_8);
    String match = null;
    for (Map.Entry<String, String> entry : tokens.entrySet()) {
      String configured = entry.getValue();
      if (configured != null
          && !configured.isBlank()
          && MessageDigest.isEqual(given, configured.getBytes(StandardCharsets.UTF_8))) {
        match = entry.getKey();
      }
    }
    return match;
  }
}
