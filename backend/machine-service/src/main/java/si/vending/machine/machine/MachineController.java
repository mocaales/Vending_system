package si.vending.machine.machine;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import si.vending.machine.core.AllowedCallers;
import si.vending.machine.core.ApiError;
import si.vending.machine.core.Page;
import si.vending.machine.core.PageParams;
import si.vending.machine.machine.MachineDtos.MachineCreate;
import si.vending.machine.machine.MachineDtos.MachineCreated;
import si.vending.machine.machine.MachineDtos.MachineOut;
import si.vending.machine.machine.MachineDtos.MachineStatusIn;
import si.vending.machine.machine.MachineDtos.MachineUpdate;

/**
 * Machine endpoints (openapi.yaml). Reads are open to operators and to the services in the caller
 * list; changes are for operators only. The responses shared by every operation (400, 401, 403,
 * 500, 503, 504) are added by {@link OpenApiConfig}.
 */
@Tag(name = "Machines")
@RestController
@RequestMapping("/v1/machines")
public class MachineController {

  private static final String BBOX = "^-?\\d+(\\.\\d+)?(,-?\\d+(\\.\\d+)?){3}$";

  private final MachineService service;

  public MachineController(MachineService service) {
    this.service = service;
  }

  @Operation(operationId = "listMachines", summary = "List machines")
  @ApiResponse(responseCode = "200", description = "Page of machines.")
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {"gateway", "inventory", "monitoring"},
      operator = true)
  @GetMapping
  public Page<MachineOut> list(
      @RequestParam(required = false) MachineStatus status,
      @RequestParam(required = false) @Pattern(regexp = BBOX) String bbox,
      @RequestParam(required = false) @Size(max = 100) @NoControlChars String q,
      @Valid PageParams page) {
    return service.list(status, parseBbox(bbox), q, page.limit(), page.offset());
  }

  /** {@code minLongitude,minLatitude,maxLongitude,maxLatitude}; already matched by the pattern. */
  private static double[] parseBbox(String bbox) {
    if (bbox == null) {
      return null;
    }
    String[] parts = bbox.split(",");
    double[] values = new double[4];
    for (int i = 0; i < 4; i++) {
      values[i] = Double.parseDouble(parts[i]);
    }
    return values;
  }

  @Operation(operationId = "createMachine", summary = "Create a machine")
  @ApiResponse(responseCode = "201", description = "Machine created together with its first key.")
  @ApiResponse(
      responseCode = "409",
      description = "MACHINE_CODE_TAKEN",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public MachineCreated create(@Valid @RequestBody MachineCreate body) {
    return service.create(body);
  }

  @Operation(operationId = "getMachine", summary = "Get a machine")
  @ApiResponse(responseCode = "200", description = "The machine (also when retired).")
  @ApiResponse(
      responseCode = "404",
      description = "MACHINE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {"gateway", "inventory", "monitoring"},
      operator = true)
  @GetMapping("/{id}")
  public MachineOut get(@PathVariable UUID id) {
    return service.get(id);
  }

  @Operation(operationId = "updateMachine", summary = "Replace machine details")
  @ApiResponse(responseCode = "200", description = "Updated machine.")
  @ApiResponse(
      responseCode = "404",
      description = "MACHINE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "MACHINE_RETIRED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @PutMapping("/{id}")
  public MachineOut update(@PathVariable UUID id, @Valid @RequestBody MachineUpdate body) {
    return service.update(id, body);
  }

  @Operation(operationId = "retireMachine", summary = "Retire a machine")
  @ApiResponse(responseCode = "204", description = "Machine retired (or already retired).")
  @ApiResponse(
      responseCode = "404",
      description = "MACHINE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "MACHINE_HAS_STOCK",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @DeleteMapping("/{id}")
  public void retire(@PathVariable UUID id) {
    service.retire(id);
  }

  @Operation(operationId = "setMachineStatus", summary = "Change administrative status")
  @ApiResponse(responseCode = "200", description = "Machine with its current status.")
  @ApiResponse(
      responseCode = "404",
      description = "MACHINE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "INVALID_STATE_TRANSITION or MACHINE_RETIRED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @PutMapping("/{id}/status")
  public MachineOut setStatus(@PathVariable UUID id, @Valid @RequestBody MachineStatusIn body) {
    return service.setStatus(id, body.status());
  }
}
