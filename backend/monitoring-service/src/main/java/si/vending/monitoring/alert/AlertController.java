package si.vending.monitoring.alert;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import si.vending.monitoring.alert.AlertDtos.AlertOut;
import si.vending.monitoring.alert.AlertDtos.ReportResult;
import si.vending.monitoring.core.AllowedCallers;
import si.vending.monitoring.core.ApiError;
import si.vending.monitoring.core.CallerIdentity;
import si.vending.monitoring.core.Page;
import si.vending.monitoring.core.PageParams;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/v1/alerts")
public class AlertController {

  /** Query values of the {@code status} filter. */
  public enum StatusFilter {
    ACTIVE,
    RESOLVED
  }

  /** Query values of the {@code type} filter. */
  public enum TypeFilter {
    TEMPERATURE_HIGH,
    DOOR_OPEN,
    OFFLINE,
    LOW_STOCK
  }

  private final AlertService service;

  public AlertController(AlertService service) {
    this.service = service;
  }

  @Operation(operationId = "listAlerts", summary = "List alerts")
  @ApiResponse(responseCode = "200", description = "Page of alerts.")
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {"gateway"},
      operator = true)
  @GetMapping
  public Page<AlertOut> list(
      @RequestParam(required = false) UUID machineId,
      @RequestParam(required = false) StatusFilter status,
      @RequestParam(required = false) Boolean acknowledged,
      @RequestParam(required = false) TypeFilter type,
      @Valid PageParams page,
      HttpServletRequest request) {
    requireOperatorOrFrontendApi(request);
    return service.list(
        machineId,
        status == null ? null : status.name(),
        acknowledged,
        type == null ? null : type.name(),
        page.limit(),
        page.offset());
  }

  // The body is read raw and validated by hand (unknown fields, strict integers, limits).
  @Operation(operationId = "reportLowStock", summary = "Report low stock of a machine (internal)")
  @ApiResponse(responseCode = "201", description = "A new LOW_STOCK alert was opened.")
  @ApiResponse(responseCode = "200", description = "Report accepted without opening an alert.")
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR or REFERENCE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(value = {"inventory"})
  @PostMapping
  public ResponseEntity<ReportResult> report(@RequestBody JsonNode body) {
    ReportResult result = service.report(body);
    return ResponseEntity.status(
            "OPENED".equals(result.outcome()) ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result);
  }

  @Operation(operationId = "acknowledgeAlert", summary = "Acknowledge an active alert")
  @ApiResponse(responseCode = "200", description = "The alert with its acknowledgement.")
  @ApiResponse(
      responseCode = "404",
      description = "ALERT_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "ALERT_NOT_ACTIVE",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @PutMapping("/{id}/acknowledgement")
  public AlertOut acknowledge(@PathVariable UUID id, HttpServletRequest request) {
    UUID operatorId = CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR).id();
    return service.acknowledge(id, operatorId);
  }

  @Operation(operationId = "resolveAlert", summary = "Resolve an alert manually")
  @ApiResponse(responseCode = "200", description = "The resolved alert.")
  @ApiResponse(
      responseCode = "404",
      description = "ALERT_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "ALERT_TYPE_NOT_RESOLVABLE",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @PutMapping("/{id}/resolution")
  public AlertOut resolve(
      @PathVariable UUID id,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    UUID operatorId = CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR).id();
    return service.resolve(id, operatorId, body);
  }

  /** Operators, or the gateway's own token without identity (frontend-api, docs/03 section 5). */
  private static void requireOperatorOrFrontendApi(HttpServletRequest request) {
    if (request.getAttribute(CallerIdentity.ATTRIBUTE) instanceof CallerIdentity identity
        && identity.kind() == CallerIdentity.Kind.SERVICE
        && "gateway".equals(identity.service())) {
      return;
    }
    CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR);
  }
}
