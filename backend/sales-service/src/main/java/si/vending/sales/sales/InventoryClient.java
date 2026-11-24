package si.vending.sales.sales;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Inventory {@code GET /v1/inventory/{machineId}/{slot}} (docs/03 s. 7). */
@Component
public class InventoryClient {

  private final ReferenceClient client;

  public InventoryClient(DependencyProperties.Inventory properties) {
    this.client =
        new ReferenceClient(
            "Inventory", properties.baseUrl(), properties.serviceToken(), "SLOT_NOT_FOUND");
  }

  /** Whether Inventory knows the slot; an empty slot or another product in it still counts. */
  public boolean slotExists(UUID machineId, String slot) {
    return client.exists("/v1/inventory/{machineId}/{slot}", machineId, slot);
  }
}
