package si.vending.sales.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import si.vending.sales.sales.SaleDtos.PaymentMethod;
import si.vending.sales.sales.SaleDtos.ReconciliationState;
import si.vending.sales.sales.SaleDtos.Resolution;

/**
 * A stored sale. Read-only in code: rows are inserted by {@link SaleRepository#insertIfAbsent} and
 * the database refuses any change of the sale itself (trigger {@code sales_immutable}).
 */
@Entity
@Table(name = "sales")
public class Sale {

  @Id
  @Column(name = "sale_id", updatable = false)
  private UUID saleId;

  @Column(name = "machine_id", updatable = false)
  private UUID machineId;

  @Column(name = "slot_code", updatable = false)
  private String slotCode;

  @Column(name = "product_id", updatable = false)
  private UUID productId;

  @Column(updatable = false)
  private int quantity;

  @Column(name = "unit_price_paid", updatable = false)
  private BigDecimal unitPricePaid;

  @Column(updatable = false, columnDefinition = "char(3)")
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", updatable = false)
  private PaymentMethod paymentMethod;

  @Column(name = "occurred_at", updatable = false)
  private Instant occurredAt;

  @Column(name = "received_at", updatable = false)
  private Instant receivedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "inventory_state")
  private ReconciliationState inventoryState;

  @Column(name = "inventory_reason")
  private String inventoryReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "resolution")
  private Resolution resolution;

  @Column(name = "note")
  private String note;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  protected Sale() {}

  public UUID saleId() {
    return saleId;
  }

  public UUID machineId() {
    return machineId;
  }

  public String slotCode() {
    return slotCode;
  }

  public UUID productId() {
    return productId;
  }

  public int quantity() {
    return quantity;
  }

  public BigDecimal unitPricePaid() {
    return unitPricePaid;
  }

  public String currency() {
    return currency;
  }

  public PaymentMethod paymentMethod() {
    return paymentMethod;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public Instant receivedAt() {
    return receivedAt;
  }

  public ReconciliationState inventoryState() {
    return inventoryState;
  }

  public Resolution resolution() {
    return resolution;
  }

  public String note() {
    return note;
  }

  public Instant resolvedAt() {
    return resolvedAt;
  }

  public UUID resolvedBy() {
    return resolvedBy;
  }

  public String inventoryReason() {
    return inventoryReason;
  }
}
