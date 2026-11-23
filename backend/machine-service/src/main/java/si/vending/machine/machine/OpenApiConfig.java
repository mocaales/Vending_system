package si.vending.machine.machine;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds the responses every operation can return (openapi.yaml, "shared responses"). */
@Configuration
public class OpenApiConfig {

  private static final Map<String, String> SHARED =
      Map.of(
          "400", "BAD_REQUEST: unreadable JSON, bad parameter or malformed UUID.",
          "401", "UNAUTHENTICATED: missing, invalid or revoked token or key.",
          "403", "FORBIDDEN or CALLER_NOT_ALLOWED.",
          "500", "INTERNAL_ERROR.",
          "503", "DEPENDENCY_UNAVAILABLE: a dependency (for example Inventory) does not answer.",
          "504", "UPSTREAM_TIMEOUT.");

  @Bean
  OperationCustomizer sharedErrorResponses() {
    return (Operation operation, org.springframework.web.method.HandlerMethod handler) -> {
      ApiResponses responses = operation.getResponses();
      SHARED.forEach((code, description) -> {
        if (!responses.containsKey(code)) {
          responses.addApiResponse(
              code,
              new ApiResponse()
                  .description(description)
                  .content(
                      new Content()
                          .addMediaType(
                              "application/json",
                              new MediaType()
                                  .schema(new Schema<>().$ref("#/components/schemas/Error")))));
        }
      });
      return operation;
    };
  }
}
