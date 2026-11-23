package si.vending.sales.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Uniform error body; {@code details} is present only for VALIDATION_ERROR. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String traceId, List<Detail> details) {

  public record Detail(String field, String issue) {}
}
