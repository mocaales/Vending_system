package si.vending.sales.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.AbstractApiTest;
import si.vending.sales.core.support.Http;

/** docs/03-api-pogodbe.md section 2: uniform error body {code, message, traceId}. */
class ErrorBodyTest extends AbstractApiTest {

  @Test
  void invalidFieldIsValidationErrorWithDetails() {
    Http.Response response = post("/test/echo", "{\"name\":\"\",\"quantity\":0}");

    assertError(response, 422, "VALIDATION_ERROR");
    Object details = response.json().get("details");
    assertThat(details).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list = (List<Map<String, Object>>) details;
    assertThat(list).isNotEmpty();
    // every entry is {field, issue}
    for (Map<String, Object> entry : list) {
      assertThat(entry.keySet()).containsExactlyInAnyOrder("field", "issue");
    }
    assertThat(list).extracting(e -> e.get("field")).contains("name", "quantity");
  }

  @Test
  void unknownJsonFieldIsRejectedWith422() {
    Http.Response response = post("/test/echo", "{\"name\":\"a\",\"quantity\":1,\"extra\":true}");

    assertError(response, 422, "VALIDATION_ERROR");
    assertThat(response.body()).contains("extra");
  }

  @Test
  void validBodyIsAccepted() {
    Http.Response response = post("/test/echo", "{\"name\":\"a\",\"quantity\":1}");

    assertThat(response.status()).isEqualTo(200);
  }

  @Test
  void malformedJsonIsBadRequestWithoutDetails() {
    Http.Response response = post("/test/echo", "{not json");

    assertError(response, 400, "BAD_REQUEST");
    assertThat(response.json()).doesNotContainKey("details");
  }

  @Test
  void invalidUuidPathVariableIsBadRequest() {
    assertError(get("/test/uuid/not-a-uuid"), 400, "BAD_REQUEST");
  }

  @Test
  void unknownPathIsNotFound() {
    assertError(get("/does/not/exist"), 404, "NOT_FOUND");
  }

  @Test
  void unexpectedExceptionIsInternalErrorWithoutLeakingDetails() {
    Http.Response response = get("/test/boom");

    assertError(response, 500, "INTERNAL_ERROR");
    assertThat(response.body()).doesNotContain("internal detail");
  }
}
