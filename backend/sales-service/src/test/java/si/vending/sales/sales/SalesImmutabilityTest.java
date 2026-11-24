package si.vending.sales.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import si.vending.sales.sales.support.SalesApiTest;

/**
 * A stored sale is immutable in the database itself (docs: sales are never changed or deleted);
 * only the reconciliation state (inventory_state, inventory_reason) may be updated by the outbox.
 */
class SalesImmutabilityTest extends SalesApiTest {

  @Autowired JdbcTemplate jdbc;

  private String storedSale() {
    Setup setup = knownSlot();
    Map<String, Object> body = sale(setup);
    assertThat(postSale(body).status()).isEqualTo(201);
    return (String) body.get("saleId");
  }

  private Integer count(String saleId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales WHERE sale_id = ?", Integer.class, UUID.fromString(saleId));
  }

  @Test
  void updatingASaleFieldFails() {
    String saleId = storedSale();

    assertThatThrownBy(() ->
            jdbc.update("UPDATE sales SET quantity = 7 WHERE sale_id = ?", UUID.fromString(saleId)))
        .isInstanceOf(DataAccessException.class);

    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM sales WHERE sale_id = ?",
                Integer.class,
                UUID.fromString(saleId)))
        .isEqualTo(1);
  }

  @Test
  void deletingASaleFails() {
    String saleId = storedSale();

    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM sales WHERE sale_id = ?", UUID.fromString(saleId)))
        .isInstanceOf(DataAccessException.class);

    assertThat(count(saleId)).isEqualTo(1);
  }

  @Test
  void truncatingTheTableFails() {
    String saleId = storedSale();

    assertThatThrownBy(() -> jdbc.execute("TRUNCATE sales"))
        .isInstanceOf(DataAccessException.class);

    assertThat(count(saleId)).isEqualTo(1);
  }

  @Test
  void reconciliationStateMayStillBeUpdated() {
    String saleId = storedSale();

    int updated =
        jdbc.update(
            "UPDATE sales SET inventory_state = 'APPLIED' WHERE sale_id = ?",
            UUID.fromString(saleId));

    assertThat(updated).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT inventory_state FROM sales WHERE sale_id = ?",
                String.class,
                UUID.fromString(saleId)))
        .isEqualTo("APPLIED");
  }
}
