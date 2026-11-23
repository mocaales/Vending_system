"""Preverjanje klicatelja z X-Service-Token (401 UNAUTHENTICATED, 403 CALLER_NOT_ALLOWED)."""

OPERATOR_ID = "3f2b8c1e-7d4a-4c9e-9b1f-0a2d3e4f5a6b"


def test_missing_token_is_unauthenticated(client):
    response = client.get("/test/sales-only")

    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHENTICATED"


def test_unknown_token_is_unauthenticated(client):
    response = client.get("/test/sales-only", headers={"X-Service-Token": "wrong"})

    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHENTICATED"


def test_allowed_caller_passes(client):
    response = client.get("/test/sales-only", headers={"X-Service-Token": "sales-token"})

    assert response.status_code == 200
    assert response.json()["caller"] == "sales"


def test_other_caller_is_not_allowed(client):
    response = client.get("/test/sales-only", headers={"X-Service-Token": "inventory-token"})

    assert response.status_code == 403
    assert response.json()["code"] == "CALLER_NOT_ALLOWED"


def test_operator_through_gateway_passes(client):
    headers = {"X-Service-Token": "gateway-token", "X-Operator-Id": OPERATOR_ID}

    response = client.get("/test/operator-or-sales", headers=headers)

    assert response.status_code == 200
    assert response.json() == {"caller": "gateway", "operatorId": OPERATOR_ID}


def test_operator_is_not_allowed_where_route_is_not_for_operators(client):
    headers = {"X-Service-Token": "gateway-token", "X-Operator-Id": OPERATOR_ID}

    response = client.get("/test/sales-only", headers=headers)

    assert response.status_code == 403
    assert response.json()["code"] == "CALLER_NOT_ALLOWED"


def test_operator_id_without_gateway_token_is_unauthenticated(client):
    response = client.get("/test/operator-or-sales", headers={"X-Operator-Id": OPERATOR_ID})

    assert response.status_code == 401


def test_invalid_operator_id_is_unauthenticated(client):
    headers = {"X-Service-Token": "gateway-token", "X-Operator-Id": "not-a-uuid"}

    response = client.get("/test/operator-or-sales", headers=headers)

    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHENTICATED"


def test_operator_id_from_another_service_does_not_make_an_operator(client):
    headers = {"X-Service-Token": "inventory-token", "X-Operator-Id": OPERATOR_ID}

    response = client.get("/test/operator-or-sales", headers=headers)

    assert response.status_code == 403
