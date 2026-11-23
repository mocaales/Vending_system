package si.vending.machine.machine;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response models of the machine API (openapi.yaml). Field limits are stated here once;
 * the frontend matches them.
 */
public final class MachineDtos {

  private MachineDtos() {}

  public record SlotIn(
      @NotNull @Size(min = 1, max = 8) @NoControlChars String slotCode,
      @Size(max = 40) @NoControlChars String label) {}

  public record SlotOut(String slotCode, String label) {}

  public record MachineCreate(
      @NotNull @Size(min = 1, max = 32) @NoControlChars String code,
      @NotNull @Size(min = 1, max = 100) @NoControlChars String name,
      @NotNull @Size(min = 1, max = 200) @NoControlChars String address,
      @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
      @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
      @NotNull @Size(min = 1, max = 100) @UniqueSlotCodes List<@NotNull @Valid SlotIn> slots) {}

  public record MachineUpdate(
      @NotNull @Size(min = 1, max = 100) @NoControlChars String name,
      @NotNull @Size(min = 1, max = 200) @NoControlChars String address,
      @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
      @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}

  public record MachineStatusIn(@NotNull MachineStatus status) {}

  public record MachineOut(
      UUID id,
      String code,
      String name,
      String address,
      double latitude,
      double longitude,
      MachineStatus status,
      List<SlotOut> slots,
      Instant createdAt,
      Instant updatedAt) {

    static MachineOut of(Machine m) {
      return new MachineOut(
          m.id(),
          m.code(),
          m.name(),
          m.address(),
          m.latitude(),
          m.longitude(),
          m.status(),
          m.slots().stream().map(s -> new SlotOut(s.slotCode(), s.label())).toList(),
          m.createdAt(),
          m.updatedAt());
    }
  }

  /** {@link MachineOut} plus the first key; {@code machineKey} appears only in this response. */
  public record MachineCreated(
      UUID id,
      String code,
      String name,
      String address,
      double latitude,
      double longitude,
      MachineStatus status,
      List<SlotOut> slots,
      Instant createdAt,
      Instant updatedAt,
      String machineKey,
      UUID keyId) {

    /** The one-time machine key is left out so it never reaches logs. */
    @Override
    public String toString() {
      return "MachineCreated[id="
          + id
          + ", code="
          + code
          + ", keyId="
          + keyId
          + ", machineKey=***]";
    }

    static MachineCreated of(MachineOut m, String machineKey, UUID keyId) {
      return new MachineCreated(
          m.id(),
          m.code(),
          m.name(),
          m.address(),
          m.latitude(),
          m.longitude(),
          m.status(),
          m.slots(),
          m.createdAt(),
          m.updatedAt(),
          machineKey,
          keyId);
    }
  }
}
