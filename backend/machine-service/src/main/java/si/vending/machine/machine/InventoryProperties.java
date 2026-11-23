package si.vending.machine.machine;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where Inventory is and how this service identifies itself to it ({@code inventory.base-url} from
 * {@code INVENTORY_URL}, {@code inventory.service-token} from {@code MACHINE_SERVICE_TOKEN}).
 */
@ConfigurationProperties("inventory")
public record InventoryProperties(String baseUrl, String serviceToken) {}
