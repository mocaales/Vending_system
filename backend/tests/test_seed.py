"""Začetni podatki prek gatewaya (issue #65); gateway nadomesti httpx.MockTransport."""

import json
from uuid import uuid4

import httpx
import pytest
import seed

TOKEN = "operator-token"


class FakeGateway:
    """Gateway po docs/03 §9 s stanjem v pomnilniku; `fail` vrne 503 na izbrani poti."""

    def __init__(self):
        self.categories, self.products, self.machines = [], [], []
        self.stock = {}  # (machineId, slot) -> telo PUT
        self.keys_created = 0
        self.fail = None

    def handle(self, request: httpx.Request) -> httpx.Response:
        path, method = request.url.path, request.method
        if path == "/api/v1/auth/login":
            body = json.loads(request.content)
            if body["password"] != "seed-password":
                return httpx.Response(401, json={"code": "INVALID_CREDENTIALS"})
            return httpx.Response(200, json={"accessToken": TOKEN, "tokenType": "Bearer"})
        if request.headers.get("Authorization") != f"Bearer {TOKEN}":
            return httpx.Response(401, json={"code": "UNAUTHENTICATED"})
        if path == self.fail:
            return httpx.Response(503, json={"code": "DEPENDENCY_UNAVAILABLE"})

        parts = path.removeprefix("/api/v1/").split("/")
        body = json.loads(request.content) if request.content else None
        if parts[0] in ("categories", "products") and method == "GET":
            return self.page(getattr(self, parts[0]), "name", request.url.params["q"])
        if parts[0] in ("categories", "products") and method == "POST":
            record = {"id": str(uuid4()), **body}
            getattr(self, parts[0]).append(record)
            return httpx.Response(201, json=record)
        if parts == ["machines"] and method == "GET":
            return self.page(self.machines, "code", request.url.params["q"])
        if parts == ["machines"] and method == "POST":
            if any(m["code"] == body["code"] for m in self.machines):
                return httpx.Response(409, json={"code": "MACHINE_CODE_TAKEN"})
            machine = {"id": str(uuid4()), **body, "status": "ACTIVE"}
            self.machines.append(machine)
            return httpx.Response(201, json={**machine, "machineKey": f"mk_{uuid4().hex}"})
        if parts[0] == "machines" and parts[2:] == ["keys"]:
            self.keys_created += 1
            return httpx.Response(201, json={"machineKey": f"mk_{uuid4().hex}"})
        if parts[0] == "inventory" and method == "GET":
            found = (parts[1], parts[2]) in self.stock
            return httpx.Response(200 if found else 404, json={})
        if parts[0] == "inventory" and method == "PUT":
            self.stock[(parts[1], parts[2])] = body
            return httpx.Response(200, json=body)
        return httpx.Response(404, json={"code": "NOT_FOUND"})

    @staticmethod
    def page(records, field, q):
        items = [r for r in records if q in r[field]]
        return httpx.Response(200, json={"items": items, "total": len(items)})


@pytest.fixture
def gateway(tmp_path, monkeypatch):
    monkeypatch.setattr(seed, "KEYS_FILE", tmp_path / "simulator" / "machines.local.json")
    monkeypatch.setenv("BOOTSTRAP_OPERATOR_EMAIL", "admin@vending.local")
    monkeypatch.setenv("BOOTSTRAP_OPERATOR_PASSWORD", "seed-password")
    return FakeGateway()


def run(gateway):
    return seed.main(transport=httpx.MockTransport(gateway.handle))


def saved_machines():
    return json.loads(seed.KEYS_FILE.read_text(encoding="utf-8"))["machines"]


def test_creates_data_and_writes_keys_for_simulator(gateway):
    assert run(gateway) == 0

    assert [c["name"] for c in gateway.categories] == seed.CATEGORIES
    assert len(gateway.products) == len(seed.PRODUCTS)
    assert [m["code"] for m in gateway.machines] == ["MB-FERI", "LJ-CENTER"]
    assert len(gateway.stock) == 6  # vsi predali obeh avtomatov

    first = saved_machines()[0]
    assert first["code"] == "MB-FERI"
    assert first["machineKey"].startswith("mk_")
    milk = next(p for p in gateway.products if p["name"] == "Mleko 1 l")
    assert first["slots"][0] == {
        "slotId": "A1",
        "productId": milk["id"],
        "unitPrice": "1.20",
        "currency": "EUR",
    }


def test_slot_is_filled_with_product_and_fresh_batch(gateway):
    run(gateway)

    machine_id = saved_machines()[0]["machineId"]
    body = gateway.stock[(machine_id, "B1")]
    klobasa = next(p for p in gateway.products if p["name"] == "Domača klobasa")
    assert (body["productId"], body["quantity"], body["lowStockThreshold"]) == (
        klobasa["id"],
        10,
        2,
    )
    assert body["batchCode"].startswith("SEED-")


def test_second_run_creates_no_duplicates_and_keeps_keys(gateway):
    run(gateway)
    keys = [m["machineKey"] for m in saved_machines()]
    gateway.stock[(saved_machines()[0]["machineId"], "A1")]["quantity"] = 3  # med tem prodaja

    assert run(gateway) == 0

    assert len(gateway.categories) == 3
    assert len(gateway.products) == 4
    assert len(gateway.machines) == 2
    assert gateway.keys_created == 0
    assert [m["machineKey"] for m in saved_machines()] == keys
    assert gateway.stock[(saved_machines()[0]["machineId"], "A1")]["quantity"] == 3


def test_lost_keys_file_creates_new_key_not_new_machine(gateway):
    run(gateway)
    seed.KEYS_FILE.unlink()

    assert run(gateway) == 0

    assert len(gateway.machines) == 2
    assert gateway.keys_created == 2
    assert all(m["machineKey"].startswith("mk_") for m in saved_machines())


def test_wrong_password_stops_without_writing(gateway, monkeypatch, capsys):
    monkeypatch.setenv("BOOTSTRAP_OPERATOR_PASSWORD", "napacno")

    assert run(gateway) == 1

    assert "401 INVALID_CREDENTIALS" in capsys.readouterr().out
    assert not seed.KEYS_FILE.exists()


def test_missing_environment(gateway, monkeypatch):
    monkeypatch.delenv("BOOTSTRAP_OPERATOR_PASSWORD")

    assert run(gateway) == 1


def test_gateway_error_is_reported(gateway, capsys):
    gateway.fail = "/api/v1/products"

    assert run(gateway) == 1

    assert "GET /api/v1/products: 503 DEPENDENCY_UNAVAILABLE" in capsys.readouterr().out
