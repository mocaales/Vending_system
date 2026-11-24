package si.vending.monitoring.measurement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import si.vending.monitoring.core.AllowedCallers;
import si.vending.monitoring.core.ApiError;
import si.vending.monitoring.core.CallerIdentity;
import si.vending.monitoring.core.Page;
import si.vending.monitoring.core.PageParams;
import si.vending.monitoring.measurement.MeasurementDtos.MachineMonitoringStatus;
import si.vending.monitoring.measurement.MeasurementDtos.MeasurementIn;
import si.vending.monitoring.measurement.MeasurementDtos.MeasurementOut;
import si.vending.monitoring.measurement.MeasurementDtos.PurgeResult;

@RestController
@RequestMapping("/v1")
public class MeasurementController {

  private final MeasurementService service;

  public MeasurementController(MeasurementService service) {
    this.service = service;
  }

  @Operation(operationId = "createMeasurement", summary = "Record a measurement")
  @ApiResponse(responseCode = "201", description = "Measurement stored (first time).")
  @ApiResponse(
      responseCode = "200",
      description = "Same measurementId with the same body was already stored.")
  @ApiResponse(
      responseCode = "409",
      description = "MEASUREMENT_ID_CONFLICT",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR, REFERENCE_NOT_FOUND or TIMESTAMP_OUT_OF_RANGE",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      machine = true)
  @PostMapping("/measurements")
  public ResponseEntity<MeasurementOut> create(
      @Valid @RequestBody MeasurementIn body, HttpServletRequest request) {
    // the identity the guard resolved (gateway token plus exactly one machine id), never the header
    UUID machineId = CallerIdentity.require(request, CallerIdentity.Kind.MACHINE).id();
    MeasurementService.Ingested result = service.ingest(body, machineId);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result.measurement());
  }

  @Operation(operationId = "listMeasurements", summary = "List measurements of a machine")
  @ApiResponse(responseCode = "200", description = "Page of measurements in the window.")
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
  @GetMapping("/measurements/{machineId}")
  public Page<MeasurementOut> list(
      @PathVariable UUID machineId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @Valid PageParams page,
      HttpServletRequest request) {
    CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR);
    return service.list(machineId, from, to, page.limit(), page.offset());
  }

  @Operation(operationId = "getMachineStatus", summary = "Get reachability and last measurement")
  @ApiResponse(responseCode = "200", description = "Current monitoring status.")
  @ApiResponse(
      responseCode = "404",
      description = "MACHINE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @GetMapping("/monitoring/{machineId}/status")
  public MachineMonitoringStatus status(@PathVariable UUID machineId, HttpServletRequest request) {
    CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR);
    return service.status(machineId);
  }

  @Operation(operationId = "purgeMeasurements", summary = "Purge old measurements of a machine")
  @ApiResponse(responseCode = "200", description = "Number of deleted measurements.")
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
  @DeleteMapping("/measurements/{machineId}")
  public PurgeResult purge(
      @PathVariable UUID machineId,
      @RequestParam(required = false) Instant before,
      HttpServletRequest request) {
    CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR);
    return service.purge(machineId, before);
  }
}
