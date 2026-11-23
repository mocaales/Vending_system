package si.vending.machine.machine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A key a machine authenticates with. Only the SHA-256 hash of the secret is stored. */
@Entity
@Table(name = "machine_keys")
public class MachineKey {

  @Id private UUID id;

  @Column(name = "machine_id", nullable = false, updatable = false)
  private UUID machineId;

  @Column(name = "key_hash", nullable = false, updatable = false, length = 64)
  private String keyHash;

  @Column(nullable = false, updatable = false, length = 4)
  private String hint;

  @Column(length = 60)
  private String label;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private KeyStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected MachineKey() {}

  public MachineKey(
      UUID id, UUID machineId, String keyHash, String hint, String label, Instant now) {
    this.id = id;
    this.machineId = machineId;
    this.keyHash = keyHash;
    this.hint = hint;
    this.label = label;
    this.status = KeyStatus.ACTIVE;
    this.createdAt = now;
  }

  public UUID id() {
    return id;
  }
}
