package si.vending.monitoring.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import si.vending.monitoring.alert.support.AlertApiTest;
import si.vending.monitoring.core.support.Http;

/**
 * #25 access rules (openapi x-callers). {@code POST /v1/alerts}: only {@code inventory}. {@code GET
 * /v1/alerts}: OPERATOR and frontend-api. Both PUT routes: OPERATOR only. No or unknown credential:
 * 401 UNAUTHENTICATED; a known caller that is not allowed (other service token, gateway token
 * without the needed identity, machine identity): 403 CALLER_NOT_ALLOWED.
 */
class AlertReportAuthTest extends AlertApiTest {

  private final String machine = newMachine();

  private Http.Response postReport(String... headers) {
    return post("/v1/alerts", reportJson(machine, 1, slot("A1", newProduct(), 1, 3)), headers);
  }

  @Test
  void postWithoutCredentialIs401() {
    assertError(postReport(), 401, "UNAUTHENTICATED");
    assertError(postReport("X-Service-Token", "nope"), 401, "UNAUTHENTICATED");
  }

  @Test
  void postByTheGatewayInAnyFormIs403() {
    assertError(postReport(frontendApi()), 403, "CALLER_NOT_ALLOWED");
    assertError(postReport(operator()), 403, "CALLER_NOT_ALLOWED");
    assertError(postReport(device(machine)), 403, "CALLER_NOT_ALLOWED");
  }

  @Test
  void postByMonitoringsOwnTokenIs403() {
    assertError(postReport("X-Service-Token", MONITORING_TOKEN), 403, "CALLER_NOT_ALLOWED");
  }

  @Test
  void rejectedPostsStoreNothing() {
    postReport(frontendApi());
    postReport(operator());

    assertThat(alertsOf(machine)).isEmpty();
  }

  @Test
  void inventoryIsAllowedToPost() {
    assertThat(postReport(inventory()).status()).isEqualTo(201);
  }

  @Test
  void getWithoutCredentialIs401() {
    assertError(get("/v1/alerts"), 401, "UNAUTHENTICATED");
    assertError(get("/v1/alerts", "X-Service-Token", "nope"), 401, "UNAUTHENTICATED");
  }

  @Test
  void operatorAndFrontendApiMayList() {
    assertThat(get("/v1/alerts?machineId=" + machine, operator()).status()).isEqualTo(200);
    assertThat(get("/v1/alerts?machineId=" + machine, frontendApi()).status()).isEqualTo(200);
  }

  @Test
  void inventoryAndMachinesMayNotList() {
    assertError(get("/v1/alerts", inventory()), 403, "CALLER_NOT_ALLOWED");
    assertError(get("/v1/alerts", device(machine)), 403, "CALLER_NOT_ALLOWED");
    assertError(get("/v1/alerts", "X-Service-Token", MONITORING_TOKEN), 403, "CALLER_NOT_ALLOWED");
  }

  @Test
  void putRoutesAreOperatorOnly() {
    String id = openLowStock(machine, 1, slot("A1", newProduct(), 1, 3)).get("id").toString();
    for (String[] caller :
        new String[][] {
          frontendApi(),
          inventory(),
          device(machine),
          {"X-Service-Token", MONITORING_TOKEN},
          {"X-Service-Token", INVENTORY_TOKEN, "X-Operator-Id", OPERATOR_ID}
        }) {
      assertError(acknowledge(id, caller), 403, "CALLER_NOT_ALLOWED");
      assertError(resolve(id, null, caller), 403, "CALLER_NOT_ALLOWED");
    }
    assertError(acknowledge(id, new String[0]), 401, "UNAUTHENTICATED");
    assertError(resolve(id, null, new String[0]), 401, "UNAUTHENTICATED");
  }

  @Test
  void rejectedPutsChangeNothing() {
    String offline = seedActive(machine, "OFFLINE");

    acknowledge(offline, frontendApi());
    resolve(offline, null, frontendApi());
    acknowledge(offline, inventory());

    assertThat(find(machine, offline))
        .containsEntry("status", "ACTIVE")
        .containsEntry("acknowledgedAt", null)
        .containsEntry("resolvedAt", null);
  }

  @Test
  void forbiddenCallerSeesNoDifferenceBetweenKnownAndUnknownAlert() {
    String unknown = java.util.UUID.randomUUID().toString();

    assertError(acknowledge(unknown, frontendApi()), 403, "CALLER_NOT_ALLOWED");
    assertError(resolve(unknown, null, frontendApi()), 403, "CALLER_NOT_ALLOWED");
  }
}
