package si.vending.machine.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.AbstractApiTest;
import si.vending.machine.core.support.Http;

/**
 * docs/03 section 1: lists are {items, total, limit, offset}; limit defaults to 50, at most 200.
 * Assumed: an out-of-range limit or offset is rejected with 422 VALIDATION_ERROR (not clamped). The
 * test route serves 300 items.
 */
class PaginationTest extends AbstractApiTest {

  @Test
  void defaultsAreLimit50Offset0() {
    Http.Response response = get("/test/items");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().keySet())
        .containsExactlyInAnyOrder("items", "total", "limit", "offset");
    assertThat(response.json().get("limit")).isEqualTo(50);
    assertThat(response.json().get("offset")).isEqualTo(0);
    assertThat(response.json().get("total")).isEqualTo(300);
    assertThat(response.items()).hasSize(50);
  }

  @Test
  void limitAndOffsetAreHonoured() {
    Http.Response response = get("/test/items?limit=10&offset=295");

    assertThat(response.json().get("limit")).isEqualTo(10);
    assertThat(response.json().get("offset")).isEqualTo(295);
    assertThat(response.items()).hasSize(5);
  }

  @Test
  void limit200IsAllowed() {
    assertThat(get("/test/items?limit=200").items()).hasSize(200);
  }

  @Test
  void limitAbove200IsRejected() {
    assertError(get("/test/items?limit=201"), 422, "VALIDATION_ERROR");
  }

  @Test
  void limitZeroIsRejected() {
    assertError(get("/test/items?limit=0"), 422, "VALIDATION_ERROR");
  }

  @Test
  void negativeOffsetIsRejected() {
    assertError(get("/test/items?offset=-1"), 422, "VALIDATION_ERROR");
  }
}
