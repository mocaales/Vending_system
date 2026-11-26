package si.vending.monitoring.notification;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import si.vending.monitoring.alert.AlertDtos.AlertOut;
import si.vending.monitoring.alert.AlertDtos.LowStockSlot;
import si.vending.monitoring.measurement.MachineClient;

/** Builds the subject and text of an alarm e-mail; lookups of names never fail the mail. */
@Component
public class NotificationComposer {

  public record Mail(String subject, String body) {}

  private static final Pattern CONTROL =
      Pattern.compile(
          "[\\p{Cntrl}\\p{Zl}\\p{Zp}\\u0085\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]+");

  /** Product lookups per mail; further products are shown by id (a slow Product never stalls). */
  static final int MAX_PRODUCT_LOOKUPS = 10;

  private final MachineClient machines;
  private final ProductClient products;

  public NotificationComposer(MachineClient machines, ProductClient products) {
    this.machines = machines;
    this.products = products;
  }

  public Mail compose(AlertOut alert) {
    String machine = machines.name(alert.machineId()).map(NotificationComposer::clean).orElse(null);
    String machineLabel = machine == null ? alert.machineId().toString() : machine;
    StringBuilder body = new StringBuilder();
    body.append("Alarm: ").append(alert.type()).append('\n');
    body.append("Avtomat: ").append(machineLabel);
    if (machine != null) {
      body.append(" (").append(alert.machineId()).append(')');
    }
    body.append('\n').append("Odprt (UTC): ").append(alert.openedAt()).append('\n');
    if (alert.slots() != null && !alert.slots().isEmpty()) {
      body.append("\nPredali z nizko zalogo:\n");
      Map<UUID, String> names = new HashMap<>();
      for (LowStockSlot slot : alert.slots()) {
        String name =
            names.computeIfAbsent(
                slot.productId(),
                id ->
                    names.size() >= MAX_PRODUCT_LOOKUPS
                        ? id.toString()
                        : products
                            .name(id)
                            .map(NotificationComposer::clean)
                            .filter(n -> !n.isEmpty())
                            .orElse(id.toString()));
        body.append("- ")
            .append(slot.slotCode())
            .append(": ")
            .append(name)
            .append(", zaloga ")
            .append(slot.quantity())
            .append(" (prag ")
            .append(slot.lowStockThreshold())
            .append(")\n");
      }
    }
    return new Mail("[Vending] Alarm " + alert.type() + ": " + machineLabel, body.toString());
  }

  /** Names come from other services: no control characters (header injection) and bounded. */
  private static String clean(String value) {
    String cleaned = CONTROL.matcher(value).replaceAll(" ").strip();
    return cleaned.codePointCount(0, cleaned.length()) > 200
        ? cleaned.substring(0, cleaned.offsetByCodePoints(0, 200))
        : cleaned;
  }
}
