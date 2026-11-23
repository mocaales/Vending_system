package si.vending.machine.machine;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A fixed slot of a machine; slots do not change after creation. */
@Embeddable
public class Slot {

  @Column(name = "slot_code", nullable = false, length = 8, updatable = false)
  private String slotCode;

  @Column(name = "label", length = 40, updatable = false)
  private String label;

  protected Slot() {}

  public Slot(String slotCode, String label) {
    this.slotCode = slotCode;
    this.label = label;
  }

  public String slotCode() {
    return slotCode;
  }

  public String label() {
    return label;
  }
}
