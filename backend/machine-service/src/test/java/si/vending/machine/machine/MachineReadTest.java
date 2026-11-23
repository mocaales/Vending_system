package si.vending.machine.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.vending.machine.core.support.Http;
import si.vending.machine.machine.support.MachineApiTest;
import si.vending.machine.machine.support.MachineFixtures;

/** #17 criterion 5 (list, filters, pagination) and GET one; callers per x-callers. */
class MachineReadTest extends MachineApiTest {

  private static final String LJ_BBOX = "14.40,46.00,14.60,46.10";

  private String createWith(String tag, String suffix, double lat, double lon) {
    String code = tag + suffix;
    Http.Response response =
        createMachine(
            MachineFixtures.createJson(code, "Name " + code, "Address " + code, lat, lon));
    assertThat(response.status()).as("setup create " + code).isEqualTo(201);
    return (String) response.json().get("id");
  }

  @SuppressWarnings("unchecked")
  private static Set<String> ids(Http.Response list) {
    assertThat(list.status()).as(list.body()).isEqualTo(200);
    Set<String> ids = new HashSet<>();
    for (Object item : list.items()) {
      ids.add((String) ((Map<String, Object>) item).get("id"));
    }
    return ids;
  }

  // --- GET /v1/machines/{id}

  @Test
  void getReturnsMachineWithSlots() {
    String id = newMachine(3);

    Http.Response response = getMachine(id);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().get("id")).isEqualTo(id);
    assertThat((List<?>) response.json().get("slots")).hasSize(3);
  }

  @Test
  void getUnknownIdIsNotFound() {
    assertError(getMachine(UUID.randomUUID().toString()), 404, "MACHINE_NOT_FOUND");
  }

  @Test
  void getMalformedIdIsBadRequest() {
    assertError(get("/v1/machines/not-a-uuid", operator()), 400, "BAD_REQUEST");
  }

  @Test
  void getRetiredMachineStillWorks() {
    String id = newMachine(1);
    assertThat(retire(id).status()).isEqualTo(204);

    Http.Response response = getMachine(id);

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.json().get("status")).isEqualTo("RETIRED");
  }

  // --- callers

  @Test
  void getAllowedForOperatorAndServicesInTheCallerList() {
    String id = newMachine(1);

    assertThat(get("/v1/machines/" + id, operator()).status()).isEqualTo(200);
    assertThat(get("/v1/machines/" + id, "X-Service-Token", INVENTORY_TOKEN).status())
        .isEqualTo(200);
    assertThat(get("/v1/machines/" + id, "X-Service-Token", MONITORING_TOKEN).status())
        .isEqualTo(200);
    assertThat(get("/v1/machines/" + id, "X-Service-Token", GATEWAY_TOKEN).status()).isEqualTo(200);
    assertThat(get("/v1/machines", "X-Service-Token", INVENTORY_TOKEN).status()).isEqualTo(200);
  }

  @Test
  void serviceOutsideTheCallerListIsForbidden() {
    String id = newMachine(1);

    assertError(
        get("/v1/machines/" + id, "X-Service-Token", SALES_TOKEN), 403, "CALLER_NOT_ALLOWED");
    assertError(get("/v1/machines", "X-Service-Token", SALES_TOKEN), 403, "CALLER_NOT_ALLOWED");
  }

  @Test
  void anonymousAndUnknownTokenAreUnauthenticated() {
    String id = newMachine(1);

    assertError(get("/v1/machines/" + id), 401, "UNAUTHENTICATED");
    assertError(get("/v1/machines"), 401, "UNAUTHENTICATED");
    assertError(get("/v1/machines", "X-Service-Token", "nope"), 401, "UNAUTHENTICATED");
  }

  // --- list

  @Test
  void listHasPageShapeAndMachineOutItems() {
    String tag = "T" + MachineFixtures.tag();
    createWith(tag, "-1", 46.05, 14.50);

    Http.Response list = get("/v1/machines?q=" + tag, operator());

    assertThat(list.status()).isEqualTo(200);
    assertThat(list.json().keySet()).containsExactlyInAnyOrder("items", "total", "limit", "offset");
    assertThat(list.json()).containsEntry("limit", 50).containsEntry("offset", 0);
    assertThat(list.json().get("total")).isEqualTo(1);
    @SuppressWarnings("unchecked")
    Map<String, Object> item = (Map<String, Object>) list.items().get(0);
    assertThat(item.keySet())
        .containsExactlyInAnyOrder(
            "id,code,name,address,latitude,longitude,status,slots,createdAt,updatedAt".split(","));
  }

  @Test
  void listWithoutStatusReturnsActiveAndServiceOnly() {
    String tag = "T" + MachineFixtures.tag();
    String active = createWith(tag, "-a", 46.05, 14.50);
    String service = createWith(tag, "-s", 46.05, 14.50);
    String retired = createWith(tag, "-r", 46.05, 14.50);
    assertThat(setStatus(service, "SERVICE").status()).isEqualTo(200);
    assertThat(retire(retired).status()).isEqualTo(204);

    Http.Response list = get("/v1/machines?q=" + tag, operator());

    assertThat(ids(list)).containsExactlyInAnyOrder(active, service);
    assertThat(list.json().get("total")).isEqualTo(2);
  }

  @Test
  void listFiltersByStatus() {
    String tag = "T" + MachineFixtures.tag();
    String active = createWith(tag, "-a", 46.05, 14.50);
    String service = createWith(tag, "-s", 46.05, 14.50);
    String retired = createWith(tag, "-r", 46.05, 14.50);
    setStatus(service, "SERVICE");
    retire(retired);

    assertThat(ids(get("/v1/machines?status=RETIRED&q=" + tag, operator())))
        .containsExactly(retired);
    assertThat(ids(get("/v1/machines?status=SERVICE&q=" + tag, operator())))
        .containsExactly(service);
    assertThat(ids(get("/v1/machines?status=ACTIVE&q=" + tag, operator()))).containsExactly(active);
  }

  @Test
  void unknownStatusFilterIsRejected() {
    assertRejected(get("/v1/machines?status=BROKEN", operator()));
  }

  @Test
  void listFiltersByBoundingBox() {
    String tag = "T" + MachineFixtures.tag();
    String inside = createWith(tag, "-in", 46.05, 14.50);
    createWith(tag, "-north", 46.50, 14.50);
    createWith(tag, "-east", 46.05, 15.60);

    Http.Response list = get("/v1/machines?bbox=" + LJ_BBOX + "&q=" + tag, operator());

    assertThat(ids(list)).containsExactly(inside);
    assertThat(list.json().get("total")).isEqualTo(1);
  }

  @Test
  void bboxHandlesNegativeCoordinates() {
    String tag = "T" + MachineFixtures.tag();
    String west = createWith(tag, "-w", -33.9, -70.7);
    createWith(tag, "-e", 46.05, 14.50);

    Http.Response list = get("/v1/machines?bbox=-71.0,-34.5,-70.0,-33.0&q=" + tag, operator());

    assertThat(ids(list)).containsExactly(west);
  }

  @Test
  void malformedBboxIsRejected() {
    assertRejected(get("/v1/machines?bbox=14.4,46.0,14.6", operator()));
    assertRejected(get("/v1/machines?bbox=a,b,c,d", operator()));
  }

  @Test
  void queryMatchesCodeNameAndAddressCaseInsensitively() {
    String tag = MachineFixtures.tag();
    String code = "Q" + tag + "-CODE";
    Http.Response created =
        createMachine(
            MachineFixtures.createJson(
                code, "Zelena Postaja " + tag, "Cankarjeva " + tag, 46.05, 14.50));
    String id = (String) created.json().get("id");

    assertThat(ids(get("/v1/machines?q=" + code.toLowerCase(), operator()))).containsExactly(id);
    assertThat(ids(get("/v1/machines?q=ZELENA%20POSTAJA%20" + tag, operator())))
        .containsExactly(id);
    assertThat(ids(get("/v1/machines?q=cankarjeva%20" + tag.toUpperCase(), operator())))
        .containsExactly(id);
    assertThat(get("/v1/machines?q=nomatch" + tag, operator()).json().get("total")).isEqualTo(0);
  }

  @Test
  void queryTreatsLikeWildcardsAsPlainText() {
    createMachine(MachineFixtures.createJson("T" + MachineFixtures.tag(), "A1"));

    assertThat(get("/v1/machines?q=%25", operator()).json().get("total")).isEqualTo(0);
    assertThat(get("/v1/machines?q=_", operator()).json().get("total")).isEqualTo(0);
  }

  @Test
  void queryLongerThan100IsRejected() {
    assertRejected(get("/v1/machines?q=" + "x".repeat(101), operator()));
  }

  @Test
  void filtersCombine() {
    String tag = "T" + MachineFixtures.tag();
    String match = createWith(tag, "-m", 46.05, 14.50);
    String serviceInside = createWith(tag, "-s", 46.06, 14.51);
    setStatus(serviceInside, "SERVICE");

    Http.Response list =
        get("/v1/machines?status=ACTIVE&bbox=" + LJ_BBOX + "&q=" + tag, operator());

    assertThat(ids(list)).containsExactly(match);
  }

  @Test
  void paginationSplitsTheResultWithoutOverlap() {
    String tag = "T" + MachineFixtures.tag();
    for (int i = 0; i < 5; i++) {
      createWith(tag, "-" + i, 46.05, 14.50);
    }

    Http.Response p1 = get("/v1/machines?limit=2&offset=0&q=" + tag, operator());
    Http.Response p2 = get("/v1/machines?limit=2&offset=2&q=" + tag, operator());
    Http.Response p3 = get("/v1/machines?limit=2&offset=4&q=" + tag, operator());
    Http.Response p4 = get("/v1/machines?limit=2&offset=6&q=" + tag, operator());

    assertThat(p1.items()).hasSize(2);
    assertThat(p2.items()).hasSize(2);
    assertThat(p3.items()).hasSize(1);
    assertThat(p4.items()).isEmpty();
    assertThat(p1.json())
        .containsEntry("total", 5)
        .containsEntry("limit", 2)
        .containsEntry("offset", 0);
    assertThat(p2.json()).containsEntry("total", 5).containsEntry("offset", 2);
    assertThat(p4.json()).containsEntry("total", 5).containsEntry("offset", 6);
    Set<String> all = new HashSet<>();
    all.addAll(ids(p1));
    all.addAll(ids(p2));
    all.addAll(ids(p3));
    assertThat(all).hasSize(5);
  }

  @Test
  void invalidPaginationIsRejected() {
    assertError(get("/v1/machines?limit=0", operator()), 422, "VALIDATION_ERROR");
    assertError(get("/v1/machines?limit=201", operator()), 422, "VALIDATION_ERROR");
    assertError(get("/v1/machines?offset=-1", operator()), 422, "VALIDATION_ERROR");
    assertError(get("/v1/machines?limit=abc", operator()), 400, "BAD_REQUEST");
  }
}
