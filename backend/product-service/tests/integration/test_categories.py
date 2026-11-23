"""Kategorije /v1/categories (issue #53)."""

from uuid import uuid4

from tests.integration.helpers import FRONTEND_API, OPERATOR, SALES


def create(client, name, description=None):
    body = {"name": name}
    if description is not None:
        body["description"] = description
    return client.post("/v1/categories", json=body, headers=OPERATOR)


def test_create_category(client):
    response = create(client, "Mleko", "Mlečni izdelki")

    assert response.status_code == 201
    body = response.json()
    assert body["name"] == "Mleko"
    assert body["description"] == "Mlečni izdelki"
    assert body["id"]


def test_create_is_not_idempotent_and_name_is_unique_ignoring_case(client):
    create(client, "Mleko")

    response = create(client, "MLEKO")

    assert response.status_code == 409
    assert response.json()["code"] == "CATEGORY_NAME_TAKEN"


def test_name_length_is_validated(client):
    assert create(client, "").status_code == 422
    assert create(client, "x" * 61).status_code == 422
    assert create(client, "Meso", "x" * 201).status_code == 422


def test_unknown_field_is_rejected(client):
    response = client.post("/v1/categories", json={"name": "Jajca", "x": 1}, headers=OPERATOR)

    assert response.status_code == 422


def test_list_is_sorted_by_name_and_filtered(client):
    for name in ["Mleko", "Jajca", "Meso"]:
        create(client, name)

    all_names = [c["name"] for c in client.get("/v1/categories", headers=OPERATOR).json()["items"]]
    filtered = client.get("/v1/categories?q=me", headers=OPERATOR).json()

    assert all_names == ["Jajca", "Meso", "Mleko"]
    assert [c["name"] for c in filtered["items"]] == ["Meso"]
    assert filtered["total"] == 1


def test_list_is_paginated(client):
    for name in ["A", "B", "C"]:
        create(client, name)

    page = client.get("/v1/categories?limit=2&offset=1", headers=OPERATOR).json()

    assert [c["name"] for c in page["items"]] == ["B", "C"]
    assert page["total"] == 3
    assert page["limit"] == 2
    assert page["offset"] == 1


def test_get_category(client):
    category_id = create(client, "Meso").json()["id"]

    response = client.get(f"/v1/categories/{category_id}", headers=FRONTEND_API)

    assert response.status_code == 200
    assert response.json()["name"] == "Meso"


def test_get_unknown_category(client):
    response = client.get(f"/v1/categories/{uuid4()}", headers=OPERATOR)

    assert response.status_code == 404
    assert response.json()["code"] == "CATEGORY_NOT_FOUND"


def test_replace_category_is_idempotent(client):
    category_id = create(client, "Meso", "staro").json()["id"]
    body = {"name": "Meso in salame"}

    first = client.put(f"/v1/categories/{category_id}", json=body, headers=OPERATOR)
    second = client.put(f"/v1/categories/{category_id}", json=body, headers=OPERATOR)

    assert first.status_code == 200
    assert first.json() == second.json()
    assert second.json()["description"] is None  # zamenjava: izpuščen opis se pobriše


def test_replace_with_name_of_another_category(client):
    create(client, "Mleko")
    category_id = create(client, "Meso").json()["id"]

    response = client.put(f"/v1/categories/{category_id}", json={"name": "mleko"}, headers=OPERATOR)

    assert response.status_code == 409
    assert response.json()["code"] == "CATEGORY_NAME_TAKEN"


def test_replace_unknown_category(client):
    response = client.put(f"/v1/categories/{uuid4()}", json={"name": "X"}, headers=OPERATOR)

    assert response.status_code == 404


def test_delete_is_permanent_and_repeat_is_not_found(client):
    category_id = create(client, "Jajca").json()["id"]

    first = client.delete(f"/v1/categories/{category_id}", headers=OPERATOR)
    second = client.delete(f"/v1/categories/{category_id}", headers=OPERATOR)

    assert first.status_code == 204
    assert second.status_code == 404
    assert second.json()["code"] == "CATEGORY_NOT_FOUND"


def test_only_operator_may_change_categories(client):
    response = client.post("/v1/categories", json={"name": "Meso"}, headers=FRONTEND_API)

    assert response.status_code == 403
    assert response.json()["code"] == "CALLER_NOT_ALLOWED"


def test_other_services_may_not_read_categories(client):
    response = client.get("/v1/categories", headers=SALES)

    assert response.status_code == 403
