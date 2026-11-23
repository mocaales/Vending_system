package si.vending.machine.machine;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import si.vending.machine.core.ApiException;
import si.vending.machine.core.Page;
import si.vending.machine.machine.MachineDtos.MachineCreate;
import si.vending.machine.machine.MachineDtos.MachineCreated;
import si.vending.machine.machine.MachineDtos.MachineOut;
import si.vending.machine.machine.MachineDtos.MachineUpdate;

@Service
public class MachineService {

  private final MachineRepository machines;
  private final MachineKeyRepository keys;
  private final InventoryClient inventory;
  private final TransactionTemplate tx;

  public MachineService(
      MachineRepository machines,
      MachineKeyRepository keys,
      InventoryClient inventory,
      PlatformTransactionManager transactionManager) {
    this.machines = machines;
    this.keys = keys;
    this.inventory = inventory;
    this.tx = new TransactionTemplate(transactionManager);
  }

  private static Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "MACHINE_NOT_FOUND", "Machine not found.");
  }

  private static ApiException retired() {
    return new ApiException(
        HttpStatus.CONFLICT, "MACHINE_RETIRED", "A retired machine cannot be changed.");
  }

  @Transactional
  public MachineCreated create(MachineCreate in) {
    if (machines.existsByCode(in.code())) {
      throw codeTaken();
    }
    Instant now = now();
    List<Slot> slots = in.slots().stream().map(s -> new Slot(s.slotCode(), s.label())).toList();
    Machine machine =
        new Machine(
            UUID.randomUUID(),
            in.code(),
            in.name(),
            in.address(),
            in.latitude(),
            in.longitude(),
            slots,
            now);
    try {
      machines.saveAndFlush(machine);
    } catch (DataIntegrityViolationException e) {
      if (isCodeConstraint(e)) {
        throw codeTaken(); // lost a race with another create of the same code
      }
      throw e;
    }
    String secret = MachineKeys.generate();
    MachineKey key =
        new MachineKey(
            UUID.randomUUID(),
            machine.id(),
            MachineKeys.hash(secret),
            MachineKeys.hint(secret),
            null,
            now);
    keys.save(key);
    return MachineCreated.of(MachineOut.of(machine), secret, key.id());
  }

  private static boolean isCodeConstraint(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof org.hibernate.exception.ConstraintViolationException c
          && c.getConstraintName() != null
          && c.getConstraintName().toLowerCase(Locale.ROOT).contains("machines_code_key")) {
        return true;
      }
    }
    return false;
  }

  private static ApiException codeTaken() {
    return new ApiException(
        HttpStatus.CONFLICT, "MACHINE_CODE_TAKEN", "Another machine already uses this code.");
  }

  @Transactional(readOnly = true)
  public MachineOut get(UUID id) {
    return MachineOut.of(machines.findById(id).orElseThrow(MachineService::notFound));
  }

  @Transactional(readOnly = true)
  public Page<MachineOut> list(
      MachineStatus status, double[] bbox, String q, int limit, int offset) {
    Specification<Machine> spec = (root, query, cb) -> cb.conjunction();
    if (status != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    } else {
      spec = spec.and((root, query, cb) -> cb.notEqual(root.get("status"), MachineStatus.RETIRED));
    }
    if (bbox != null) {
      spec =
          spec.and((root, query, cb) ->
              cb.and(
                  cb.between(root.<Double>get("longitude"), bbox[0], bbox[2]),
                  cb.between(root.<Double>get("latitude"), bbox[1], bbox[3])));
    }
    if (q != null && !q.isEmpty()) {
      String pattern = "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
      spec =
          spec.and((root, query, cb) -> {
            List<Predicate> any = new ArrayList<>();
            for (String field : List.of("code", "name", "address")) {
              any.add(cb.like(cb.lower(root.get(field)), pattern, '\\'));
            }
            return cb.or(any.toArray(Predicate[]::new));
          });
    }
    org.springframework.data.domain.Page<Machine> result =
        machines.findAll(
            spec, new OffsetPageRequest(limit, offset, Sort.by("code").and(Sort.by("id"))));
    return new Page<>(
        result.getContent().stream().map(MachineOut::of).toList(),
        result.getTotalElements(),
        limit,
        offset);
  }

  private static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  @Transactional
  public MachineOut update(UUID id, MachineUpdate in) {
    Machine machine = machines.findForUpdate(id).orElseThrow(MachineService::notFound);
    if (machine.status() == MachineStatus.RETIRED) {
      throw retired();
    }
    machine.replaceDetails(in.name(), in.address(), in.latitude(), in.longitude(), now());
    return MachineOut.of(machine);
  }

  @Transactional
  public MachineOut setStatus(UUID id, MachineStatus target) {
    Machine machine = machines.findForUpdate(id).orElseThrow(MachineService::notFound);
    if (machine.status() == MachineStatus.RETIRED) {
      throw retired();
    }
    if (target == MachineStatus.RETIRED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "INVALID_STATE_TRANSITION",
          "Use DELETE /v1/machines/{id} to retire a machine.");
    }
    if (machine.status() != target) {
      machine.changeStatus(target, now());
    }
    return MachineOut.of(machine);
  }

  /**
   * Retires a machine: refused while Inventory still holds stock for it; revokes all its keys.
   * Repeating the call is a no-op and does not ask Inventory. The Inventory call runs outside any
   * database transaction.
   */
  public void retire(UUID id) {
    MachineStatus current =
        tx.execute(
            s -> machines.findById(id).map(Machine::status).orElseThrow(MachineService::notFound));
    if (current == MachineStatus.RETIRED) {
      return;
    }
    if (inventory.hasStock(id)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "MACHINE_HAS_STOCK",
          "Inventory still holds stock for this machine.");
    }
    tx.executeWithoutResult(s -> {
      Machine machine = machines.findForUpdate(id).orElseThrow(MachineService::notFound);
      if (machine.status() == MachineStatus.RETIRED) {
        return;
      }
      Instant now = now();
      machine.changeStatus(MachineStatus.RETIRED, now);
      keys.revokeAll(id, now);
    });
  }
}
