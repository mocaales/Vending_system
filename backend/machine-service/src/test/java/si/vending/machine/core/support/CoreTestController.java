package si.vending.machine.core.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import si.vending.machine.core.AllowedCallers;
import si.vending.machine.core.Page;
import si.vending.machine.core.PageParams;

/** Test-only routes that exercise the {@code core} package; the template has no business routes. */
@RestController
class CoreTestController {

  private static final int TOTAL = 300;

  record EchoRequest(@NotBlank String name, @Min(1) int quantity) {}

  @PostMapping("/test/echo")
  EchoRequest echo(@Valid @RequestBody EchoRequest request) {
    return request;
  }

  @GetMapping("/test/uuid/{id}")
  Map<String, String> uuid(@PathVariable UUID id) {
    return Map.of("id", id.toString());
  }

  @GetMapping("/test/boom")
  void boom() {
    throw new IllegalStateException("internal detail that must not leak");
  }

  /** Returns the trace id that the request logging context (MDC key {@code traceId}) holds. */
  @GetMapping("/test/trace")
  Map<String, String> trace() {
    return Map.of("mdcTraceId", String.valueOf(MDC.get("traceId")));
  }

  @AllowedCallers("gateway")
  @GetMapping("/test/guarded")
  Map<String, String> guarded() {
    return Map.of("ok", "true");
  }

  @GetMapping("/test/items")
  Page<Integer> items(@Valid PageParams params) {
    List<Integer> all = IntStream.range(0, TOTAL).boxed().toList();
    int from = Math.min(params.offset(), TOTAL);
    int to = Math.min(from + params.limit(), TOTAL);
    return new Page<>(all.subList(from, to), TOTAL, params.limit(), params.offset());
  }
}
