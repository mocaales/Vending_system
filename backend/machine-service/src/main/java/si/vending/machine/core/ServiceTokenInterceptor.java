package si.vending.machine.core;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Enforces {@link AllowedCallers}: identifies the caller by {@code X-Service-Token}. */
public class ServiceTokenInterceptor implements HandlerInterceptor {

  public static final String OPERATOR_HEADER = "X-Operator-Id";
  private static final String GATEWAY = "gateway";

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
    String caller = identify(request.getHeader("X-Service-Token"));
    String operatorId = request.getHeader(OPERATOR_HEADER);
    boolean operatorPresent = operatorId != null && !operatorId.isBlank();
    if (caller == null) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Missing or invalid service token.");
    }
    if (GATEWAY.equals(caller) && operatorPresent) {
      if (!isOperator(operatorId)) {
        throw new ApiException(
            HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid operator identity.");
      }
      if (allowed.operator()) {
        return true;
      }
    }
    if (!Arrays.asList(allowed.value()).contains(caller)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "CALLER_NOT_ALLOWED", "Caller is not allowed to use this route.");
    }
    return true;
  }

  private static boolean isOperator(String operatorId) {
    if (operatorId == null || operatorId.isBlank()) {
      return false;
    }
    try {
      UUID.fromString(operatorId);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
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
