package si.vending.sales.sales;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
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
import si.vending.sales.core.AllowedCallers;
import si.vending.sales.core.ApiError;
import si.vending.sales.core.CallerIdentity;
import si.vending.sales.core.Page;
import si.vending.sales.core.PageParams;
import si.vending.sales.sales.SaleDtos.ReconciliationIn;
import si.vending.sales.sales.SaleDtos.ReconciliationState;
import si.vending.sales.sales.SaleDtos.SaleEvent;
import si.vending.sales.sales.SaleDtos.SaleOut;
import si.vending.sales.sales.SaleDtos.SalesStatsOut;

@RestController
@RequestMapping("/v1/sales")
public class SaleController {

  /** Top-product count of the statistics: default 5, 1..20; stated once, here. */
  private static final int TOP_MAX = 20;

  private final SaleService service;
  private final SaleQueryService queries;

  public SaleController(SaleService service, SaleQueryService queries) {
    this.service = service;
    this.queries = queries;
  }

  @Operation(operationId = "recordSale", summary = "Record a sale")
  @ApiResponse(responseCode = "201", description = "Sale accepted and stored.")
  @ApiResponse(responseCode = "200", description = "Same saleId and content seen before.")
  @ApiResponse(
      responseCode = "403",
      description = "MACHINE_KEY_MISMATCH or CALLER_NOT_ALLOWED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "SALE_ID_CONFLICT",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR, REFERENCE_NOT_FOUND or TIMESTAMP_OUT_OF_RANGE",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      machine = true)
  @PostMapping
  public ResponseEntity<SaleOut> record(
      @Valid @RequestBody SaleEvent body, HttpServletRequest request) {
    // the identity the guard resolved (gateway token plus exactly one machine id), never the header
    UUID machineId = CallerIdentity.require(request, CallerIdentity.Kind.MACHINE).id();
    SaleService.Recorded result = service.record(body, machineId);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result.sale());
  }

  @Operation(operationId = "listSales", summary = "List sales")
  @ApiResponse(responseCode = "200", description = "Page of sales, newest first.")
  @ApiResponse(
      responseCode = "400",
      description = "BAD_REQUEST",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "CALLER_NOT_ALLOWED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {"gateway", "product"},
      operator = true)
  @GetMapping
  public Page<SaleOut> list(
      @RequestParam(required = false) UUID machineId,
      @RequestParam(required = false) UUID productId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) ReconciliationState reconciliation,
      @Valid PageParams page) {
    return queries.list(
        machineId, productId, from, to, reconciliation, page.limit(), page.offset());
  }

  @Operation(operationId = "getSalesStats", summary = "Sales statistics over all machines")
  @ApiResponse(responseCode = "200", description = "Aggregated figures.")
  @ApiResponse(
      responseCode = "400",
      description = "BAD_REQUEST",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "CALLER_NOT_ALLOWED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {"gateway"},
      operator = true)
  @GetMapping("/stats")
  public SalesStatsOut stats(
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "5") @Min(1) @Max(TOP_MAX) int top) {
    return queries.stats(null, from, to, top);
  }

  @Operation(operationId = "getMachineSalesStats", summary = "Sales statistics of one machine")
  @ApiResponse(responseCode = "200", description = "Aggregated figures of the machine.")
  @ApiResponse(
      responseCode = "400",
      description = "BAD_REQUEST",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "CALLER_NOT_ALLOWED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {"gateway"},
      operator = true)
  @GetMapping("/stats/{machineId}")
  public SalesStatsOut machineStats(
      @PathVariable UUID machineId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "5") @Min(1) @Max(TOP_MAX) int top) {
    return queries.stats(machineId, from, to, top);
  }

  @Operation(operationId = "getSale", summary = "Get a sale")
  @ApiResponse(responseCode = "200", description = "The sale.")
  @ApiResponse(
      responseCode = "400",
      description = "BAD_REQUEST",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "CALLER_NOT_ALLOWED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "SALE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {"gateway"},
      operator = true)
  @GetMapping("/{saleId}")
  public SaleOut get(@PathVariable UUID saleId) {
    return queries.get(saleId);
  }

  @Operation(operationId = "resolveReconciliation", summary = "Close a stock mismatch")
  @ApiResponse(responseCode = "200", description = "The sale with its reconciliation record.")
  @ApiResponse(
      responseCode = "400",
      description = "BAD_REQUEST",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "CALLER_NOT_ALLOWED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "SALE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "INVALID_STATE_TRANSITION",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @PutMapping("/{saleId}/reconciliation")
  public SaleOut resolveReconciliation(
      @PathVariable UUID saleId,
      @Valid @RequestBody ReconciliationIn body,
      HttpServletRequest request) {
    UUID operatorId = CallerIdentity.require(request, CallerIdentity.Kind.OPERATOR).id();
    return queries.resolve(saleId, body, operatorId);
  }
}
