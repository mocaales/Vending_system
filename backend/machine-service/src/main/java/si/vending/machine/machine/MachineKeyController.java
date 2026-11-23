package si.vending.machine.machine;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import si.vending.machine.core.AllowedCallers;
import si.vending.machine.core.ApiError;
import si.vending.machine.machine.MachineDtos.KeyCreated;
import si.vending.machine.machine.MachineDtos.KeyIn;
import si.vending.machine.machine.MachineDtos.KeyList;
import si.vending.machine.machine.MachineDtos.MachineKeyVerifyIn;
import si.vending.machine.machine.MachineDtos.MachineKeyVerifyOut;

/**
 * Machine key endpoints (openapi.yaml). The key routes are for operators only; no service may read
 * keys. The verify route is for the gateway only.
 */
@Tag(name = "Machine keys")
@RestController
public class MachineKeyController {

  private final MachineService service;

  public MachineKeyController(MachineService service) {
    this.service = service;
  }

  @Operation(operationId = "listMachineKeys", summary = "List keys of a machine")
  @ApiResponse(responseCode = "200", description = "Keys of the machine.")
  @ApiResponse(
      responseCode = "404",
      description = "MACHINE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @GetMapping("/v1/machines/{id}/keys")
  public KeyList list(@PathVariable UUID id) {
    return service.listKeys(id);
  }

  @Operation(operationId = "createMachineKey", summary = "Issue a new key")
  @ApiResponse(responseCode = "201", description = "Key created.")
  @ApiResponse(
      responseCode = "404",
      description = "MACHINE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "MACHINE_RETIRED or KEY_LIMIT_REACHED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/v1/machines/{id}/keys")
  public KeyCreated create(
      @PathVariable UUID id, @Valid @RequestBody(required = false) KeyIn body) {
    return service.createKey(id, body);
  }

  @Operation(operationId = "revokeMachineKey", summary = "Revoke a key")
  @ApiResponse(responseCode = "204", description = "Key revoked (or already revoked).")
  @ApiResponse(
      responseCode = "404",
      description = "KEY_NOT_FOUND or MACHINE_NOT_FOUND",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers(
      value = {},
      operator = true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @DeleteMapping("/v1/machines/{id}/keys/{keyId}")
  public void revoke(@PathVariable UUID id, @PathVariable UUID keyId) {
    service.revokeKey(id, keyId);
  }

  @Operation(operationId = "verifyMachineKey", summary = "Verify a machine key (internal)")
  @ApiResponse(responseCode = "200", description = "Key is valid.")
  @ApiResponse(
      responseCode = "422",
      description = "VALIDATION_ERROR",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @AllowedCallers({"gateway"})
  @PostMapping("/v1/internal/machine-keys/verify")
  public MachineKeyVerifyOut verify(@Valid @RequestBody MachineKeyVerifyIn body) {
    return service.verifyKey(body.machineKey());
  }
}
