"""Izdelki /v1/products (issue #19). Sales je nadomeščen z lažnim strežnikom."""

from uuid import uuid4

import httpx
import pytest
from app.clients.sales import SalesClient, get_sales_client
from tests.integration.helpers import FRONTEND_API, MONITORING, OPERATOR, SALES


@pytest.fixture
def sales(client):
    """Lažni Sales. Test nastavi `open` (št. odprtih uskladitev) ali `down` (ne odgovarja)."""
    stub = {"open": 0, "down": False, "requests": []}

    def handle(request: httpx.Request) -> httpx.Response:
        stub["requests"].append(request)
        if stub["down"]:
            raise httpx.ConnectError("Sales is down")
        items = [{"saleId": str(uuid4())}] * stub["open"]
        return httpx.Response(200, json={"items": items, "total": len(items), "limit": 1})

    fake = SalesClient("http://sales", "product-token", transport=httpx.MockTransport(handle))
    client.app.dependency_overrides[get_sales_client] = lambda: fake
    yield stub
    client.app.dependency_overrides.pop(get_sales_client)


@pytest.fixture
def category_id(client):
    response = client.post("/v1/categories", json={"name": "Mleko"}, headers=OPERATOR)
    return response.json()["id"]


def product_body(category_id, **changes):
    body = {
        "name": "Domače mleko 1 l",
        "categoryId": category_id,
        "referencePrice": "1.8",
        "currency": "EUR",
        "weightGrams": 1000,
        "allergens": ["mleko"],
        "imageUrl": "https://example.com/mleko.jpg",
    }
    return body | changes


def create(client, category_id, **changes):
    return client.post("/v1/products", json=product_body(category_id, **changes), headers=OPERATOR)


# --- ustvarjanje -------------------------------------------------------------------------


def test_create_product(client, category_id):
    response = create(client, category_id)

    assert response.status_code == 201
    body = response.json()
    assert body["name"] == "Domače mleko 1 l"
    assert body["categoryName"] == "Mleko"
    assert body["referencePrice"] == "1.80"  # denar je vedno niz z dvema decimalkama
    assert body["status"] == "ACTIVE"
    assert "sku" not in body


def test_names_may_repeat(client, category_id):
    assert create(client, category_id).status_code == 201
    assert create(client, category_id).status_code == 201


def test_unknown_category_is_reference_not_found(client):
    response = create(client, str(uuid4()))

    assert response.status_code == 422
    assert response.json()["code"] == "REFERENCE_NOT_FOUND"


@pytest.mark.parametrize(
    "changes",
    [
        {"sku": "X1"},  # polja sku ni (B3)
        {"referencePrice": 1.8},  # denar mora biti niz
        {"referencePrice": "-1.00"},
        {"referencePrice": "1.234"},
        {"currency": "eur"},
        {"weightGrams": 0},
        {"imageUrl": "ni-url"},
        {"name": ""},
    ],
)
def test_invalid_product_is_validation_error(client, category_id, changes):
    response = create(client, category_id, **changes)

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_only_operator_may_create(client, category_id):
    response = client.post("/v1/products", json=product_body(category_id), headers=FRONTEND_API)

    assert response.status_code == 403


# --- branje ------------------------------------------------------------------------------


def test_list_is_sorted_filtered_and_only_active_by_default(client, category_id, sales):
    other = client.post("/v1/categories", json={"name": "Meso"}, headers=OPERATOR).json()["id"]
    create(client, category_id, name="Mleko")
    create(client, category_id, name="Jogurt")
    create(client, other, name="Salama")
    archived = create(client, category_id, name="Kefir").json()["id"]
    client.delete(f"/v1/products/{archived}", headers=OPERATOR)

    def names(query):
        response = client.get(f"/v1/products{query}", headers=OPERATOR).json()
        return [p["name"] for p in response["items"]]

    assert names("") == ["Jogurt", "Mleko", "Salama"]
    assert names(f"?categoryId={other}") == ["Salama"]
    assert names("?q=jog") == ["Jogurt"]
    assert names("?status=ARCHIVED") == ["Kefir"]


def test_list_is_paginated(client, category_id):
    for name in ["A", "B", "C"]:
        create(client, category_id, name=name)

    page = client.get("/v1/products?limit=2&offset=2", headers=SALES).json()

    assert [p["name"] for p in page["items"]] == ["C"]
    assert page["total"] == 3


def test_get_product_returns_archived_too(client, category_id, sales):
    product_id = create(client, category_id).json()["id"]
    client.delete(f"/v1/products/{product_id}", headers=OPERATOR)

    response = client.get(f"/v1/products/{product_id}", headers=MONITORING)

    assert response.status_code == 200
    assert response.json()["status"] == "ARCHIVED"


def test_get_unknown_product(client):
    response = client.get(f"/v1/products/{uuid4()}", headers=OPERATOR)

    assert response.status_code == 404
    assert response.json()["code"] == "PRODUCT_NOT_FOUND"


def test_monitoring_may_read_one_product_but_not_the_list(client):
    response = client.get("/v1/products", headers=MONITORING)

    assert response.status_code == 403


# --- zamenjava ---------------------------------------------------------------------------


def test_replace_is_idempotent(client, category_id):
    product_id = create(client, category_id).json()["id"]
    body = product_body(category_id, referencePrice="2.10", allergens=[])

    first = client.put(f"/v1/products/{product_id}", json=body, headers=OPERATOR)
    second = client.put(f"/v1/products/{product_id}", json=body, headers=OPERATOR)

    assert first.status_code == 200
    assert first.json() == second.json()
    assert second.json()["referencePrice"] == "2.10"


def test_replace_archived_product_is_rejected(client, category_id, sales):
    product_id = create(client, category_id).json()["id"]
    client.delete(f"/v1/products/{product_id}", headers=OPERATOR)

    response = client.put(
        f"/v1/products/{product_id}", json=product_body(category_id), headers=OPERATOR
    )

    assert response.status_code == 409
    assert response.json()["code"] == "PRODUCT_ARCHIVED"


def test_replace_with_unknown_category(client, category_id):
    product_id = create(client, category_id).json()["id"]

    response = client.put(
        f"/v1/products/{product_id}", json=product_body(str(uuid4())), headers=OPERATOR
    )

    assert response.status_code == 422
    assert response.json()["code"] == "REFERENCE_NOT_FOUND"


def test_replace_unknown_product(client, category_id):
    response = client.put(
        f"/v1/products/{uuid4()}", json=product_body(category_id), headers=OPERATOR
    )

    assert response.status_code == 404


# --- arhiviranje -------------------------------------------------------------------------


def test_archive_asks_sales_and_archives(client, category_id, sales):
    product_id = create(client, category_id).json()["id"]

    response = client.delete(f"/v1/products/{product_id}", headers=OPERATOR)

    assert response.status_code == 204
    assert client.get(f"/v1/products/{product_id}", headers=OPERATOR).json()["status"] == "ARCHIVED"
    request = sales["requests"][0]
    assert request.url.path == "/v1/sales"
    assert request.url.params["productId"] == product_id
    assert request.url.params["reconciliation"] == "NEEDS_RECONCILIATION"
    assert request.url.params["limit"] == "1"
    assert request.headers["X-Service-Token"] == "product-token"


def test_repeated_archive_does_not_ask_sales_again(client, category_id, sales):
    product_id = create(client, category_id).json()["id"]
    client.delete(f"/v1/products/{product_id}", headers=OPERATOR)
    sales["down"] = True

    response = client.delete(f"/v1/products/{product_id}", headers=OPERATOR)

    assert response.status_code == 204
    assert len(sales["requests"]) == 1


def test_archive_with_open_reconciliation_is_rejected(client, category_id, sales):
    product_id = create(client, category_id).json()["id"]
    sales["open"] = 1

    response = client.delete(f"/v1/products/{product_id}", headers=OPERATOR)

    assert response.status_code == 409
    assert response.json()["code"] == "PRODUCT_HAS_OPEN_RECONCILIATION"
    assert client.get(f"/v1/products/{product_id}", headers=OPERATOR).json()["status"] == "ACTIVE"


def test_archive_when_sales_is_down(client, category_id, sales):
    product_id = create(client, category_id).json()["id"]
    sales["down"] = True

    response = client.delete(f"/v1/products/{product_id}", headers=OPERATOR)

    assert response.status_code == 503
    assert response.json()["code"] == "DEPENDENCY_UNAVAILABLE"
    assert client.get(f"/v1/products/{product_id}", headers=OPERATOR).json()["status"] == "ACTIVE"


def test_archive_unknown_product(client, sales):
    response = client.delete(f"/v1/products/{uuid4()}", headers=OPERATOR)

    assert response.status_code == 404


# --- kategorija v uporabi ----------------------------------------------------------------


def test_category_used_by_archived_product_cannot_be_deleted(client, category_id, sales):
    product_id = create(client, category_id).json()["id"]
    client.delete(f"/v1/products/{product_id}", headers=OPERATOR)

    response = client.delete(f"/v1/categories/{category_id}", headers=OPERATOR)

    assert response.status_code == 409
    assert response.json()["code"] == "CATEGORY_IN_USE"
