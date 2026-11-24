"""Polnitev /v1/inventory/refill in pregled premikov /v1/inventory/movements (issue #22)."""

from datetime import UTC, datetime, timedelta
from uuid import uuid4

import pytest
from app.domain.expiry import local_today
from tests.integration.helpers import (
    FRONTEND_API,
    OPERATOR,
    SALES,
    add_machine,
    add_product,
    movements,
    put_slot,
    refill,
    refill_body,
    slot_body,
)

# --- polnitev ----------------------------------------------------------------------------


def test_refill_empty_slot(client, machine_id, product_id):
    body = refill_body(machine_id, product_id)

    response = refill(client, body)

    assert response.status_code == 201
    slot = response.json()
    assert (slot["productId"], slot["batchCode"], slot["quantity"]) == (product_id, "R2026-11", 5)
    assert slot["expiryDate"] == body["expiryDate"]
    assert movements(machine_id) == [("REFILL", 5)]


def test_repeated_refill_does_not_double_quantity(client, machine_id, product_id, services):
    body = refill_body(machine_id, product_id)
    refill(client, body)
    services["down"] = True  # ponovitev ne kliče drugih storitev

    response = refill(client, body)

    assert response.status_code == 200
    assert response.json()["quantity"] == 5
    assert movements(machine_id) == [("REFILL", 5)]


def test_same_refill_id_with_other_body_is_conflict(client, machine_id, product_id):
    body = refill_body(machine_id, product_id)
    refill(client, body)

    response = refill(client, body | {"quantity": 6})

    assert response.status_code == 409
    assert response.json()["code"] == "REFILL_ID_CONFLICT"


def test_same_batch_is_added_and_threshold_kept(client, machine_id, product_id):
    put_slot(client, machine_id, "A1", slot_body(product_id, batchCode="R2026-11", quantity=3))
    expiry = client.get(f"/v1/inventory/{machine_id}/A1", headers=OPERATOR).json()["expiryDate"]

    response = refill(client, refill_body(machine_id, product_id, expiryDate=expiry))

    assert response.json()["quantity"] == 8
    assert response.json()["lowStockThreshold"] == 2
    assert movements(machine_id) == [("SET", 3), ("REFILL", 5)]


def test_other_batch_in_full_slot_is_rejected(client, machine_id, product_id):
    refill(client, refill_body(machine_id, product_id))

    response = refill(client, refill_body(machine_id, product_id, batchCode="DRUGA"))

    assert response.status_code == 409
    assert response.json()["code"] == "SLOT_HAS_OTHER_BATCH"


def test_other_product_in_full_slot_is_rejected(client, machine_id, product_id, services):
    refill(client, refill_body(machine_id, product_id))

    response = refill(client, refill_body(machine_id, add_product(services)))

    assert response.status_code == 409
    assert response.json()["code"] == "SLOT_HAS_OTHER_BATCH"


def test_new_batch_after_slot_is_sold_out(client, machine_id, product_id):
    refill(client, refill_body(machine_id, product_id, quantity=1))
    sale = {"saleId": str(uuid4()), "productId": product_id, "quantity": 1}
    client.post(f"/v1/inventory/{machine_id}/A1/decrease", json=sale, headers=SALES)

    response = refill(client, refill_body(machine_id, product_id, batchCode="NOVA"))

    assert response.status_code == 201
    assert response.json()["batchCode"] == "NOVA"


def test_expired_batch_is_rejected(client, machine_id, product_id):
    yesterday = str(local_today() - timedelta(days=1))

    response = refill(client, refill_body(machine_id, product_id, expiryDate=yesterday))

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_batch_expiring_today_is_accepted(client, machine_id, product_id):
    today = str(local_today())

    response = refill(client, refill_body(machine_id, product_id, expiryDate=today))

    assert response.status_code == 201


def test_slot_holds_at_most_1000(client, machine_id, product_id):
    body = refill_body(machine_id, product_id, quantity=1000)
    refill(client, body)

    response = refill(client, refill_body(machine_id, product_id, quantity=1))

    assert response.status_code == 422


def test_archived_product_cannot_be_refilled(client, machine_id, services):
    archived = add_product(services, status="ARCHIVED")

    response = refill(client, refill_body(machine_id, archived))

    assert response.status_code == 409
    assert response.json()["code"] == "PRODUCT_ARCHIVED"


def test_unknown_product_and_machine(client, machine_id, product_id):
    unknown_product = refill(client, refill_body(machine_id, str(uuid4())))
    unknown_machine = refill(client, refill_body(str(uuid4()), product_id))
    unknown_slot = refill(client, refill_body(machine_id, product_id, slot="Z9"))

    assert unknown_product.json()["code"] == "REFERENCE_NOT_FOUND"
    assert unknown_machine.json()["code"] == "MACHINE_NOT_FOUND"
    assert unknown_slot.json()["code"] == "SLOT_NOT_FOUND"


@pytest.mark.parametrize("changes", [{"quantity": 0}, {"slot": ""}, {"expiryDate": "jutri"}])
def test_invalid_refill_is_validation_error(client, machine_id, product_id, changes):
    response = refill(client, refill_body(machine_id, product_id, **changes))

    assert response.status_code == 422


def test_only_operator_may_refill(client, machine_id, product_id):
    body = refill_body(machine_id, product_id)

    response = client.post("/v1/inventory/refill", json=body, headers=FRONTEND_API)

    assert response.status_code == 403


# --- premiki -----------------------------------------------------------------------------


@pytest.fixture
def history(client, services, product_id):
    """Dva avtomata: v prvem SET in REMOVAL, v drugem REFILL drugega izdelka."""
    first, second = add_machine(services), add_machine(services)
    other = add_product(services)
    put_slot(client, first, "A1", slot_body(product_id))
    client.delete(f"/v1/inventory/{first}/A1", headers=OPERATOR)
    refill(client, refill_body(second, other))
    return {"first": first, "second": second, "other": other}


def list_types(client, **params):
    response = client.get("/v1/inventory/movements", params=params, headers=OPERATOR)
    assert response.status_code == 200
    return [m["type"] for m in response.json()["items"]]


def test_movements_newest_first(client, history):
    assert list_types(client) == ["REFILL", "REMOVAL", "SET"]


def test_movements_filters(client, history, product_id):
    assert list_types(client, machineId=history["first"]) == ["REMOVAL", "SET"]
    assert list_types(client, productId=history["other"]) == ["REFILL"]
    assert list_types(client, type="SET") == ["SET"]
    assert list_types(client, productId=product_id, type="REMOVAL") == ["REMOVAL"]


def test_movements_time_window(client, history):
    hour_ago = (datetime.now(UTC) - timedelta(hours=1)).isoformat()
    in_hour = (datetime.now(UTC) + timedelta(hours=1)).isoformat()

    assert len(list_types(client, **{"from": hour_ago, "to": in_hour})) == 3
    assert list_types(client, **{"from": in_hour}) == []
    assert list_types(client, to=hour_ago) == []


def test_movements_are_paginated(client, history):
    response = client.get("/v1/inventory/movements?limit=1&offset=1", headers=OPERATOR).json()

    assert [m["type"] for m in response["items"]] == ["REMOVAL"]
    assert response["total"] == 3


def test_movement_fields(client, history):
    item = client.get("/v1/inventory/movements?type=REFILL", headers=OPERATOR).json()["items"][0]

    assert item["machineId"] == history["second"]
    assert item["quantity"] == 5
    assert item["refillId"] is not None
    assert item["saleId"] is None


def test_only_operator_may_read_movements(client):
    response = client.get("/v1/inventory/movements", headers=FRONTEND_API)

    assert response.status_code == 403
