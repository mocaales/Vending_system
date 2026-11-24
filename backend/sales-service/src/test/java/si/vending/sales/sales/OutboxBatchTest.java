package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.vending.sales.sales.support.OutboxTest;

/** {@code outbox.batch-size} bounds one pass; the rest stays open for the next one. */
class OutboxBatchTest extends OutboxTest {

  @DynamicPropertySource
  static void smallBatch(DynamicPropertyRegistry registry) {
    registry.add("outbox.batch-size", () -> "2");
  }

  @Test
  void onePassHandlesAtMostBatchSizeEntries() {
    List<String> saleIds = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      saleIds.add(accept(knownSlot()));
    }

    runOnce();
    assertThat(INVENTORY.decreaseCalls()).hasSize(2);
    assertThat(saleIds.stream().filter(this::isDone).count()).isEqualTo(2);

    runOnce();
    assertThat(INVENTORY.decreaseCalls()).hasSize(3);
    assertThat(saleIds).allMatch(this::isDone);
    assertThat(saleIds).allMatch(id -> stateOf(id).equals("APPLIED"));
  }
}
