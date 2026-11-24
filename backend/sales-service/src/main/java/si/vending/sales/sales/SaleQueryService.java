package si.vending.sales.sales;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.vending.sales.core.ApiException;
import si.vending.sales.core.Page;
import si.vending.sales.sales.SaleDtos.ReconciliationIn;
import si.vending.sales.sales.SaleDtos.ReconciliationState;
import si.vending.sales.sales.SaleDtos.SaleOut;
import si.vending.sales.sales.SaleDtos.SalesStatsOut;
import si.vending.sales.sales.SaleQueryRepository.Filter;

/** Reads of stored sales (list, one sale, statistics). Never calls Product or Inventory. */
@Service
public class SaleQueryService {

  private final SaleQueryRepository queries;
  private final SaleRepository sales;

  public SaleQueryService(SaleQueryRepository queries, SaleRepository sales) {
    this.queries = queries;
    this.sales = sales;
  }

  @Transactional(readOnly = true)
  public Page<SaleOut> list(
      UUID machineId,
      UUID productId,
      Instant from,
      Instant to,
      ReconciliationState reconciliation,
      int limit,
      int offset) {
    Filter filter = filter(machineId, productId, from, to, reconciliation);
    long total = queries.count(filter);
    List<SaleOut> items = queries.page(filter, limit, offset).stream().map(SaleOut::of).toList();
    return new Page<>(items, total, limit, offset);
  }

  @Transactional(readOnly = true)
  public SaleOut get(UUID saleId) {
    return sales.findById(saleId).map(SaleOut::of).orElseThrow(SaleQueryService::notFound);
  }

  /**
   * NEEDS_RECONCILIATION to RESOLVED. The conditional UPDATE decides the winner of concurrent
   * requests; the others then see the committed row: the same body is 200, anything else 409.
   */
  @Transactional
  public SaleOut resolve(UUID saleId, ReconciliationIn in, UUID operatorId) {
    int updated =
        sales.resolve(
            saleId,
            in.resolution().name(),
            in.note(),
            Instant.now().truncatedTo(ChronoUnit.MICROS),
            operatorId);
    Sale sale = sales.findById(saleId).orElseThrow(() -> notFound());
    if (updated == 1 || (sale.resolution() == in.resolution() && in.note().equals(sale.note()))) {
      return SaleOut.of(sale);
    }
    throw new ApiException(
        HttpStatus.CONFLICT,
        "INVALID_STATE_TRANSITION",
        "The sale is not waiting for reconciliation, or it was resolved differently.");
  }

  private static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "SALE_NOT_FOUND", "Sale not found.");
  }

  /** Statistics of all machines, or of one when {@code machineId} is given. */
  @Transactional(readOnly = true)
  public SalesStatsOut stats(UUID machineId, Instant from, Instant to, int top) {
    Filter filter = filter(machineId, null, from, to, null);
    return new SalesStatsOut(queries.totals(filter), queries.topProducts(filter, top));
  }

  private static Filter filter(
      UUID machineId,
      UUID productId,
      Instant from,
      Instant to,
      ReconciliationState reconciliation) {
    checkBound("from", from);
    checkBound("to", to);
    if (from != null && to != null && from.isAfter(to)) {
      throw ApiException.invalidField("from", "must not be after to.");
    }
    return new Filter(machineId, productId, from, to, reconciliation);
  }

  private static void checkBound(String field, Instant value) {
    if (value != null
        && (value.isBefore(SaleDtos.OCCURRED_AT_MIN) || value.isAfter(SaleDtos.TIME_MAX))) {
      throw ApiException.invalidField(
          field, "must be between " + SaleDtos.OCCURRED_AT_MIN + " and " + SaleDtos.TIME_MAX + ".");
    }
  }
}
