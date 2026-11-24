package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.sales.core.support.Http;
import si.vending.sales.sales.support.SalesReadTest;

/**
 * GET /v1/sales/stats and /v1/sales/stats/{machineId} (issue #27). {@code SalesStatsOut}: {@code
 * totals[{currency, salesCount, quantity, turnover}]} (one entry per currency, turnover = sum of
 * quantity x unitPricePaid as a decimal string with two decimals) and {@code
 * topProducts[{productId, quantity, turnover}]} ordered by quantity, at most {@code top} (default
 * 5, 1..20). {@code from} inclusive, {@code to} exclusive on occurredAt. Without sales both lists
 * are empty (docs/03 section 8).
 *
 * <p>The tie-break between products with equal quantity is not specified, so every ordering
 * assertion uses distinct quantities. Statistics over all machines use a private time window (see
 * {@link SalesReadTest#window()}).
 */
class SalesStatsTest extends SalesReadTest {

  private static String range(Window w) {
    return "from=" + iso(w.from()) + "&to=" + iso(w.to());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Http.Response response, String key) {
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    return (List<Map<String, Object>>) response.json().get(key);
  }

  private static Map<String, Object> totalsOf(Http.Response response, String currency) {
    return list(response, "totals").stream()
        .filter(t -> currency.equals(t.get("currency")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no totals for " + currency));
  }

  // --- all machines

  @Test
  void aWindowWithoutSalesIsEmpty() {
    Window w = window();

    Http.Response response = asOperator("/v1/sales/stats?" + range(w));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().keySet()).containsExactlyInAnyOrder("totals", "topProducts");
    assertThat(list(response, "totals")).isEmpty();
    assertThat(list(response, "topProducts")).isEmpty();
  }

  @Test
  void totalsAreTheExactDecimalSums() {
    Window w = window();
    UUID machine = UUID.randomUUID();
    UUID milk = UUID.randomUUID();
    UUID eggs = UUID.randomUUID();
    insert(machine, milk, 2, "2.50", "EUR", w.at(1), "APPLIED", null); // 5.00
    insert(machine, milk, 1, "2.50", "EUR", w.at(2), "PENDING", null); // 2.50
    insert(
        machine,
        eggs,
        3,
        "1.20",
        "EUR",
        w.at(3),
        "NEEDS_RECONCILIATION",
        "INSUFFICIENT_STOCK"); // 3.60
    insert(machine, eggs, 1, "0.10", "EUR", w.at(4), "RESOLVED", null); // 0.10

    Http.Response response = asOperator("/v1/sales/stats?" + range(w));

    assertThat(response.status()).isEqualTo(200);
    assertThat(list(response, "totals")).hasSize(1);
    Map<String, Object> eur = totalsOf(response, "EUR");
    assertThat(eur.keySet())
        .containsExactlyInAnyOrder("currency", "salesCount", "quantity", "turnover");
    assertThat(eur.get("salesCount")).isEqualTo(4);
    assertThat(eur.get("quantity")).isEqualTo(7);
    assertThat(eur.get("turnover")).isEqualTo("11.20");
  }

  @Test
  void decimalSumsDoNotDriftLikeFloats() {
    Window w = window();
    UUID machine = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    for (int i = 0; i < 10; i++) {
      insert(machine, product, 1, "0.10", "EUR", w.at(i + 1), "APPLIED", null);
    }

    assertThat(totalsOf(asOperator("/v1/sales/stats?" + range(w)), "EUR").get("turnover"))
        .isEqualTo("1.00");
  }

  @Test
  void eachCurrencyHasItsOwnTotals() {
    Window w = window();
    UUID machine = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    insert(machine, product, 2, "2.00", "EUR", w.at(1), "APPLIED", null);
    insert(machine, product, 1, "3.00", "USD", w.at(2), "APPLIED", null);

    Http.Response response = asOperator("/v1/sales/stats?" + range(w));

    assertThat(list(response, "totals")).hasSize(2);
    assertThat(totalsOf(response, "EUR").get("turnover")).isEqualTo("4.00");
    assertThat(totalsOf(response, "EUR").get("quantity")).isEqualTo(2);
    assertThat(totalsOf(response, "USD").get("turnover")).isEqualTo("3.00");
    assertThat(totalsOf(response, "USD").get("salesCount")).isEqualTo(1);
  }

  @Test
  void totalsCoverEveryMachine() {
    Window w = window();
    UUID product = UUID.randomUUID();
    insert(UUID.randomUUID(), product, 1, "1.00", "EUR", w.at(1), "APPLIED", null);
    insert(UUID.randomUUID(), product, 1, "1.00", "EUR", w.at(2), "APPLIED", null);

    Map<String, Object> eur = totalsOf(asOperator("/v1/sales/stats?" + range(w)), "EUR");

    assertThat(eur.get("salesCount")).isEqualTo(2);
  }

  @Test
  void topProductsAreOrderedByQuantityWithTheirTurnover() {
    Window w = window();
    UUID machine = UUID.randomUUID();
    UUID milk = UUID.randomUUID();
    UUID eggs = UUID.randomUUID();
    UUID meat = UUID.randomUUID();
    insert(machine, milk, 1, "2.50", "EUR", w.at(1), "APPLIED", null);
    insert(machine, milk, 2, "2.50", "EUR", w.at(2), "APPLIED", null); // milk: 3 pcs, 7.50
    insert(machine, eggs, 5, "1.20", "EUR", w.at(3), "APPLIED", null); // eggs: 5 pcs, 6.00
    insert(machine, meat, 1, "9.99", "EUR", w.at(4), "APPLIED", null); // meat: 1 pc, 9.99

    List<Map<String, Object>> top = list(asOperator("/v1/sales/stats?" + range(w)), "topProducts");

    assertThat(top).hasSize(3);
    assertThat(top.stream().map(p -> p.get("productId")).toList())
        .containsExactly(eggs.toString(), milk.toString(), meat.toString());
    assertThat(top.get(0).keySet()).containsExactlyInAnyOrder("productId", "quantity", "turnover");
    assertThat(top.get(0).get("quantity")).isEqualTo(5);
    assertThat(top.get(0).get("turnover")).isEqualTo("6.00");
    assertThat(top.get(1).get("quantity")).isEqualTo(3);
    assertThat(top.get(1).get("turnover")).isEqualTo("7.50");
    assertThat(top.get(2).get("quantity")).isEqualTo(1);
    assertThat(top.get(2).get("turnover")).isEqualTo("9.99");
  }

  @Test
  void topIsFiveByDefaultAndKeepsTheBestSellers() {
    Window w = window();
    UUID machine = UUID.randomUUID();
    UUID[] products = new UUID[7];
    for (int i = 0; i < 7; i++) {
      products[i] = UUID.randomUUID();
      insert(machine, products[i], i + 1, "1.00", "EUR", w.at(i + 1), "APPLIED", null);
    }

    List<Map<String, Object>> top = list(asOperator("/v1/sales/stats?" + range(w)), "topProducts");

    assertThat(top.stream().map(p -> p.get("productId")).toList())
        .containsExactly(
            products[6].toString(),
            products[5].toString(),
            products[4].toString(),
            products[3].toString(),
            products[2].toString());
  }

  @Test
  void topLimitsTheListAndDoesNotChangeTheTotals() {
    Window w = window();
    UUID machine = UUID.randomUUID();
    for (int i = 0; i < 7; i++) {
      insert(machine, UUID.randomUUID(), i + 1, "1.00", "EUR", w.at(i + 1), "APPLIED", null);
    }

    Http.Response two = asOperator("/v1/sales/stats?top=2&" + range(w));
    Http.Response twenty = asOperator("/v1/sales/stats?top=20&" + range(w));

    assertThat(list(two, "topProducts")).hasSize(2);
    assertThat(list(two, "topProducts").get(0).get("quantity")).isEqualTo(7);
    assertThat(list(twenty, "topProducts")).hasSize(7);
    assertThat(totalsOf(two, "EUR").get("salesCount")).isEqualTo(7);
    assertThat(totalsOf(two, "EUR").get("quantity")).isEqualTo(28);
  }

  @Test
  void topOutsideOneTo20IsRejected() {
    for (String bad : new String[] {"top=0", "top=21", "top=-1"}) {
      assertError(asOperator("/v1/sales/stats?" + bad), 422, "VALIDATION_ERROR");
      assertError(
          asOperator("/v1/sales/stats/" + UUID.randomUUID() + "?" + bad), 422, "VALIDATION_ERROR");
    }
  }

  @Test
  void aNonNumericTopIsABadRequest() {
    assertError(asOperator("/v1/sales/stats?top=many"), 400, "BAD_REQUEST");
  }

  @Test
  void fromIsInclusiveAndToIsExclusive() {
    Window w = window();
    UUID machine = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    insert(machine, product, 1, "1.00", "EUR", w.from().minusMillis(1), "APPLIED", null);
    insert(machine, product, 1, "1.00", "EUR", w.from(), "APPLIED", null); // in
    insert(machine, product, 1, "1.00", "EUR", w.to().minusMillis(1), "APPLIED", null); // in
    insert(machine, product, 1, "1.00", "EUR", w.to(), "APPLIED", null); // out

    Http.Response response = asOperator("/v1/sales/stats?" + range(w));

    assertThat(totalsOf(response, "EUR").get("salesCount")).isEqualTo(2);
    assertThat(totalsOf(response, "EUR").get("turnover")).isEqualTo("2.00");
  }

  @Test
  void fromAfterToIs422() {
    Window w = window();

    assertError(
        asOperator("/v1/sales/stats?from=" + iso(w.to()) + "&to=" + iso(w.from())),
        422,
        "VALIDATION_ERROR");
  }

  @Test
  void malformedTimesAreABadRequest() {
    assertError(asOperator("/v1/sales/stats?from=yesterday"), 400, "BAD_REQUEST");
    assertError(asOperator("/v1/sales/stats?to=2026-13-45"), 400, "BAD_REQUEST");
  }

  // --- per machine

  @Test
  void machineStatsCountOnlyThatMachine() {
    Window w = window();
    UUID mine = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    UUID milk = UUID.randomUUID();
    UUID eggs = UUID.randomUUID();
    insert(mine, milk, 2, "2.50", "EUR", w.at(1), "APPLIED", null); // 5.00
    insert(mine, milk, 1, "2.50", "EUR", w.at(2), "APPLIED", null); // 2.50
    insert(other, eggs, 9, "1.00", "EUR", w.at(3), "APPLIED", null);

    Http.Response response = asOperator("/v1/sales/stats/" + mine + "?" + range(w));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().keySet()).containsExactlyInAnyOrder("totals", "topProducts");
    Map<String, Object> eur = totalsOf(response, "EUR");
    assertThat(eur.get("salesCount")).isEqualTo(2);
    assertThat(eur.get("quantity")).isEqualTo(3);
    assertThat(eur.get("turnover")).isEqualTo("7.50");
    List<Map<String, Object>> top = list(response, "topProducts");
    assertThat(top).hasSize(1);
    assertThat(top.get(0).get("productId")).isEqualTo(milk.toString());
    assertThat(top.get(0).get("quantity")).isEqualTo(3);
    assertThat(top.get(0).get("turnover")).isEqualTo("7.50");
  }

  @Test
  void machineStatsRespectFromToAndTop() {
    UUID machine = UUID.randomUUID();
    Instant t = now();
    UUID best = UUID.randomUUID();
    insert(machine, best, 4, "1.00", "EUR", t.minusSeconds(3600), "APPLIED", null);
    insert(machine, UUID.randomUUID(), 2, "1.00", "EUR", t.minusSeconds(7200), "APPLIED", null);
    insert(
        machine, UUID.randomUUID(), 1, "1.00", "EUR", t.minusSeconds(86400L * 3), "APPLIED", null);

    Http.Response response =
        asOperator(
            "/v1/sales/stats/%s?top=1&from=%s&to=%s"
                .formatted(machine, iso(t.minusSeconds(86400)), iso(t)));

    assertThat(totalsOf(response, "EUR").get("salesCount")).isEqualTo(2);
    assertThat(list(response, "topProducts")).hasSize(1);
    assertThat(list(response, "topProducts").get(0).get("productId")).isEqualTo(best.toString());
  }

  @Test
  void aMachineWithoutSalesHasEmptyStats() {
    Http.Response response = asOperator("/v1/sales/stats/" + UUID.randomUUID());

    assertThat(response.status()).isEqualTo(200);
    assertThat(list(response, "totals")).isEmpty();
    assertThat(list(response, "topProducts")).isEmpty();
    assertThat(PRODUCT.calls()).isEmpty();
    assertThat(INVENTORY.calls()).isEmpty();
  }

  @Test
  void malformedMachineIdIs400() {
    assertError(asOperator("/v1/sales/stats/not-a-uuid"), 400, "BAD_REQUEST");
  }

  @Test
  void statsReadsNeverCallProductOrInventory() {
    UUID machine = UUID.randomUUID();
    insert(machine, UUID.randomUUID(), now().minusSeconds(60));

    assertThat(asOperator("/v1/sales/stats").status()).isEqualTo(200);
    assertThat(asOperator("/v1/sales/stats/" + machine).status()).isEqualTo(200);
    assertThat(PRODUCT.calls()).isEmpty();
    assertThat(INVENTORY.calls()).isEmpty();
  }
}
