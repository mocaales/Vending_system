package si.vending.sales.sales;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import si.vending.sales.sales.SaleDtos.ReconciliationState;
import si.vending.sales.sales.SaleDtos.TopProduct;
import si.vending.sales.sales.SaleDtos.Totals;

/**
 * Read queries over {@code sales}. The WHERE clause is built only from fixed fragments; every value
 * is a bound parameter. Sums are computed in SQL with {@code numeric}.
 */
@Repository
public class SaleQueryRepository {

  /** Optional filters, AND-ed; {@code null} means no filter. {@code to} is exclusive. */
  public record Filter(
      UUID machineId,
      UUID productId,
      Instant from,
      Instant to,
      ReconciliationState reconciliation) {}

  @PersistenceContext private EntityManager entityManager;

  private final JdbcClient jdbc;

  public SaleQueryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public long count(Filter filter) {
    Map<String, Object> params = new HashMap<>();
    String where = where(filter, params);
    return jdbc.sql("SELECT count(*) FROM sales" + where).params(params).query(Long.class).single();
  }

  /** Newest first; equal times ordered by sale id so paging is deterministic. */
  public List<Sale> page(Filter filter, int limit, int offset) {
    Map<String, Object> params = new HashMap<>();
    String where = where(filter, params);
    Query query =
        entityManager.createNativeQuery(
            "SELECT * FROM sales"
                + where
                + " ORDER BY occurred_at DESC, sale_id LIMIT :limit OFFSET :offset",
            Sale.class);
    params.forEach(query::setParameter);
    query.setParameter("limit", limit);
    query.setParameter("offset", offset);
    List<Sale> result = new ArrayList<>();
    for (Object row : query.getResultList()) {
      result.add((Sale) row);
    }
    return result;
  }

  public List<Totals> totals(Filter filter) {
    Map<String, Object> params = new HashMap<>();
    String where = where(filter, params);
    return jdbc.sql(
            "SELECT currency, count(*) AS sales_count, sum(quantity) AS quantity,"
                + " sum(quantity * unit_price_paid) AS turnover FROM sales"
                + where
                + " GROUP BY currency ORDER BY currency")
        .params(params)
        .query((rs, i) ->
            new Totals(
                rs.getString("currency").strip(),
                rs.getLong("sales_count"),
                rs.getLong("quantity"),
                money(rs.getBigDecimal("turnover"))))
        .list();
  }

  public List<TopProduct> topProducts(Filter filter, int top) {
    Map<String, Object> params = new HashMap<>();
    String where = where(filter, params);
    params.put("top", top);
    return jdbc.sql(
            "SELECT product_id, sum(quantity) AS quantity,"
                + " sum(quantity * unit_price_paid) AS turnover FROM sales"
                + where
                + " GROUP BY product_id ORDER BY quantity DESC, product_id LIMIT :top")
        .params(params)
        .query((rs, i) ->
            new TopProduct(
                rs.getObject("product_id", UUID.class),
                rs.getLong("quantity"),
                money(rs.getBigDecimal("turnover"))))
        .list();
  }

  private static String money(BigDecimal value) {
    return value.setScale(2).toPlainString();
  }

  private static String where(Filter f, Map<String, Object> params) {
    List<String> conditions = new ArrayList<>();
    if (f.machineId() != null) {
      conditions.add("machine_id = :machineId");
      params.put("machineId", f.machineId());
    }
    if (f.productId() != null) {
      conditions.add("product_id = :productId");
      params.put("productId", f.productId());
    }
    if (f.from() != null) {
      conditions.add("occurred_at >= :from");
      params.put("from", java.sql.Timestamp.from(f.from()));
    }
    if (f.to() != null) {
      conditions.add("occurred_at < :to");
      params.put("to", java.sql.Timestamp.from(f.to()));
    }
    if (f.reconciliation() != null) {
      conditions.add("inventory_state = :state");
      params.put("state", f.reconciliation().name());
    }
    return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
  }
}
