package si.vending.sales.sales;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import si.vending.sales.core.AllowedCallers;
import si.vending.sales.core.ApiError;
import si.vending.sales.core.ApiException;
import si.vending.sales.core.CallerIdentity;
import si.vending.sales.core.Page;
import si.vending.sales.core.PageParams;
import si.vending.sales.sales.ClosingDtos.ClosingIn;
import si.vending.sales.sales.ClosingDtos.ClosingOut;
import si.vending.sales.sales.ClosingDtos.ClosingStatus;
import si.vending.sales.sales.ClosingDtos.ClosingUpdate;
import si.vending.sales.sales.ClosingDtos.ClosingsDeleted;

/** Daily closings. Static paths under {@code /v1/sales/closings}, never read as a sale id. */
@RestController
@RequestMapping("/v1/sales/closings")
public class ClosingController {

  private final ClosingService service;

  public ClosingController(ClosingService service) {
    this.service = service;
  }

  @Operation(operationId = "createClosing", summary = "Create a daily closing")
  @ApiResponse(responseCode = "201", description = "Draft closing created.")
  @ApiResponse(
      responseCode = "409",
      description = "CLOSING_EXISTS",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @PostMapping
  public ResponseEntity<ClosingOut> create(
      @Valid @RequestBody ClosingIn body, HttpServletRequest request) {
    UUID operatorId = CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR).id();
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body, operatorId));
  }

  @Operation(operationId = "listClosings", summary = "List daily closings")
  @ApiResponse(responseCode = "200", description = "Page of closings, newest date first.")
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {"gateway"},
      operator = true)
  @GetMapping
  public Page<ClosingOut> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID machineId,
      @RequestParam(required = false) ClosingStatus status,
      @Valid PageParams page) {
    return service.list(from, to, machineId, status, page.limit(), page.offset());
  }

  @Operation(operationId = "deleteClosingsByDate", summary = "Delete all draft closings of a day")
  @ApiResponse(responseCode = "200", description = "Number of deleted drafts.")
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @DeleteMapping
  public ClosingsDeleted deleteByDate(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    if (date == null) {
      throw ApiException.invalidField("date", "is required.");
    }
    return service.deleteDrafts(date);
  }

  @Operation(operationId = "getClosing", summary = "Get a daily closing")
  @ApiResponse(responseCode = "200", description = "The closing.")
  @ApiResponse(
      responseCode = "404",
      description = "CLOSING_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {"gateway"},
      operator = true)
  @GetMapping("/{id}")
  public ClosingOut get(@PathVariable UUID id) {
    return service.get(id);
  }

  @Operation(operationId = "updateClosing", summary = "Recompute or confirm a closing")
  @ApiResponse(responseCode = "200", description = "The closing after the update.")
  @ApiResponse(
      responseCode = "404",
      description = "CLOSING_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "CLOSING_CONFIRMED or CLOSING_HAS_OPEN_RECONCILIATION",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @PutMapping("/{id}")
  public ClosingOut update(
      @PathVariable UUID id, @Valid @RequestBody ClosingUpdate body, HttpServletRequest request) {
    UUID operatorId = CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR).id();
    return service.update(id, body, operatorId);
  }

  @Operation(operationId = "deleteClosing", summary = "Delete a draft closing")
  @ApiResponse(responseCode = "204", description = "Draft deleted.")
  @ApiResponse(
      responseCode = "404",
      description = "CLOSING_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "CLOSING_CONFIRMED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
