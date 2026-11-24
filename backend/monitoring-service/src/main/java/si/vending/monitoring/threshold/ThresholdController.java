package si.vending.monitoring.threshold;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import si.vending.monitoring.core.AllowedCallers;
import si.vending.monitoring.core.ApiError;
import si.vending.monitoring.core.CallerIdentity;
import si.vending.monitoring.threshold.ThresholdDtos.ThresholdsOut;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/v1/thresholds")
public class ThresholdController {

  private final ThresholdAdminService service;

  public ThresholdController(ThresholdAdminService service) {
    this.service = service;
  }

  @Operation(operationId = "getThresholds", summary = "Get thresholds of a machine")
  @ApiResponse(responseCode = "200", description = "Effective thresholds.")
  @ApiResponse(
      responseCode = "404",
      description = "MACHINE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @GetMapping("/{machineId}")
  public ThresholdsOut get(@PathVariable UUID machineId, HttpServletRequest request) {
    CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR);
    return service.get(machineId);
  }

  // The body is read raw and validated after the machine check (unknown machine is 404 first).
  @Operation(operationId = "replaceThresholds", summary = "Replace thresholds of a machine")
  @ApiResponse(responseCode = "200", description = "Stored thresholds.")
  @ApiResponse(
      responseCode = "404",
      description = "MACHINE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @PutMapping("/{machineId}")
  public ThresholdsOut replace(
      @PathVariable UUID machineId, @RequestBody JsonNode body, HttpServletRequest request) {
    CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR);
    return service.replace(machineId, body);
  }

  @Operation(operationId = "resetThresholds", summary = "Reset thresholds to the defaults")
  @ApiResponse(responseCode = "204", description = "Thresholds reset (or already default).")
  @ApiResponse(
      responseCode = "404",
      description = "MACHINE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @DeleteMapping("/{machineId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reset(@PathVariable UUID machineId, HttpServletRequest request) {
    CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR);
    service.reset(machineId);
  }
}
