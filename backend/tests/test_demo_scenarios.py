"""Demo scenariji (issue #45); gateway nadomesti MockTransport, docker compose se le zapise."""

import json

import demo_scenarios as demo
import httpx
import pytest
from fake_gateway import FakeGateway
from smoke_test import SmokeError

PRODUCT = {"id": "p1", "referencePrice": "1.20"}
SETUP = {
    ("POST", "/api/v1/auth/login"): (200, {"accessToken": "tok"}),
    ("POST", "/api/v1/machines"): (201, {"id": "m1", "machineKey": "mk_1"}),
    ("POST", "/api/v1/categories"): (201, {"id": "c1"}),
    ("POST", "/api/v1/products"): (201, PRODUCT),
    ("POST", "/api/v1/inventory/refill"): (201, {"quantity": 5}),
}
SALE_MOVEMENT = {"type": "SALE", "saleId": "s1"}
STOP = ("stop", "-t", "0", "inventory-service")
START = ("up", "-d", "--wait", "inventory-service")


def sale(state, reason=None):
    return (200, {"inventory": {"state": state, "reason": reason}})


@pytest.fixture(autouse=True)
def fast_and_fixed(monkeypatch):
    """Brez čakanja; vsaka prodaja ima saleId s1, da ga tabela gatewaya pozna."""
    monkeypatch.setattr(demo.time, "sleep", lambda seconds: None)
    original = demo.sale_event
    monkeypatch.setattr(demo, "sale_event", lambda *a, **k: dict(original(*a, **k), saleId="s1"))


@pytest.fixture
def compose_calls(monkeypatch):
    calls = []
    monkeypatch.setattr(demo, "compose", lambda *args: calls.append(args))
    return calls


def client(routes):
    gateway = FakeGateway({**SETUP, **routes})
    http = httpx.Client(base_url="http://gateway", transport=httpx.MockTransport(gateway.handle))
    return http, gateway


def test_refill_is_repeated_with_the_same_refill_id():
    movement = {"slot": "A1", "quantity": 5, "batchCode": "DEMO"}
    http, gateway = client(
        {
            ("POST", "/api/v1/inventory/refill"): [(201, {"quantity": 5}), (200, {"quantity": 5})],
            ("GET", "/api/v1/inventory/movements"): (200, {"items": [movement]}),
        }
    )

    demo.refill(http)

    refills = [json.loads(r.content) for r in gateway.requests if r.url.path.endswith("/refill")]
    assert len(refills) == 2 and refills[0] == refills[1]


def test_refill_fails_with_a_doubled_movement():
    movement = {"slot": "A1", "quantity": 5, "batchCode": "DEMO"}
    http, _ = client(
        {
            ("POST", "/api/v1/inventory/refill"): [(201, {"quantity": 5}), (200, {"quantity": 5})],
            ("GET", "/api/v1/inventory/movements"): (200, {"items": [movement, movement]}),
        }
    )

    with pytest.raises(SmokeError, match="REFILL movements: expected 1, got 2"):
        demo.refill(http)


@pytest.mark.parametrize("total, error", [(1, None), (2, "got 2 and 1")])
def test_repeated_sale_needs_one_sale_and_one_movement(total, error):
    http, _ = client(
        {
            ("POST", "/api/v1/device/sales"): [
                (201, {}),
                (200, {}),
                (409, {"code": "SALE_ID_CONFLICT"}),
            ],
            ("GET", "/api/v1/sales/s1"): sale("APPLIED"),
            ("GET", "/api/v1/sales"): (200, {"total": total}),
            ("GET", "/api/v1/inventory/movements"): (200, {"items": [SALE_MOVEMENT]}),
            ("GET", "/api/v1/inventory/m1/A1"): (200, {"quantity": 4}),
        }
    )

    if error:
        with pytest.raises(SmokeError, match=error):
            demo.repeated_sale(http)
    else:
        demo.repeated_sale(http)


def test_inventory_down_gives_pending_then_applied(compose_calls):
    http, _ = client(
        {
            ("POST", "/api/v1/device/sales"): [
                (201, {}),
                (503, {"code": "DEPENDENCY_UNAVAILABLE"}),
            ],
            ("GET", "/api/v1/sales/s1"): [sale("PENDING"), sale("PENDING"), sale("APPLIED")],
            ("GET", "/api/v1/inventory/movements"): (200, {"items": [SALE_MOVEMENT]}),
        }
    )

    demo.inventory_down(http)

    assert compose_calls == [STOP, START]


def test_inventory_down_tries_again_when_outbox_was_faster(compose_calls):
    http, _ = client(
        {
            ("POST", "/api/v1/device/sales"): [(201, {}), (201, {}), (503, {})],
            ("GET", "/api/v1/sales/s1"): [
                sale("APPLIED"),
                sale("PENDING"),
                sale("PENDING"),
                sale("APPLIED"),
            ],
            ("GET", "/api/v1/inventory/movements"): (200, {"items": [SALE_MOVEMENT]}),
        }
    )

    demo.inventory_down(http)

    assert compose_calls == [STOP, START, STOP, START]


def test_inventory_is_started_again_when_the_scenario_fails(compose_calls):
    http, _ = client(
        {
            ("POST", "/api/v1/device/sales"): (201, {}),
            ("GET", "/api/v1/sales/s1"): sale("APPLIED"),
        }
    )

    with pytest.raises(SmokeError, match="APPLIED before Inventory stopped"):
        demo.inventory_down(http)

    assert compose_calls[-1] == START


def test_archive_is_rejected_until_the_sale_is_resolved():
    product = "/api/v1/products/p1"
    reconciliation = "/api/v1/sales/s1/reconciliation"
    open_sale = sale("NEEDS_RECONCILIATION", "INSUFFICIENT_STOCK")
    http, gateway = client(
        {
            ("POST", "/api/v1/device/sales"): (201, {}),
            ("GET", "/api/v1/sales/s1"): [open_sale, open_sale, sale("RESOLVED")],
            ("GET", "/api/v1/inventory/m1/A1"): (200, {"quantity": 2}),
            ("DELETE", product): [(409, {"code": "PRODUCT_HAS_OPEN_RECONCILIATION"}), (204, None)],
            ("PUT", reconciliation): (200, {}),
            ("GET", product): (200, {"status": "ARCHIVED"}),
        }
    )

    demo.archive_reconciliation(http)

    steps = [
        (r.method, r.url.path) for r in gateway.requests if r.url.path in (product, reconciliation)
    ]
    assert steps == [
        ("DELETE", product),
        ("PUT", reconciliation),
        ("DELETE", product),
        ("GET", product),
    ]
    put = next(r for r in gateway.requests if r.url.path == reconciliation)
    assert json.loads(put.content)["resolution"] == "WRITTEN_OFF"


def test_unknown_scenario(capsys):
    assert demo.main(["refil"]) == 1

    assert "Na voljo: refill, repeated-sale" in capsys.readouterr().out


def test_failed_scenario_is_named(monkeypatch, capsys):
    monkeypatch.setenv("BOOTSTRAP_OPERATOR_EMAIL", "admin@vending.local")
    monkeypatch.setenv("BOOTSTRAP_OPERATOR_PASSWORD", "geslo")
    gateway = FakeGateway({**SETUP, ("POST", "/api/v1/machines"): (503, {})})

    assert demo.main(["refill"], transport=httpx.MockTransport(gateway.handle)) == 1

    assert "FAIL refill: POST /api/v1/machines: expected 201, got 503" in capsys.readouterr().out
