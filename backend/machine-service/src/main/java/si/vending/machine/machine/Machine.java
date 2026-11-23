package si.vending.machine.machine;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "machines")
public class Machine {

  @Id private UUID id;

  @Column(nullable = false, length = 32, updatable = false)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 200)
  private String address;

  @Column(nullable = false)
  private double latitude;

  @Column(nullable = false)
  private double longitude;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private MachineStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ElementCollection
  @CollectionTable(name = "machine_slots", joinColumns = @JoinColumn(name = "machine_id"))
  @OrderColumn(name = "position")
  @BatchSize(size = 100)
  private List<Slot> slots = new ArrayList<>();

  protected Machine() {}

  public Machine(
      UUID id,
      String code,
      String name,
      String address,
      double latitude,
      double longitude,
      List<Slot> slots,
      Instant now) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.address = address;
    this.latitude = latitude;
    this.longitude = longitude;
    this.status = MachineStatus.ACTIVE;
    this.slots = new ArrayList<>(slots);
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Replaces the editable fields; returns whether anything changed. */
  public boolean replaceDetails(
      String name, String address, double latitude, double longitude, Instant now) {
    boolean changed =
        !this.name.equals(name)
            || !this.address.equals(address)
            || this.latitude != latitude
            || this.longitude != longitude;
    if (changed) {
      this.name = name;
      this.address = address;
      this.latitude = latitude;
      this.longitude = longitude;
      this.updatedAt = now;
    }
    return changed;
  }

  public void changeStatus(MachineStatus status, Instant now) {
    this.status = status;
    this.updatedAt = now;
  }

  public UUID id() {
    return id;
  }

  public String code() {
    return code;
  }

  public String name() {
    return name;
  }

  public String address() {
    return address;
  }

  public double latitude() {
    return latitude;
  }

  public double longitude() {
    return longitude;
  }

  public MachineStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public List<Slot> slots() {
    return slots;
  }
}
