"""Odpis potekle zaloge DELETE /v1/inventory/expired (issue #54)."""

from datetime import timedelta
from uuid import uuid4

from app.domain.expiry import local_today
from tests.integration.helpers import (
    FRONTEND_API,
    OPERATOR,
    add_machine,
    movements,
    put_slot,
    slot_body,
)

YESTERDAY = str(local_today() - timedelta(days=1))
TODAY = str(local_today())


def remove_expired(client, machine_id):
    return client.delete(f"/v1/inventory/expired?machineId={machine_id}", headers=OPERATOR)


def test_removes_only_expired_slots(client, machine_id, product_id):
    put_slot(client, machine_id, "A1", slot_body(product_id, expiryDate=YESTERDAY, quantity=4))
    put_slot(client, machine_id, "A2", slot_body(product_id, expiryDate=TODAY))  # velja še danes

    response = remove_expired(client, machine_id)

    assert response.status_code == 200
    assert response.json() == {
        "removed": [
            {"slot": "A1", "productId": product_id, "batchCode": "L2026-11-17", "quantity": 4}
        ]
    }
    slot = client.get(f"/v1/inventory/{machine_id}/A1", headers=OPERATOR).json()
    assert (slot["productId"], slot["quantity"], slot["lowStockThreshold"]) == (None, 0, 2)
    assert client.get(f"/v1/inventory/{machine_id}/A2", headers=OPERATOR).json()["quantity"] == 8
    assert movements(machine_id) == [("SET", 4), ("SET", 8), ("REMOVAL", 4)]


def test_repeated_removal_returns_empty_list(client, machine_id, product_id):
    put_slot(client, machine_id, "A1", slot_body(product_id, expiryDate=YESTERDAY))
    remove_expired(client, machine_id)

    response = remove_expired(client, machine_id)

    assert response.status_code == 200
    assert response.json() == {"removed": []}
    assert movements(machine_id) == [("SET", 8), ("REMOVAL", 8)]


def test_other_machines_are_not_touched(client, machine_id, product_id, services):
    other = add_machine(services)
    put_slot(client, other, "A1", slot_body(product_id, expiryDate=YESTERDAY))

    remove_expired(client, machine_id)

    assert client.get(f"/v1/inventory/{other}/A1", headers=OPERATOR).json()["quantity"] == 8


def test_machine_id_is_required(client):
    response = client.delete("/v1/inventory/expired", headers=OPERATOR)

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_unknown_machine(client, services):
    response = remove_expired(client, uuid4())

    assert response.status_code == 404
    assert response.json()["code"] == "MACHINE_NOT_FOUND"


def test_machine_service_down(client, machine_id, services):
    services["down"] = True

    response = remove_expired(client, machine_id)

    assert response.status_code == 503


def test_only_operator_may_remove(client, machine_id):
    response = client.delete(f"/v1/inventory/expired?machineId={machine_id}", headers=FRONTEND_API)

    assert response.status_code == 403
