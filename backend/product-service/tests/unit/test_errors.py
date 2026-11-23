"""Enotno telo napake {code, message, traceId} (docs/03 §2)."""


def test_api_error_has_uniform_body(client):
    response = client.get("/test/conflict")

    assert response.status_code == 409
    body = response.json()
    assert body["code"] == "SOMETHING_TAKEN"
    assert body["message"] == "Something is taken."
    assert body["traceId"] == response.headers["X-Trace-Id"]
    assert "details" not in body


def test_unknown_field_is_validation_error_with_details(client):
    response = client.post("/test/items", json={"name": "Mleko", "sku": "X1"})

    assert response.status_code == 422
    body = response.json()
    assert body["code"] == "VALIDATION_ERROR"
    assert body["details"][0]["field"] == "sku"


def test_missing_field_is_validation_error(client):
    response = client.post("/test/items", json={})

    assert response.status_code == 422
    assert response.json()["details"][0]["field"] == "name"


def test_malformed_json_is_bad_request(client):
    response = client.post(
        "/test/items", content="{not json", headers={"Content-Type": "application/json"}
    )

    assert response.status_code == 400
    assert response.json()["code"] == "BAD_REQUEST"


def test_invalid_uuid_in_path_is_bad_request(client):
    response = client.get("/test/items/not-a-uuid")

    assert response.status_code == 400
    assert response.json()["code"] == "BAD_REQUEST"


def test_unknown_path_is_not_found(client):
    response = client.get("/does-not-exist")

    assert response.status_code == 404
    assert response.json()["code"] == "NOT_FOUND"


def test_unexpected_error_is_internal_error(client):
    response = client.get("/test/crash")

    assert response.status_code == 500
    body = response.json()
    assert body["code"] == "INTERNAL_ERROR"
    assert body["traceId"] == response.headers["X-Trace-Id"]
