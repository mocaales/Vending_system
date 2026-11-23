package si.vending.machine.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import si.vending.machine.core.support.AbstractApiTest;

/**
 * #17 security review, finding F1: the service-token guard is opt-in, so a new route without {@link
 * AllowedCallers} would be open to anyone. This test walks every handler the application maps and
 * requires {@code @AllowedCallers} on each one, except the explicit allowlist below.
 *
 * <p>Covers future routes too (issue #18 and later): it reads the live mapping, not a list of
 * controllers.
 *
 * <p>How it fails on a miss: a new handler such as {@code @GetMapping("/v1/x")} without
 * {@code @AllowedCallers} on it or its controller makes this test fail with the message "routes
 * without @AllowedCallers" listing {@code Controller#method GET /v1/x}. Fix it by annotating the
 * route; a route that is public on purpose goes into {@link #PUBLIC_ON_PURPOSE} with a reason.
 */
class RouteGuardCoverageTest extends AbstractApiTest {

  /** Handlers that are public on purpose, as {@code SimpleClassName#method}. */
  private static final Set<String> PUBLIC_ON_PURPOSE =
      Set.of(
          // liveness and readiness probes for the container orchestrator (no business data)
          "HealthController#live",
          "HealthController#ready",
          // Spring Boot error page for unmatched or failed requests; no business data
          "BasicErrorController#error",
          "BasicErrorController#errorHtml",
          // test-only routes of CoreTestController; /test/guarded is the one guarded route
          "CoreTestController#echo",
          "CoreTestController#uuid",
          "CoreTestController#boom",
          "CoreTestController#trace",
          "CoreTestController#items");

  /** Package prefixes of public handlers: springdoc serves /openapi.json and its resources. */
  private static final List<String> PUBLIC_PACKAGES = List.of("org.springdoc.");

  @Autowired RequestMappingHandlerMapping mapping;

  private static boolean allowlisted(HandlerMethod handler) {
    String name = handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
    String declaring = handler.getMethod().getDeclaringClass().getName();
    return PUBLIC_ON_PURPOSE.contains(name)
        || PUBLIC_PACKAGES.stream().anyMatch(declaring::startsWith);
  }

  private static boolean guarded(HandlerMethod handler) {
    return AnnotatedElementUtils.hasAnnotation(handler.getMethod(), AllowedCallers.class)
        || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), AllowedCallers.class);
  }

  @Test
  void everyRouteIsGuardedUnlessItIsOnTheAllowlist() {
    // guards against a vacuous pass: the walk must have seen the machine routes
    assertThat(mapping.getHandlerMethods().values())
        .anyMatch(h -> h.getBeanType().getSimpleName().equals("MachineController"));
    List<String> unguarded = new ArrayList<>();
    for (Map.Entry<?, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
      HandlerMethod handler = entry.getValue();
      if (!guarded(handler) && !allowlisted(handler)) {
        unguarded.add(
            handler.getBeanType().getSimpleName()
                + "#"
                + handler.getMethod().getName()
                + " "
                + entry.getKey());
      }
    }

    assertThat(unguarded).as("routes without @AllowedCallers").isEmpty();
  }
}
