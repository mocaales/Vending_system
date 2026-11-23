package si.vending.machine.machine;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MachineRepository
    extends JpaRepository<Machine, UUID>, JpaSpecificationExecutor<Machine> {

  boolean existsByCode(String code);

  /** Loads the machine with a row lock, to serialise changes of its status. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Machine m where m.id = :id")
  Optional<Machine> findForUpdate(@Param("id") UUID id);
}
