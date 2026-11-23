package si.vending.machine.machine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MachineKeyRepository extends JpaRepository<MachineKey, UUID> {

  /** Revokes every active key of the machine; returns the number of keys revoked. */
  @Modifying
  @Query(
      "update MachineKey k set k.status = si.vending.machine.machine.KeyStatus.REVOKED,"
          + " k.revokedAt = :now where k.machineId = :machineId"
          + " and k.status = si.vending.machine.machine.KeyStatus.ACTIVE")
  int revokeAll(@Param("machineId") UUID machineId, @Param("now") Instant now);

  Optional<MachineKey> findByKeyHash(String keyHash);

  Optional<MachineKey> findByIdAndMachineId(UUID id, UUID machineId);

  List<MachineKey> findByMachineIdOrderByCreatedAtAscIdAsc(UUID machineId);

  long countByMachineIdAndStatus(UUID machineId, KeyStatus status);
}
