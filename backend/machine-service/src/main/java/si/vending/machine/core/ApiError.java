package si.vending.machine.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Uniform error body; {@code details} is present only for VALIDATION_ERROR. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Error")
public record ApiError(String code, String message, String traceId, List<Detail> details) {

  @Schema(name = "ValidationErrorDetail")
  public record Detail(String field, String issue) {}
}
