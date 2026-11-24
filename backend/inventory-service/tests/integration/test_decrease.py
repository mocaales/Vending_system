"""Zmanjšanje zaloge po prodaji, enkrat na saleId (issue #21)."""

from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4

import pytest
from tests.integration.helpers import OPERATOR, SALES, add_product, movements, put_slot, slot_body


@pytest.fixture
def slot_url(client, machine_id, product_id):
    """Predal A1 z 8 kosi izdelka `product_id`."""
    put_slot(client, machine_id, "A1", slot_body(product_id))
    return f"/v1/inventory/{machine_id}/A1"


def decrease(client, slot_url, product_id, sale_id=None, quantity=1):
    body = {"saleId": sale_id or str(uuid4()), "productId": product_id, "quantity": quantity}
    return client.post(f"{slot_url}/decrease", json=body, headers=SALES)


def quantity(client, slot_url):
    return client.get(slot_url, headers=OPERATOR).json()["quantity"]


def test_decrease_applies_and_writes_sale_movement(client, slot_url, product_id, machine_id):
    response = decrease(client, slot_url, product_id, quantity=3)

    assert response.status_code == 201
    assert response.json() == {"result": "APPLIED", "quantityAfter": 5}  # brez `reason`
    assert quantity(client, slot_url) == 5
    assert movements(machine_id) == [("SET", 8), ("SALE", 3)]


def test_same_sale_again_returns_stored_result(client, slot_url, product_id, machine_id):
    sale_id = str(uuid4())
    first = decrease(client, slot_url, product_id, sale_id)

    second = decrease(client, slot_url, product_id, sale_id)

    assert second.status_code == 200
    assert second.json() == first.json()
    assert quantity(client, slot_url) == 7
    assert movements(machine_id) == [("SET", 8), ("SALE", 1)]


def test_same_sale_with_other_body_is_conflict(client, slot_url, product_id):
    sale_id = str(uuid4())
    decrease(client, slot_url, product_id, sale_id)

    response = decrease(client, slot_url, product_id, sale_id, quantity=2)

    assert response.status_code == 409
    assert response.json()["code"] == "SALE_ID_CONFLICT"


def test_other_product_needs_reconciliation(client, slot_url, services, machine_id):
    response = decrease(client, slot_url, add_product(services))

    assert response.status_code == 201
    assert response.json() == {
        "result": "NEEDS_RECONCILIATION",
        "reason": "PRODUCT_MISMATCH",
        "quantityAfter": 8,
    }
    assert movements(machine_id) == [("SET", 8)]


def test_too_little_stock_needs_reconciliation(client, slot_url, product_id):
    response = decrease(client, slot_url, product_id, quantity=9)

    assert response.status_code == 201
    assert response.json()["reason"] == "INSUFFICIENT_STOCK"
    assert quantity(client, slot_url) == 8  # zaloga nikoli ni negativna


def test_empty_slot_is_product_mismatch(client, slot_url, product_id):
    client.delete(slot_url, headers=OPERATOR)

    response = decrease(client, slot_url, product_id)

    assert response.json()["reason"] == "PRODUCT_MISMATCH"


def test_slot_never_set(client, machine_id, product_id):
    response = decrease(client, f"/v1/inventory/{machine_id}/A2", product_id)

    assert response.status_code == 404
    assert response.json()["code"] == "SLOT_NOT_FOUND"


def test_only_sales_may_decrease(client, slot_url, product_id):
    body = {"saleId": str(uuid4()), "productId": product_id, "quantity": 1}

    response = client.post(f"{slot_url}/decrease", json=body, headers=OPERATOR)

    assert response.status_code == 403
    assert response.json()["code"] == "CALLER_NOT_ALLOWED"


@pytest.mark.parametrize("changes", [{"quantity": 0}, {"quantity": 101}, {"x": 1}])
def test_invalid_decrease_is_validation_error(client, slot_url, product_id, changes):
    body = {"saleId": str(uuid4()), "productId": product_id, "quantity": 1} | changes

    response = client.post(f"{slot_url}/decrease", json=body, headers=SALES)

    assert response.status_code == 422


def test_concurrent_calls_with_same_sale_decrease_once(client, slot_url, product_id, machine_id):
    sale_id = str(uuid4())

    with ThreadPoolExecutor(max_workers=5) as pool:
        responses = list(
            pool.map(lambda _: decrease(client, slot_url, product_id, sale_id), range(5))
        )

    assert sorted(r.status_code for r in responses) == [200, 200, 200, 200, 201]
    assert {r.json()["quantityAfter"] for r in responses} == {7}
    assert movements(machine_id) == [("SET", 8), ("SALE", 1)]
