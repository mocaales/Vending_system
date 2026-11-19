package si.vending.machine.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.AbstractApiTest;
import si.vending.machine.core.support.Http;

/** X-Trace-Id is read or generated, returned in the response, put in the logging context. */
class TraceIdTest extends AbstractApiTest {

  @Test
  void sentTraceIdIsEchoed() {
    Http.Response response = get("/health/live", "X-Trace-Id", "trace-from-caller-1");

    assertThat(response.header("X-Trace-Id")).isEqualTo("trace-from-caller-1");
  }

  @Test
  void missingTraceIdIsGeneratedAsUuid() {
    Http.Response response = get("/health/live");

    String generated = response.header("X-Trace-Id");
    assertThat(generated).isNotBlank();
    assertThat(UUID.fromString(generated)).isNotNull();
  }

  @Test
  void eachRequestWithoutTraceIdGetsADifferentOne() {
    assertThat(get("/health/live").header("X-Trace-Id"))
        .isNotEqualTo(get("/health/live").header("X-Trace-Id"));
  }

  @Test
  void errorBodyCarriesTheSentTraceId() {
    Http.Response response = get("/does/not/exist", "X-Trace-Id", "trace-from-caller-2");

    assertError(response, 404, "NOT_FOUND");
    assertThat(response.json().get("traceId")).isEqualTo("trace-from-caller-2");
  }

  @Test
  void traceIdIsAvailableInTheLoggingContextDuringTheRequest() {
    Http.Response response = get("/test/trace", "X-Trace-Id", "trace-from-caller-3");

    assertThat(response.json().get("mdcTraceId")).isEqualTo("trace-from-caller-3");
  }
}
