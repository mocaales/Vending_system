package si.vending.machine.machine;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where Inventory is and how this service identifies itself to it ({@code
 * inventory-client.base-url} from {@code INVENTORY_URL}, {@code inventory-client.service-token}
 * from {@code MACHINE_SERVICE_TOKEN}).
 *
 * <p>The prefix is not {@code inventory}: Spring's relaxed binding would then read the environment
 * variable {@code INVENTORY_SERVICE_TOKEN} as {@code inventory.service-token} and send Inventory's
 * own token instead of Machine's.
 */
@ConfigurationProperties("inventory-client")
public record InventoryProperties(String baseUrl, String serviceToken) {}
