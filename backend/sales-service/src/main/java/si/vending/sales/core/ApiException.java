package si.vending.sales.core;

import java.util.List;
import org.springframework.http.HttpStatus;

/** An expected failure with a fixed HTTP status and error code. */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final transient List<ApiError.Detail> details;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, null);
  }

  public ApiException(
      HttpStatus status, String code, String message, List<ApiError.Detail> details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details == null ? null : List.copyOf(details);
  }

  /** 422 VALIDATION_ERROR for one field. */
  public static ApiException invalidField(String field, String issue) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "VALIDATION_ERROR",
        "Request validation failed.",
        List.of(new ApiError.Detail(field, issue)));
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  /** Field details for VALIDATION_ERROR; {@code null} otherwise. */
  public List<ApiError.Detail> details() {
    return details;
  }
}
