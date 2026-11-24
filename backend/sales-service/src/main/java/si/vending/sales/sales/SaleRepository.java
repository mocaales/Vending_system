package si.vending.sales.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

  /**
   * Inserts a new sale in state PENDING; does nothing if the {@code saleId} exists. Returns 1 if
   * inserted, 0 if another request stored the same {@code saleId} first (race-safe, no exception).
   */
  @Transactional
  @Modifying
  @Query(
      value =
          """
          INSERT INTO sales (sale_id, machine_id, slot_code, product_id, quantity,
                             unit_price_paid, currency, payment_method, occurred_at, received_at)
          VALUES (:saleId, :machineId, :slotCode, :productId, :quantity,
                  :unitPricePaid, :currency, :paymentMethod, :occurredAt, :receivedAt)
          ON CONFLICT (sale_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("saleId") UUID saleId,
      @Param("machineId") UUID machineId,
      @Param("slotCode") String slotCode,
      @Param("productId") UUID productId,
      @Param("quantity") int quantity,
      @Param("unitPricePaid") BigDecimal unitPricePaid,
      @Param("currency") String currency,
      @Param("paymentMethod") String paymentMethod,
      @Param("occurredAt") Instant occurredAt,
      @Param("receivedAt") Instant receivedAt);

  /**
   * Closes a sale that needs reconciliation. The WHERE clause makes the transition atomic: of
   * concurrent requests exactly one updates the row. Returns 1 if this call resolved the sale.
   */
  @Transactional
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE sales SET inventory_state = 'RESOLVED', resolution = :resolution, note = :note,
                           resolved_at = :resolvedAt, resolved_by = :resolvedBy
          WHERE sale_id = :saleId AND inventory_state = 'NEEDS_RECONCILIATION'
          """,
      nativeQuery = true)
  int resolve(
      @Param("saleId") UUID saleId,
      @Param("resolution") String resolution,
      @Param("note") String note,
      @Param("resolvedAt") Instant resolvedAt,
      @Param("resolvedBy") UUID resolvedBy);
}
