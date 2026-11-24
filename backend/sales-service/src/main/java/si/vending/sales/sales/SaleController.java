package si.vending.sales.sales;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.vending.sales.core.AllowedCallers;
import si.vending.sales.core.ApiError;
import si.vending.sales.core.CallerIdentity;
import si.vending.sales.sales.SaleDtos.SaleEvent;
import si.vending.sales.sales.SaleDtos.SaleOut;

@RestController
@RequestMapping("/v1/sales")
public class SaleController {

  private final SaleService service;

  public SaleController(SaleService service) {
    this.service = service;
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
}
