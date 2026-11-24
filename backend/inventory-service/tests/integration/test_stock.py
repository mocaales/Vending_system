"""Zaloga predalov /v1/inventory (issue #20). Machine in Product sta lažna (fixture `services`)."""

from uuid import uuid4

import pytest
from app.core.database import get_engine
from app.repository.models import Movement
from sqlalchemy import select
from sqlalchemy.orm import Session
from tests.integration.helpers import FRONTEND_API, MACHINE, OPERATOR, SALES


def add_machine(services, slots=("A1", "A2"), status="ACTIVE"):
    machine_id = str(uuid4())
    services["machines"][machine_id] = {
        "id": machine_id,
        "status": status,
        "slots": [{"slotCode": code} for code in slots],
    }
    return machine_id


def add_product(services, status="ACTIVE"):
    product_id = str(uuid4())
    services["products"][product_id] = {"id": product_id, "status": status}
    return product_id


def slot_body(product_id, **changes):
    body = {
        "productId": product_id,
        "batchCode": "L2026-11-17",
        "expiryDate": "2026-12-01",
        "quantity": 8,
        "lowStockThreshold": 2,
    }
    return body | changes


def put_slot(client, machine_id, slot, body):
    return client.put(f"/v1/inventory/{machine_id}/{slot}", json=body, headers=OPERATOR)


def movements(machine_id):
    with Session(get_engine()) as session:
        rows = session.scalars(
            select(Movement).where(Movement.machine_id == machine_id).order_by(Movement.occurred_at)
        ).all()
        return [(m.type, m.quantity) for m in rows]


@pytest.fixture
def machine_id(services):
    return add_machine(services)


@pytest.fixture
def product_id(services):
    return add_product(services)


# --- nastavitev predala ------------------------------------------------------------------


def test_set_slot_writes_set_movement(client, machine_id, product_id):
    response = put_slot(client, machine_id, "A1", slot_body(product_id))

    assert response.status_code == 200
    assert response.json() == {
        "machineId": machine_id,
        "slot": "A1",
        "productId": product_id,
        "batchCode": "L2026-11-17",
        "expiryDate": "2026-12-01",
        "quantity": 8,
        "lowStockThreshold": 2,
    }
    assert movements(machine_id) == [("SET", 8)]


def test_set_slot_is_idempotent(client, machine_id, product_id, services):
    first = put_slot(client, machine_id, "A1", slot_body(product_id))
    services["down"] = True  # ponovitev ne kliče drugih storitev
    second = put_slot(client, machine_id, "A1", slot_body(product_id))

    assert first.json() == second.json()
    assert movements(machine_id) == [("SET", 8)]


def test_same_product_may_be_corrected(client, machine_id, product_id):
    put_slot(client, machine_id, "A1", slot_body(product_id))

    response = put_slot(client, machine_id, "A1", slot_body(product_id, quantity=5))

    assert response.json()["quantity"] == 5
    assert movements(machine_id) == [("SET", 8), ("SET", 5)]


def test_other_product_in_full_slot_is_rejected(client, machine_id, product_id, services):
    put_slot(client, machine_id, "A1", slot_body(product_id))

    response = put_slot(client, machine_id, "A1", slot_body(add_product(services)))

    assert response.status_code == 409
    assert response.json()["code"] == "SLOT_NOT_EMPTY"


def test_other_product_after_emptying_is_allowed(client, machine_id, product_id, services):
    put_slot(client, machine_id, "A1", slot_body(product_id))
    client.delete(f"/v1/inventory/{machine_id}/A1", headers=OPERATOR)

    response = put_slot(client, machine_id, "A1", slot_body(add_product(services)))

    assert response.status_code == 200


@pytest.mark.parametrize(
    ("product_status", "status", "code"),
    [("ARCHIVED", 409, "PRODUCT_ARCHIVED"), (None, 422, "REFERENCE_NOT_FOUND")],
)
def test_product_must_exist_and_be_active(
    client, machine_id, services, product_status, status, code
):
    product_id = add_product(services, product_status) if product_status else str(uuid4())

    response = put_slot(client, machine_id, "A1", slot_body(product_id))

    assert response.status_code == status
    assert response.json()["code"] == code


def test_machine_and_slot_must_exist(client, product_id, services):
    retired = add_machine(services, status="RETIRED")
    known = add_machine(services)

    unknown_machine = put_slot(client, uuid4(), "A1", slot_body(product_id))
    unknown_slot = put_slot(client, known, "Z9", slot_body(product_id))
    retired_machine = put_slot(client, retired, "A1", slot_body(product_id))

    assert unknown_machine.json()["code"] == "MACHINE_NOT_FOUND"
    assert unknown_slot.json()["code"] == "SLOT_NOT_FOUND"
    assert retired_machine.json()["code"] == "MACHINE_RETIRED"


def test_set_slot_when_machine_is_down(client, machine_id, product_id, services):
    services["down"] = True

    response = put_slot(client, machine_id, "A1", slot_body(product_id))

    assert response.status_code == 503
    assert response.json()["code"] == "DEPENDENCY_UNAVAILABLE"


def test_set_slot_sends_service_token_and_trace_id(client, machine_id, product_id, services):
    headers = OPERATOR | {"X-Trace-Id": "trace-123"}
    client.put(f"/v1/inventory/{machine_id}/A1", json=slot_body(product_id), headers=headers)

    for request in services["requests"]:
        assert request.headers["X-Service-Token"] == "inventory-token"
        assert request.headers["X-Trace-Id"] == "trace-123"
    assert [r.url.path for r in services["requests"]] == [
        f"/v1/machines/{machine_id}",
        f"/v1/products/{product_id}",
    ]


@pytest.mark.parametrize(
    "changes",
    [{"quantity": 0}, {"quantity": 1001}, {"lowStockThreshold": -1}, {"batchCode": ""}, {"x": 1}],
)
def test_invalid_slot_is_validation_error(client, machine_id, product_id, changes):
    response = put_slot(client, machine_id, "A1", slot_body(product_id, **changes))

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_only_operator_may_set(client, machine_id, product_id):
    response = client.put(
        f"/v1/inventory/{machine_id}/A1", json=slot_body(product_id), headers=FRONTEND_API
    )

    assert response.status_code == 403


# --- branje ------------------------------------------------------------------------------


def test_get_slot(client, machine_id, product_id):
    put_slot(client, machine_id, "A1", slot_body(product_id))

    response = client.get(f"/v1/inventory/{machine_id}/A1", headers=SALES)

    assert response.status_code == 200
    assert response.json()["quantity"] == 8


def test_get_slot_never_set(client, machine_id):
    response = client.get(f"/v1/inventory/{machine_id}/A1", headers=OPERATOR)

    assert response.status_code == 404
    assert response.json()["code"] == "SLOT_NOT_FOUND"


def test_machine_stock_is_sorted_by_slot(client, machine_id, product_id):
    put_slot(client, machine_id, "A2", slot_body(product_id))
    put_slot(client, machine_id, "A1", slot_body(product_id, quantity=3))

    page = client.get(f"/v1/inventory/{machine_id}?limit=200&offset=0", headers=MACHINE).json()

    assert [(s["slot"], s["quantity"]) for s in page["items"]] == [("A1", 3), ("A2", 8)]
    assert page["total"] == 2


def test_unknown_machine_is_empty_for_machine_service(client, services):
    response = client.get(f"/v1/inventory/{uuid4()}", headers=MACHINE)

    assert response.status_code == 200
    assert response.json()["items"] == []
    assert services["requests"] == []  # branje druge storitve ne kliče Machine


def test_unknown_machine_is_not_found_for_operator(client, services):
    response = client.get(f"/v1/inventory/{uuid4()}", headers=OPERATOR)

    assert response.status_code == 404
    assert response.json()["code"] == "MACHINE_NOT_FOUND"


def test_product_stock_over_machines(client, product_id, services):
    first, second = add_machine(services), add_machine(services)
    put_slot(client, first, "A1", slot_body(product_id))
    put_slot(client, second, "A2", slot_body(product_id))
    put_slot(client, second, "A1", slot_body(add_product(services)))

    page = client.get(f"/v1/inventory/product/{product_id}", headers=FRONTEND_API).json()

    assert page["total"] == 2
    assert {s["machineId"] for s in page["items"]} == {first, second}


def test_sales_may_not_read_product_stock(client, product_id):
    response = client.get(f"/v1/inventory/product/{product_id}", headers=SALES)

    assert response.status_code == 403


# --- praznjenje in prag ------------------------------------------------------------------


def test_empty_slot_writes_removal_and_keeps_threshold(client, machine_id, product_id):
    put_slot(client, machine_id, "A1", slot_body(product_id))

    response = client.delete(f"/v1/inventory/{machine_id}/A1", headers=OPERATOR)

    assert response.status_code == 204
    slot = client.get(f"/v1/inventory/{machine_id}/A1", headers=OPERATOR).json()
    assert slot["productId"] is None
    assert slot["batchCode"] is None
    assert slot["quantity"] == 0
    assert slot["lowStockThreshold"] == 2
    assert movements(machine_id) == [("SET", 8), ("REMOVAL", 8)]


def test_empty_slot_again_writes_no_movement(client, machine_id, product_id):
    put_slot(client, machine_id, "A1", slot_body(product_id))
    client.delete(f"/v1/inventory/{machine_id}/A1", headers=OPERATOR)

    response = client.delete(f"/v1/inventory/{machine_id}/A1", headers=OPERATOR)

    assert response.status_code == 204
    assert movements(machine_id) == [("SET", 8), ("REMOVAL", 8)]


def test_empty_slot_never_set(client, machine_id):
    response = client.delete(f"/v1/inventory/{machine_id}/A1", headers=OPERATOR)

    assert response.status_code == 404


def test_set_threshold(client, machine_id, product_id):
    put_slot(client, machine_id, "A1", slot_body(product_id))
    url = f"/v1/inventory/{machine_id}/A1/threshold"

    first = client.put(url, json={"lowStockThreshold": 5}, headers=OPERATOR)
    second = client.put(url, json={"lowStockThreshold": 5}, headers=OPERATOR)

    assert first.status_code == 200
    assert first.json() == second.json()
    assert first.json()["lowStockThreshold"] == 5
    assert movements(machine_id) == [("SET", 8)]


def test_set_threshold_of_unknown_slot(client, machine_id):
    response = client.put(
        f"/v1/inventory/{machine_id}/A1/threshold", json={"lowStockThreshold": 5}, headers=OPERATOR
    )

    assert response.status_code == 404
    assert response.json()["code"] == "SLOT_NOT_FOUND"
