"""Koraki smoke testa (issue #36); gateway nadomesti httpx.MockTransport, brez Compose."""

import json

import httpx
import pytest
import smoke_test
from smoke_test import SmokeError

MACHINE_ID = "0d9c4a52-3b1e-4f6a-8c27-91e5a7b3d410"
PRODUCT = {"id": "c3a8f7d4-12ab-4e55-b6c9-5d0e8a1f2b73", "referencePrice": "1.20"}


class FakeGateway:
    """Odgovori po tabeli (metoda, pot) -> (status, telo); seznam pomeni zaporedne odgovore."""

    def __init__(self, routes):
        self.routes = routes
        self.requests = []

    def handle(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        answer = self.routes[(request.method, request.url.path)]
        if isinstance(answer, list):
            answer = answer.pop(0) if len(answer) > 1 else answer[0]
        status, body = answer
        return httpx.Response(status, json=body)

    def sent(self, index):
        return json.loads(self.requests[index].content)


@pytest.fixture(autouse=True)
def no_waiting(monkeypatch):
    monkeypatch.setattr(smoke_test.time, "sleep", lambda seconds: None)


def client(routes):
    gateway = FakeGateway(routes)
    http = httpx.Client(base_url="http://gateway", transport=httpx.MockTransport(gateway.handle))
    return http, gateway


def test_login_sends_token_with_next_requests():
    http, gateway = client(
        {
            ("POST", "/api/v1/auth/login"): (200, {"accessToken": "tok"}),
            ("GET", "/api/v1/auth/me"): (200, {}),
        }
    )

    smoke_test.login(http, "admin@vending.local", "geslo")
    http.get("/api/v1/auth/me")

    assert gateway.requests[1].headers["Authorization"] == "Bearer tok"


def test_create_machine_returns_id_and_key():
    http, gateway = client(
        {("POST", "/api/v1/machines"): (201, {"id": MACHINE_ID, "machineKey": "mk_1"})}
    )

    assert smoke_test.create_machine(http, "Smoke ab12") == (MACHINE_ID, "mk_1")
    assert gateway.sent(0)["code"] == "SMOKE-AB12"
    assert gateway.sent(0)["slots"] == [{"slotCode": "A1"}]


def test_refill_three_items_and_threshold_two():
    http, gateway = client(
        {
            ("POST", "/api/v1/inventory/refill"): (201, {}),
            ("PUT", f"/api/v1/inventory/{MACHINE_ID}/A1/threshold"): (200, {}),
        }
    )

    smoke_test.refill(http, MACHINE_ID, PRODUCT["id"])

    assert gateway.sent(0)["quantity"] == 3
    assert gateway.sent(1) == {"lowStockThreshold": 2}


def test_sale_uses_machine_key():
    http, gateway = client({("POST", "/api/v1/device/sales"): (201, {})})
    sale = smoke_test.sale_event(MACHINE_ID, PRODUCT, quantity=1)

    smoke_test.sell(http, "mk_1", sale)

    assert gateway.requests[0].headers["X-Machine-Key"] == "mk_1"
    assert gateway.sent(0) == sale


def test_unexpected_status_names_request():
    http, _ = client({("POST", "/api/v1/device/sales"): (503, {"code": "DEPENDENCY_UNAVAILABLE"})})

    with pytest.raises(SmokeError, match="expected 201, got 503 DEPENDENCY_UNAVAILABLE"):
        smoke_test.sell(http, "mk_1", smoke_test.sale_event(MACHINE_ID, PRODUCT, quantity=1))


def test_decrease_waits_for_sale_movement():
    sale_movement = {"type": "SALE", "quantity": 1}
    http, gateway = client(
        {
            ("GET", "/api/v1/inventory/movements"): [
                (200, {"items": []}),
                (200, {"items": [sale_movement]}),
            ],
            ("GET", f"/api/v1/inventory/{MACHINE_ID}/A1"): (200, {"quantity": 2}),
        }
    )

    smoke_test.wait_for_decrease(http, MACHINE_ID)

    assert len(gateway.requests) == 3


def test_decrease_with_wrong_quantity_fails():
    http, _ = client(
        {
            ("GET", "/api/v1/inventory/movements"): (200, {"items": [{"type": "SALE"}]}),
            ("GET", f"/api/v1/inventory/{MACHINE_ID}/A1"): (200, {"quantity": 3}),
        }
    )

    with pytest.raises(SmokeError, match="expected 2, got 3"):
        smoke_test.wait_for_decrease(http, MACHINE_ID)


def test_repeated_sale_must_not_add_movement():
    two = {"items": [{"type": "SALE"}, {"type": "SALE"}]}
    http, _ = client(
        {
            ("POST", "/api/v1/device/sales"): (200, {}),
            ("GET", "/api/v1/inventory/movements"): (200, two),
        }
    )

    with pytest.raises(SmokeError, match="expected 1, got 2"):
        smoke_test.repeat_sale(http, MACHINE_ID, "mk_1", {"saleId": "1"})


def test_waiting_has_a_limit():
    with pytest.raises(SmokeError, match="LOW_STOCK alert: not reached"):
        smoke_test.wait_until(lambda: [], "LOW_STOCK alert", timeout=0)


def test_retire_with_stock_needs_the_right_code():
    http, _ = client({("DELETE", f"/api/v1/machines/{MACHINE_ID}"): (409, {"code": "OTHER"})})

    with pytest.raises(SmokeError, match="expected 409 MACHINE_HAS_STOCK, got 409 OTHER"):
        smoke_test.retire_with_stock_is_rejected(http, MACHINE_ID)


def test_archive_is_rejected_until_sale_is_reconciled():
    url = f"/api/v1/products/{PRODUCT['id']}"
    http, gateway = client(
        {
            ("DELETE", url): [(409, {"code": "PRODUCT_HAS_OPEN_RECONCILIATION"}), (204, None)],
            ("PUT", "/api/v1/sales/s1/reconciliation"): (200, {}),
        }
    )

    smoke_test.archive_after_reconciliation(http, PRODUCT["id"], "s1")

    assert [(r.method, r.url.path) for r in gateway.requests] == [
        ("DELETE", url),
        ("PUT", "/api/v1/sales/s1/reconciliation"),
        ("DELETE", url),
    ]
    assert gateway.sent(1)["resolution"] == "WRITTEN_OFF"


def test_old_token_is_rejected_after_logout():
    http, gateway = client(
        {
            ("DELETE", "/api/v1/auth/session"): (204, None),
            ("GET", "/api/v1/auth/me"): (401, {"code": "UNAUTHENTICATED"}),
        }
    )

    smoke_test.logout(http, "tok")

    assert gateway.requests[1].headers["Authorization"] == "Bearer tok"


def test_failed_step_is_reported(monkeypatch, capsys):
    monkeypatch.setenv("BOOTSTRAP_OPERATOR_EMAIL", "admin@vending.local")
    monkeypatch.setenv("BOOTSTRAP_OPERATOR_PASSWORD", "napacno")
    gateway = FakeGateway({("POST", "/api/v1/auth/login"): (401, {"code": "INVALID_CREDENTIALS"})})

    assert smoke_test.main(transport=httpx.MockTransport(gateway.handle)) == 1

    assert "FAIL prijava: POST /api/v1/auth/login" in capsys.readouterr().out


def test_missing_environment(monkeypatch):
    monkeypatch.delenv("BOOTSTRAP_OPERATOR_PASSWORD", raising=False)

    assert smoke_test.main() == 1
