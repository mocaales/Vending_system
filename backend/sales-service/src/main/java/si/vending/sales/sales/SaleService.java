package si.vending.sales.sales;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import si.vending.sales.core.ApiException;
import si.vending.sales.sales.SaleDtos.SaleEvent;
import si.vending.sales.sales.SaleDtos.SaleOut;

/**
 * Sale intake. Not transactional as a whole: Product and Inventory are called outside any database
 * transaction, and the single INSERT ... ON CONFLICT DO NOTHING decides who stored a {@code saleId}
 * first.
 */
@Service
public class SaleService {

  private static final Duration MAX_FUTURE = Duration.ofMinutes(5);

  /** The stored sale and whether this call created it (201) or found it (200). */
  public record Recorded(SaleOut sale, boolean created) {}

  private final SaleRepository repository;
  private final ProductClient products;
  private final InventoryClient inventory;

  public SaleService(SaleRepository repository, ProductClient products, InventoryClient inventory) {
    this.repository = repository;
    this.products = products;
    this.inventory = inventory;
  }

  public Recorded record(SaleEvent event, UUID machineId) {
    checkMachine(event, machineId);
    Sale existing = repository.findById(event.saleId()).orElse(null);
    if (existing != null) {
      return replay(existing, event);
    }
    Instant now = Instant.now();
    Instant occurredAt = event.occurredAt().truncatedTo(ChronoUnit.MICROS);
    if (occurredAt.isAfter(now.plus(MAX_FUTURE))) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "TIMESTAMP_OUT_OF_RANGE",
          "occurredAt is more than 5 minutes in the future.");
    }
    if (!products.exists(event.productId())) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY, "REFERENCE_NOT_FOUND", "Unknown product.");
    }
    if (!inventory.slotExists(event.machineId(), event.slotId())) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY, "REFERENCE_NOT_FOUND", "Unknown slot.");
    }
    int inserted =
        repository.insertIfAbsent(
            event.saleId(),
            event.machineId(),
            event.slotId(),
            event.productId(),
            event.quantity(),
            new BigDecimal(event.unitPricePaid()),
            event.currency(),
            event.paymentMethod().name(),
            occurredAt,
            now.truncatedTo(ChronoUnit.MICROS));
    Sale stored = repository.findById(event.saleId()).orElseThrow();
    if (inserted == 1) {
      return new Recorded(SaleOut.of(stored), true);
    }
    return replay(stored, event); // another request stored the same saleId first
  }

  /** The body's machineId must be the machine the guard authenticated. */
  private static void checkMachine(SaleEvent event, UUID authenticated) {
    if (!event.machineId().equals(authenticated)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "MACHINE_KEY_MISMATCH",
          "The machine key belongs to another machine than machineId.");
    }
  }

  private static Recorded replay(Sale stored, SaleEvent event) {
    if (!sameContent(stored, event)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "SALE_ID_CONFLICT",
          "A sale with this saleId already exists with different content.");
    }
    return new Recorded(SaleOut.of(stored), false);
  }

  private static boolean sameContent(Sale s, SaleEvent e) {
    return s.machineId().equals(e.machineId())
        && s.slotCode().equals(e.slotId())
        && s.productId().equals(e.productId())
        && s.quantity() == e.quantity()
        && s.unitPricePaid().compareTo(new BigDecimal(e.unitPricePaid())) == 0
        && s.currency().equals(e.currency())
        && s.paymentMethod() == e.paymentMethod()
        && s.occurredAt().equals(e.occurredAt().truncatedTo(ChronoUnit.MICROS));
  }
}
