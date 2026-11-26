"""Outbox nizke zaloge in pošiljanje poročil LOW_STOCK v Monitoring (issue #33)."""

import json
from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from uuid import uuid4

import httpx
import pytest
from app.clients.monitoring import MonitoringClient
from app.core.database import get_engine
from app.domain.expiry import local_today
from app.domain.low_stock import send_pending
from sqlalchemy.orm import Session
from tests.integration.helpers import (
    OPERATOR,
    SALES,
    add_machine,
    put_slot,
    refill,
    refill_body,
    slot_body,
)


@pytest.fixture
def monitoring():
    """Lažni Monitoring: shrani prejeta poročila; `down` pomeni, da ne odgovarja."""
    fake = {"reports": [], "requests": [], "down": False}

    def handle(request: httpx.Request) -> httpx.Response:
        if fake["down"]:
            raise httpx.ConnectError("monitoring is down")
        fake["requests"].append(request)
        fake["reports"].append(json.loads(request.content))
        return httpx.Response(201, json={"outcome": "OPENED"})

    transport = httpx.MockTransport(handle)
    fake["client"] = MonitoringClient(
        "Monitoring", "http://monitoring", "inventory-token", transport
    )
    return fake


def deliver(monitoring):
    """En prehod delavca; vrne poročila, ki jih je Monitoring prejel v tem prehodu."""
    before = len(monitoring["reports"])
    with Session(get_engine()) as session:
        send_pending(session, monitoring["client"])
    return monitoring["reports"][before:]


def sell(client, machine_id, product_id, slot="A1"):
    sale = {"saleId": str(uuid4()), "productId": product_id, "quantity": 1}
    return client.post(f"/v1/inventory/{machine_id}/{slot}/decrease", json=sale, headers=SALES)


def low(slot, product_id, quantity, threshold=2):
    return {
        "slotCode": slot,
        "productId": product_id,
        "quantity": quantity,
        "lowStockThreshold": threshold,
    }


def test_sale_down_to_threshold_sends_report(client, machine_id, product_id, monitoring):
    put_slot(client, machine_id, "A1", slot_body(product_id, quantity=3))
    assert deliver(monitoring) == []  # 3 je nad pragom 2

    sell(client, machine_id, product_id)

    assert deliver(monitoring) == [
        {
            "machineId": machine_id,
            "type": "LOW_STOCK",
            "version": 1,
            "slots": [low("A1", product_id, 2)],
        }
    ]
    assert deliver(monitoring) == []  # poslano poročilo se ne pošlje znova


def test_report_is_posted_to_alerts_with_inventory_token(
    client, machine_id, product_id, monitoring
):
    put_slot(client, machine_id, "A1", slot_body(product_id, quantity=1))

    deliver(monitoring)

    request = monitoring["requests"][0]
    assert (request.method, request.url.path) == ("POST", "/v1/alerts")
    assert request.headers["X-Service-Token"] == "inventory-token"


def test_only_latest_report_is_sent(client, machine_id, product_id, monitoring):
    put_slot(client, machine_id, "A1", slot_body(product_id, quantity=3))
    sell(client, machine_id, product_id)  # verzija 1: 2 kosa
    sell(client, machine_id, product_id)  # verzija 2: 1 kos

    reports = deliver(monitoring)

    assert [(r["version"], r["slots"]) for r in reports] == [(2, [low("A1", product_id, 1)])]


def test_refill_above_threshold_sends_empty_slots(client, machine_id, product_id, monitoring):
    body = refill_body(machine_id, product_id)  # 5 kosov iste serije
    batch = {"batchCode": body["batchCode"], "expiryDate": body["expiryDate"]}
    put_slot(client, machine_id, "A1", slot_body(product_id, quantity=2, **batch))
    deliver(monitoring)

    refill(client, body)

    assert [(r["version"], r["slots"]) for r in deliver(monitoring)] == [(2, [])]


def test_threshold_change_sends_new_report(client, machine_id, product_id, monitoring):
    put_slot(client, machine_id, "A1", slot_body(product_id))
    url = f"/v1/inventory/{machine_id}/A1/threshold"

    client.put(url, json={"lowStockThreshold": 10}, headers=OPERATOR)
    assert deliver(monitoring)[0]["slots"] == [low("A1", product_id, 8, threshold=10)]

    client.put(url, json={"lowStockThreshold": 2}, headers=OPERATOR)
    assert deliver(monitoring)[0]["slots"] == []

    client.put(url, json={"lowStockThreshold": 2}, headers=OPERATOR)
    assert deliver(monitoring) == []  # seznam se ni spremenil


def test_emptied_slot_is_not_in_report(client, machine_id, product_id, monitoring):
    put_slot(client, machine_id, "A1", slot_body(product_id, quantity=1))
    deliver(monitoring)

    client.delete(f"/v1/inventory/{machine_id}/A1", headers=OPERATOR)

    assert deliver(monitoring)[0]["slots"] == []


def test_expired_removal_sends_new_report(client, machine_id, product_id, monitoring):
    yesterday = str(local_today() - timedelta(days=1))
    put_slot(client, machine_id, "A1", slot_body(product_id, quantity=1, expiryDate=yesterday))
    put_slot(client, machine_id, "A2", slot_body(product_id, quantity=1))
    deliver(monitoring)

    client.delete(f"/v1/inventory/expired?machineId={machine_id}", headers=OPERATOR)

    assert deliver(monitoring)[0]["slots"] == [low("A2", product_id, 1)]


def test_unreachable_monitoring_does_not_block_sale(client, machine_id, product_id, monitoring):
    put_slot(client, machine_id, "A1", slot_body(product_id, quantity=3))
    monitoring["down"] = True

    response = sell(client, machine_id, product_id)
    assert response.status_code == 201
    assert response.json()["quantityAfter"] == 2
    assert deliver(monitoring) == []

    monitoring["down"] = False  # naslednji prehod poskusi znova
    assert [r["version"] for r in deliver(monitoring)] == [1]


def test_versions_are_counted_per_machine(client, services, product_id, monitoring):
    first, second = add_machine(services), add_machine(services)
    put_slot(client, first, "A1", slot_body(product_id, quantity=1))
    put_slot(client, second, "A1", slot_body(product_id, quantity=1))

    reports = deliver(monitoring)

    assert sorted((r["machineId"], r["version"]) for r in reports) == sorted(
        [(first, 1), (second, 1)]
    )


def test_concurrent_sales_get_consecutive_versions(client, machine_id, product_id, monitoring):
    put_slot(client, machine_id, "A1", slot_body(product_id, quantity=3))
    put_slot(client, machine_id, "A2", slot_body(product_id, quantity=3))

    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(lambda slot: sell(client, machine_id, product_id, slot), ["A1", "A2"] * 2))

    # Štiri prodaje so štirikrat spremenile seznam; zadnje poročilo vidi vse.
    assert deliver(monitoring) == [
        {
            "machineId": machine_id,
            "type": "LOW_STOCK",
            "version": 4,
            "slots": [low("A1", product_id, 1), low("A2", product_id, 1)],
        }
    ]
