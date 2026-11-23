package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.machine.machine.MachineDtos.MachineCreated;
import si.vending.machine.machine.MachineDtos.MachineOut;
import si.vending.machine.machine.MachineDtos.SlotOut;

/**
 * #17 security review, finding I4: the one-time machine key is returned in the create response
 * only. A record's default {@code toString()} would print it wherever the object is logged or
 * traced, so the key must be left out of {@code toString()}.
 */
class MachineCreatedTest {

  private static final String KEY = "mk_SECRET-VALUE-do-not-log-4f9a";

  @Test
  void toStringDoesNotContainTheMachineKey() {
    MachineOut out =
        new MachineOut(
            UUID.randomUUID(),
            "C-1",
            "Name",
            "Address",
            46.5,
            15.6,
            MachineStatus.ACTIVE,
            List.of(new SlotOut("A1", null)),
            Instant.parse("2026-11-23T10:00:00Z"),
            Instant.parse("2026-11-23T10:00:00Z"));
    MachineCreated created = MachineCreated.of(out, KEY, UUID.randomUUID());

    assertThat(created.toString()).doesNotContain(KEY);
  }

  @Test
  void toStringStillIdentifiesTheMachine() {
    UUID id = UUID.randomUUID();
    MachineOut out =
        new MachineOut(
            id,
            "C-2",
            "Name",
            "Address",
            46.5,
            15.6,
            MachineStatus.ACTIVE,
            List.of(),
            Instant.parse("2026-11-23T10:00:00Z"),
            Instant.parse("2026-11-23T10:00:00Z"));

    assertThat(MachineCreated.of(out, KEY, UUID.randomUUID()).toString()).contains(id.toString());
  }
}
