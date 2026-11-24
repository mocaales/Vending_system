package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.ClosingTest;

/**
 * #55: GET /v1/sales/closings (filters from, to, machineId, status; pagination) and GET
 * /v1/sales/closings/{id}; the static path is never read as a sale id.
 *
 * <p>Not pinned: the order across dates (newest date first, openapi.yaml). A closing can only be
 * created for yesterday and the tests cannot move the clock, so every closing here has one date.
 */
class ClosingReadTest extends ClosingTest {

  private static List<Object> idsOf(Http.Response response) {
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    List<Object> ids = new ArrayList<>();
    for (Map<String, Object> item : items(response)) {
      ids.add(item.get("id"));
    }
    return ids;
  }

  // --- get by id

  @Test
  void getByIdReturnsTheClosingToOperatorAndFrontendApi() {
    UUID machine = UUID.randomUUID();
    saleOnYesterday(machine, 1, "2.50", "EUR");
    Map<String, Object> created = created(machine);
    String path = "/v1/sales/closings/" + created.get("id");

    Http.Response asOperator = get(path, operator());
    Http.Response asFrontend = get(path, frontendApi());

    assertThat(asOperator.status()).isEqualTo(200);
    assertThat(asOperator.json()).isEqualTo(created);
    assertThat(asFrontend.status()).isEqualTo(200);
    assertThat(asFrontend.json()).isEqualTo(created);
  }

  @Test
  void anUnknownIdIs404ClosingNotFoundNotSaleNotFound() {
    assertError(getClosing(UUID.randomUUID()), 404, "CLOSING_NOT_FOUND");
  }

  @Test
  void aMalformedIdIs400() {
    assertError(getClosing("not-a-uuid"), 400, "BAD_REQUEST");
  }

  // --- routes

  @Test
  void theStaticPathIsTheListNotASaleLookup() {
    Fx sale = saleAt(UUID.randomUUID(), now().minusSeconds(60));

    Http.Response list = asOperator("/v1/sales/closings");

    assertThat(list.status()).as(list.body()).isEqualTo(200);
    assertThat(list.json().keySet()).containsExactlyInAnyOrder("items", "total", "limit", "offset");
    // the sale route still works next to it
    assertThat(asOperator("/v1/sales/" + sale.saleId()).status()).isEqualTo(200);
    assertError(asOperator("/v1/sales/" + UUID.randomUUID()), 404, "SALE_NOT_FOUND");
  }

  @Test
  void theDeleteOnTheStaticPathIsTheDateDeleteNotAMissingRoute() {
    Http.Response response = deleteDate(null, operator());

    assertInvalidField(response, "date");
  }

  // --- list

  @Test
  void listHasThePageShapeWithDefaults() {
    created(UUID.randomUUID());
    String day = yesterday().toString();

    Http.Response response = asOperator("/v1/sales/closings?from=" + day + "&to=" + day);

    assertThat(response.json().keySet())
        .containsExactlyInAnyOrder("items", "total", "limit", "offset");
    assertThat(response.json().get("limit")).isEqualTo(50);
    assertThat(response.json().get("offset")).isEqualTo(0);
    assertThat((Integer) response.json().get("total")).isGreaterThanOrEqualTo(1);
    items(response).forEach(ClosingTest::assertClosingShape);
  }

  @Test
  void machineIdFilterReturnsOnlyThatMachinesClosing() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    Map<String, Object> closingA = created(a);
    created(b);
    created(null);

    Http.Response response = asOperator("/v1/sales/closings?machineId=" + a);

    assertThat(idsOf(response)).containsExactly(closingA.get("id"));
    assertThat(response.json().get("total")).isEqualTo(1);
  }

  @Test
  void withoutMachineIdTheAllMachinesClosingIsListedToo() {
    UUID a = UUID.randomUUID();
    Map<String, Object> closingA = created(a);
    Map<String, Object> all = created(null);
    String day = yesterday().toString();

    List<Object> ids =
        idsOf(asOperator("/v1/sales/closings?from=" + day + "&to=" + day + "&limit=200"));

    assertThat(ids).contains(closingA.get("id"), all.get("id"));
  }

  @Test
  void fromAndToAreInclusiveDates() {
    UUID machine = UUID.randomUUID();
    Map<String, Object> closing = created(machine);
    LocalDate day = yesterday();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    String base = "/v1/sales/closings?machineId=" + machine;

    assertThat(idsOf(asOperator(base + "&from=" + day + "&to=" + day)))
        .containsExactly(closing.get("id"));
    assertThat(idsOf(asOperator(base + "&from=" + day))).containsExactly(closing.get("id"));
    assertThat(idsOf(asOperator(base + "&to=" + day))).containsExactly(closing.get("id"));
    assertThat(idsOf(asOperator(base + "&from=" + day.minusDays(5) + "&to=" + today)))
        .containsExactly(closing.get("id"));
    assertThat(idsOf(asOperator(base + "&from=" + today))).isEmpty();
    assertThat(idsOf(asOperator(base + "&to=" + day.minusDays(1)))).isEmpty();
  }

  @Test
  void fromAfterToIs422() {
    Http.Response response =
        asOperator("/v1/sales/closings?from=" + yesterday() + "&to=" + yesterday().minusDays(3));

    assertError(response, 422, "VALIDATION_ERROR");
  }

  @Test
  void statusFilterSeparatesDraftsFromConfirmedClosings() {
    UUID draftMachine = UUID.randomUUID();
    UUID confirmedMachine = UUID.randomUUID();
    Map<String, Object> draft = created(draftMachine);
    Map<String, Object> confirmed = created(confirmedMachine);
    assertThat(confirm(confirmed.get("id"), null).status()).isEqualTo(200);
    String d = "/v1/sales/closings?machineId=" + draftMachine;
    String c = "/v1/sales/closings?machineId=" + confirmedMachine;

    assertThat(idsOf(asOperator(d + "&status=DRAFT"))).containsExactly(draft.get("id"));
    assertThat(idsOf(asOperator(d + "&status=CONFIRMED"))).isEmpty();
    assertThat(idsOf(asOperator(c + "&status=CONFIRMED"))).containsExactly(confirmed.get("id"));
    assertThat(idsOf(asOperator(c + "&status=DRAFT"))).isEmpty();
    assertThat(idsOf(asOperator(c))).containsExactly(confirmed.get("id"));
  }

  @Test
  void anUnknownStatusIs400Or422() {
    assertThat(asOperator("/v1/sales/closings").status())
        .as("the list route exists")
        .isEqualTo(200);
    Http.Response response = asOperator("/v1/sales/closings?status=DONE");

    assertThat(response.status()).isIn(400, 422);
    assertThat(response.json().get("code")).isIn("BAD_REQUEST", "VALIDATION_ERROR");
  }

  @Test
  void unparseableFilterValuesAre400Or422() {
    assertThat(asOperator("/v1/sales/closings").status())
        .as("the list route exists")
        .isEqualTo(200);
    for (String bad :
        new String[] {
          "from=abc", "to=2026-13-45", "from=" + yesterday() + "T00:00:00Z", "machineId=not-a-uuid"
        }) {
      Http.Response response = asOperator("/v1/sales/closings?" + bad);
      assertThat(response.status()).as(bad).isIn(400, 422);
    }
  }

  @Test
  void paginationWalksEveryClosingOnceWithoutDuplicates() {
    Set<Object> mine = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      mine.add(created(UUID.randomUUID()).get("id"));
    }
    String day = yesterday().toString();
    String base = "/v1/sales/closings?from=" + day + "&to=" + day + "&limit=2";

    Http.Response first = asOperator(base);
    int total = (Integer) first.json().get("total");
    assertThat(total).isGreaterThanOrEqualTo(3);
    assertThat(first.json().get("limit")).isEqualTo(2);
    assertThat(items(first)).hasSize(2);

    List<Object> seen = new ArrayList<>();
    for (int offset = 0; offset < total; offset += 2) {
      Http.Response page = asOperator(base + "&offset=" + offset);
      assertThat(page.json().get("offset")).isEqualTo(offset);
      assertThat(page.json().get("total")).isEqualTo(total);
      assertThat(items(page)).hasSize(Math.min(2, total - offset));
      seen.addAll(idsOf(page));
    }
    assertThat(seen).hasSize(total).doesNotHaveDuplicates().containsAll(mine);
    assertThat(idsOf(asOperator(base + "&offset=" + total))).isEmpty();
  }

  @Test
  void limitOutOfRangeIsRejected() {
    assertThat(asOperator("/v1/sales/closings").status())
        .as("the list route exists")
        .isEqualTo(200);
    for (String bad : new String[] {"limit=0", "limit=201", "limit=-1", "offset=-1"}) {
      Http.Response response = asOperator("/v1/sales/closings?" + bad);
      assertThat(response.status()).as(bad).isIn(400, 422);
    }
    assertError(asOperator("/v1/sales/closings?limit=abc"), 400, "BAD_REQUEST");
  }
}
