package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import si.vending.machine.machine.support.MachineKeyApiTest;

/**
 * #18 criterion 4: the secret is never stored in plaintext, only its SHA-256 hex (column key_hash,
 * V1__machines.sql).
 */
class MachineKeyStorageTest extends MachineKeyApiTest {

  @Autowired JdbcTemplate jdbc;

  /** Hex SHA-256 of the secret, computed independently of production code. */
  static String sha256(String secret) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, Object> row(String keyId) {
    List<Map<String, Object>> rows =
        jdbc.queryForList("SELECT * FROM machine_keys WHERE id = ?", UUID.fromString(keyId));
    assertThat(rows).as("row for key " + keyId).hasSize(1);
    return rows.get(0);
  }

  private void assertNoColumnHoldsPlaintext(Map<String, Object> row, String secret) {
    row.forEach((column, value) ->
        assertThat(String.valueOf(value)).as("column " + column).doesNotContain(secret));
  }

  @Test
  void keyIssuedWithTheMachineIsStoredHashedOnly() {
    Created m = newMachineWithKey();

    Map<String, Object> row = row(m.keyId());

    assertNoColumnHoldsPlaintext(row, m.machineKey());
    assertThat(String.valueOf(row.get("key_hash")).trim()).isEqualTo(sha256(m.machineKey()));
  }

  @Test
  void keyIssuedLaterIsStoredHashedOnly() {
    Created m = newMachineWithKey();
    Map<String, Object> created = createKey(m.machineId()).json();
    String secret = (String) created.get("machineKey");

    Map<String, Object> row = row((String) created.get("keyId"));

    assertNoColumnHoldsPlaintext(row, secret);
    assertThat(String.valueOf(row.get("key_hash")).trim()).isEqualTo(sha256(secret));
    assertThat(String.valueOf(row.get("hint")).trim())
        .isEqualTo(secret.substring(secret.length() - 4));
    assertThat(row.get("machine_id").toString()).isEqualTo(m.machineId());
    assertThat(row.get("status")).isEqualTo("ACTIVE");
  }

  @Test
  void secretAppearsInNoColumnOfAnyTableRow() {
    Created m = newMachineWithKey();
    String secret = issueKey(m.machineId());

    Integer matches =
        jdbc.queryForObject(
            "SELECT count(*) FROM machine_keys k WHERE k::text LIKE ?",
            Integer.class,
            "%" + secret + "%");

    assertThat(matches).isZero();
  }

  @Test
  void revokeKeepsTheRowAndMarksItRevoked() {
    Created m = newMachineWithKey();
    revokeKey(m.machineId(), m.keyId());

    Map<String, Object> row = row(m.keyId());

    assertThat(row.get("status")).isEqualTo("REVOKED");
    assertThat(row.get("revoked_at")).isNotNull();
  }

  @Test
  void retireRevokesEveryKeyInTheDatabase() {
    Created m = newMachineWithKey();
    String second = issueKey(m.machineId());
    assertThat(second).isNotBlank();

    assertThat(retire(m.machineId()).status()).isEqualTo(204);

    Integer active =
        jdbc.queryForObject(
            "SELECT count(*) FROM machine_keys WHERE machine_id = ? AND status = 'ACTIVE'",
            Integer.class,
            UUID.fromString(m.machineId()));
    assertThat(active).isZero();
  }
}
