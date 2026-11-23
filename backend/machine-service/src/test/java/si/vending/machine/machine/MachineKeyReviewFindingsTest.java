package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineKeyApiTest;

/** #18 security-review findings: L1 (verify key length), I1 (key cap race), I5 (numeric enum). */
class MachineKeyReviewFindingsTest extends MachineKeyApiTest {

  // --- L1: machineKey has a maximum length of 128

  @Test
  void keyOf128CharsIsAcceptedAsInputAndUnknown() {
    assertError(verify("a".repeat(128)), 401, "UNAUTHENTICATED");
  }

  @Test
  void keyOf129CharsIsRejectedAsValidation() {
    Http.Response response = verify("a".repeat(129));

    assertInvalidField(response, "machineKey");
  }

  @Test
  void megabyteKeyIsRejectedAndNeverOkOrServerError() {
    Http.Response response = verify("a".repeat(1_000_000));

    assertThat(response.status()).isNotIn(200, 500).isIn(400, 413, 422);
    assertThat(response.status()).isEqualTo(422);
    assertThat(response.json().get("code")).isEqualTo("VALIDATION_ERROR");
  }

  @Test
  @SuppressWarnings("unchecked")
  void openApiDeclaresMaxLength128ForVerifyKey() throws Exception {
    Map<String, Object> spec;
    try (Reader reader = Files.newBufferedReader(Path.of("openapi.yaml"))) {
      spec = new Yaml().load(reader);
    }
    Map<String, Object> schemas =
        (Map<String, Object>) ((Map<String, Object>) spec.get("components")).get("schemas");
    Map<String, Object> verifyIn = (Map<String, Object>) schemas.get("MachineKeyVerifyIn");
    Map<String, Object> props = (Map<String, Object>) verifyIn.get("properties");
    Map<String, Object> key = (Map<String, Object>) props.get("machineKey");

    assertThat(key.get("maxLength")).isEqualTo(128);
  }

  // --- I1: the 3-active-key cap holds under concurrency (regression guard)

  @Test
  void parallelKeyCreationNeverExceedsThreeActiveKeys() throws Exception {
    Created m = newMachineWithKey();
    int threads = 6;
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<Future<Http.Response>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(() -> {
              ready.countDown();
              go.await();
              return createKey(m.machineId());
            }));
      }
      ready.await();
      go.countDown();
      int created = 0;
      int limited = 0;
      for (Future<Http.Response> future : futures) {
        Http.Response response = future.get();
        if (response.status() == 201) {
          created++;
        } else {
          assertError(response, 409, "KEY_LIMIT_REACHED");
          limited++;
        }
      }
      assertThat(created).isEqualTo(2);
      assertThat(limited).isEqualTo(threads - 2);
    } finally {
      pool.shutdownNow();
    }

    long active =
        listKeys(m.machineId()).items().stream()
            .filter(k -> "ACTIVE".equals(((Map<?, ?>) k).get("status")))
            .count();
    assertThat(active).isEqualTo(3);
  }

  // --- I5: numbers are not accepted for enum fields

  @Test
  void numericStatusIsRejectedAndChangesNothing() {
    String id = newMachine(1);
    String path = "/v1/machines/" + id + "/status";

    assertRejected(put(path, "{\"status\":1}", operator()));
    assertRejected(put(path, "{\"status\":0}", operator()));

    assertThat(statusOf(id)).isEqualTo("ACTIVE");
  }
}
