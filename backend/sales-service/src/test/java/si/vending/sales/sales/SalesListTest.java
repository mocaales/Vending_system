package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.SalesReadTest;

/**
 * GET /v1/sales (issue #27): filters machineId, productId, from (inclusive), to (exclusive),
 * reconciliation (the sale's inventory state), AND-ed; newest occurredAt first; {items, total,
 * limit, offset} with limit 1..200 (default 50) and offset >= 0. Out-of-range paging, an inverted
 * time range and times outside 2020-01-01..2100-01-01 are 422 VALIDATION_ERROR; an unparseable
 * value (not a date-time, not a UUID, not an integer) is 400 BAD_REQUEST (docs/03 section 2).
 * Product's own query (productId + NEEDS_RECONCILIATION + limit=1 with Product's token) must find
 * the sale.
 */
class SalesListTest extends SalesReadTest {

  @Test
  void answersTheListShapeWithDefaults() {
    UUID machine = UUID.randomUUID();
    Fx sale = insert(machine, UUID.randomUUID(), now().minusSeconds(60));

    Http.Response response = listOf(machine, "");

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().keySet())
        .containsExactlyInAnyOrder("items", "total", "limit", "offset");
    assertThat(response.json().get("limit")).isEqualTo(50);
    assertThat(response.json().get("offset")).isEqualTo(0);
    assertThat(response.json().get("total")).isEqualTo(1);
    assertThat(items(response)).hasSize(1);
    assertSaleOut(items(response).get(0), sale);
  }

  @Test
  void aMachineWithoutSalesIsAnEmptyPage() {
    Http.Response response = listOf(UUID.randomUUID(), "");

    assertThat(response.status()).isEqualTo(200);
    assertThat(items(response)).isEmpty();
    assertThat(response.json().get("total")).isEqualTo(0);
  }

  @Test
  void newestOccurredAtFirst() {
    UUID machine = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    Instant t = now();
    // inserted out of order on purpose: the order is by occurredAt, not by insertion
    Fx middle = insert(machine, product, t.minus(2, ChronoUnit.HOURS));
    Fx newest = insert(machine, product, t.minus(1, ChronoUnit.HOURS));
    Fx oldest = insert(machine, product, t.minus(3, ChronoUnit.HOURS));

    assertThat(saleIds(listOf(machine, ""))).containsExactlyElementsOf(ids(newest, middle, oldest));
  }

  @Test
  void paginationSlicesTheOrderedListAndTotalIsTheFullCount() {
    UUID machine = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    Instant t = now();
    Fx[] sales = new Fx[5]; // sales[0] is the newest
    for (int i = 0; i < 5; i++) {
      sales[i] = insert(machine, product, t.minus(i + 1, ChronoUnit.HOURS));
    }

    Http.Response page = listOf(machine, "&limit=2&offset=2");

    assertThat(page.status()).isEqualTo(200);
    assertThat(page.json().get("total")).isEqualTo(5);
    assertThat(page.json().get("limit")).isEqualTo(2);
    assertThat(page.json().get("offset")).isEqualTo(2);
    assertThat(saleIds(page)).containsExactlyElementsOf(ids(sales[2], sales[3]));
    assertThat(saleIds(listOf(machine, "&limit=2&offset=4")))
        .containsExactlyElementsOf(ids(sales[4]));
  }

  @Test
  void offsetBeyondTheEndIsAnEmptyPageWithTheRealTotal() {
    UUID machine = UUID.randomUUID();
    insert(machine, UUID.randomUUID(), now().minusSeconds(60));

    Http.Response response = listOf(machine, "&offset=10");

    assertThat(response.status()).isEqualTo(200);
    assertThat(items(response)).isEmpty();
    assertThat(response.json().get("total")).isEqualTo(1);
  }

  @Test
  void limit200IsAllowed() {
    assertThat(listOf(UUID.randomUUID(), "&limit=200").status()).isEqualTo(200);
  }

  @Test
  void limitOutsideOneTo200IsRejected() {
    for (String bad : new String[] {"&limit=0", "&limit=201", "&limit=-1", "&offset=-1"}) {
      assertError(listOf(UUID.randomUUID(), bad), 422, "VALIDATION_ERROR");
    }
  }

  @Test
  void aNonNumericLimitIsABadRequest() {
    assertError(listOf(UUID.randomUUID(), "&limit=abc"), 400, "BAD_REQUEST");
  }

  // --- filters

  @Test
  void filtersByMachine() {
    UUID machine = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    Fx mine = insert(machine, product, now().minusSeconds(60));
    insert(other, product, now().minusSeconds(60));

    Http.Response response = listOf(machine, "");

    assertThat(saleIds(response)).containsExactlyElementsOf(ids(mine));
    assertThat(response.json().get("total")).isEqualTo(1);
  }

  @Test
  void filtersByProduct() {
    UUID machine = UUID.randomUUID();
    UUID milk = UUID.randomUUID();
    UUID eggs = UUID.randomUUID();
    Fx milkSale = insert(machine, milk, now().minusSeconds(60));
    insert(machine, eggs, now().minusSeconds(120));

    Http.Response response = asOperator("/v1/sales?machineId=" + machine + "&productId=" + milk);

    assertThat(saleIds(response)).containsExactlyElementsOf(ids(milkSale));
    assertThat(response.json().get("total")).isEqualTo(1);
  }

  @Test
  void productFilterAloneFindsTheSalesOfThatProductOnEveryMachine() {
    UUID product = UUID.randomUUID();
    Fx a = insert(UUID.randomUUID(), product, now().minusSeconds(60));
    Fx b = insert(UUID.randomUUID(), product, now().minusSeconds(120));
    insert(UUID.randomUUID(), UUID.randomUUID(), now().minusSeconds(30));

    Http.Response response = asOperator("/v1/sales?productId=" + product);

    assertThat(saleIds(response)).containsExactlyElementsOf(ids(a, b));
    assertThat(response.json().get("total")).isEqualTo(2);
  }

  @Test
  void filtersAreCombinedWithAnd() {
    UUID machine = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    UUID milk = UUID.randomUUID();
    UUID eggs = UUID.randomUUID();
    Instant t = now();
    Fx hit = insert(machine, milk, t.minus(1, ChronoUnit.HOURS), "NEEDS_RECONCILIATION");
    insert(machine, milk, t.minus(2, ChronoUnit.HOURS), "APPLIED"); // wrong state
    insert(machine, eggs, t.minus(3, ChronoUnit.HOURS), "NEEDS_RECONCILIATION"); // wrong product
    insert(other, milk, t.minus(4, ChronoUnit.HOURS), "NEEDS_RECONCILIATION"); // wrong machine
    insert(machine, milk, t.minus(30, ChronoUnit.DAYS), "NEEDS_RECONCILIATION"); // outside range

    Http.Response response =
        asOperator(
            "/v1/sales?machineId=%s&productId=%s&reconciliation=NEEDS_RECONCILIATION&from=%s&to=%s"
                .formatted(machine, milk, iso(t.minus(1, ChronoUnit.DAYS)), iso(t)));

    assertThat(saleIds(response)).containsExactlyElementsOf(ids(hit));
    assertThat(response.json().get("total")).isEqualTo(1);
  }

  @Test
  void fromIsInclusiveAndToIsExclusive() {
    UUID machine = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    Instant from = now().minus(10, ChronoUnit.HOURS);
    Instant to = from.plus(2, ChronoUnit.HOURS);
    insert(machine, product, from.minusMillis(1)); // just before from: out
    Fx atFrom = insert(machine, product, from);
    Fx inside = insert(machine, product, from.plus(1, ChronoUnit.HOURS));
    insert(machine, product, to); // exactly at to: out
    insert(machine, product, to.plusMillis(1)); // out

    Http.Response response = listOf(machine, "&from=" + iso(from) + "&to=" + iso(to));

    assertThat(saleIds(response)).containsExactlyElementsOf(ids(inside, atFrom));
    assertThat(response.json().get("total")).isEqualTo(2);
  }

  @Test
  void fromAloneAndToAloneAreOpenEnded() {
    UUID machine = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    Instant t = now().minus(5, ChronoUnit.HOURS);
    Fx early = insert(machine, product, t);
    Fx late = insert(machine, product, t.plus(2, ChronoUnit.HOURS));
    Instant middle = t.plus(1, ChronoUnit.HOURS);

    assertThat(saleIds(listOf(machine, "&from=" + iso(middle))))
        .containsExactlyElementsOf(ids(late));
    assertThat(saleIds(listOf(machine, "&to=" + iso(middle))))
        .containsExactlyElementsOf(ids(early));
  }

  @Test
  void fromEqualToIsAnEmptyRange() {
    UUID machine = UUID.randomUUID();
    Instant t = now().minus(1, ChronoUnit.HOURS);
    insert(machine, UUID.randomUUID(), t);

    Http.Response response = listOf(machine, "&from=" + iso(t) + "&to=" + iso(t));

    assertThat(response.status()).isEqualTo(200);
    assertThat(items(response)).isEmpty();
  }

  @Test
  void fromAfterToIs422() {
    Instant t = now().minus(1, ChronoUnit.HOURS);

    Http.Response response =
        listOf(UUID.randomUUID(), "&from=" + iso(t) + "&to=" + iso(t.minusSeconds(1)));

    assertError(response, 422, "VALIDATION_ERROR");
  }

  @Test
  void filtersByEachReconciliationState() {
    UUID machine = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    Instant t = now();
    Map<String, Fx> byState =
        Map.of(
            "PENDING", insert(machine, product, t.minusSeconds(40), "PENDING"),
            "APPLIED", insert(machine, product, t.minusSeconds(30), "APPLIED"),
            "NEEDS_RECONCILIATION",
                insert(machine, product, t.minusSeconds(20), "NEEDS_RECONCILIATION"),
            "RESOLVED", insert(machine, product, t.minusSeconds(10), "RESOLVED"));

    for (String state : byState.keySet()) {
      Http.Response response = listOf(machine, "&reconciliation=" + state);

      assertThat(saleIds(response)).as(state).containsExactlyElementsOf(ids(byState.get(state)));
      assertThat(response.json().get("total")).as(state).isEqualTo(1);
      assertSaleOut(items(response).get(0), byState.get(state));
    }
    assertThat(listOf(machine, "").json().get("total")).isEqualTo(4);
  }

  @Test
  void anUnknownReconciliationStateIsRejected() {
    Http.Response response = listOf(UUID.randomUUID(), "&reconciliation=BOGUS");

    assertThat(response.status()).isIn(400, 422);
    assertThat(response.json().get("code")).isIn("BAD_REQUEST", "VALIDATION_ERROR");
  }

  @Test
  void malformedFilterValuesAreABadRequest() {
    for (String bad :
        new String[] {"machineId=not-a-uuid", "productId=123", "from=yesterday", "to=2026-13-45"}) {
      assertError(asOperator("/v1/sales?" + bad), 400, "BAD_REQUEST");
    }
  }

  // --- the exact query of Product (product-service app/clients/sales.py)

  @Test
  void productsOpenReconciliationQueryFindsTheSaleAndNothingElse() {
    UUID product = UUID.randomUUID();
    UUID machine = UUID.randomUUID();
    Instant t = now();
    Fx open = insert(machine, product, t.minus(2, ChronoUnit.HOURS), "NEEDS_RECONCILIATION");
    insert(machine, product, t.minus(1, ChronoUnit.HOURS), "APPLIED");
    insert(machine, product, t.minus(3, ChronoUnit.HOURS), "RESOLVED");
    insert(UUID.randomUUID(), UUID.randomUUID(), t, "NEEDS_RECONCILIATION"); // another product

    Http.Response response =
        get(
            "/v1/sales?productId=" + product + "&reconciliation=NEEDS_RECONCILIATION&limit=1",
            product());

    assertThat(response.status()).isEqualTo(200);
    assertThat(saleIds(response)).containsExactlyElementsOf(ids(open));
    assertThat(response.json().get("total")).isEqualTo(1);
    assertThat(response.json().get("limit")).isEqualTo(1);
    assertThat(PRODUCT.calls()).isEmpty();
    assertThat(INVENTORY.calls()).isEmpty();
  }

  @Test
  void productsQueryForAProductWithoutOpenReconciliationIsEmpty() {
    UUID product = UUID.randomUUID();
    insert(UUID.randomUUID(), product, now().minusSeconds(60), "APPLIED");

    Http.Response response =
        get(
            "/v1/sales?productId=" + product + "&reconciliation=NEEDS_RECONCILIATION&limit=1",
            product());

    assertThat(response.status()).isEqualTo(200);
    assertThat(items(response)).isEmpty();
    assertThat(response.json().get("total")).isEqualTo(0);
  }

  @Test
  void limitOneReturnsOneItemButTotalCountsAll() {
    UUID product = UUID.randomUUID();
    UUID machine = UUID.randomUUID();
    Instant t = now();
    Fx newer = insert(machine, product, t.minusSeconds(10), "NEEDS_RECONCILIATION");
    insert(machine, product, t.minusSeconds(20), "NEEDS_RECONCILIATION");

    Http.Response response =
        get(
            "/v1/sales?productId=" + product + "&reconciliation=NEEDS_RECONCILIATION&limit=1",
            product());

    assertThat(saleIds(response)).containsExactlyElementsOf(List.of(newer.saleId().toString()));
    assertThat(response.json().get("total")).isEqualTo(2);
  }
}
