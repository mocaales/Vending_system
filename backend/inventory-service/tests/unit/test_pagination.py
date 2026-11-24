"""Seznam {items, total, limit, offset}: limit privzeto 50, največ 200."""


def test_default_page(client):
    response = client.get("/test/items")

    assert response.status_code == 200
    assert response.json() == {"items": [], "total": 0, "limit": 50, "offset": 0}


def test_custom_page(client):
    response = client.get("/test/items?limit=200&offset=10")

    assert response.json()["limit"] == 200
    assert response.json()["offset"] == 10


def test_limit_above_maximum_is_validation_error(client):
    response = client.get("/test/items?limit=201")

    assert response.status_code == 422
    assert response.json()["details"][0]["field"] == "limit"


def test_negative_offset_is_validation_error(client):
    response = client.get("/test/items?offset=-1")

    assert response.status_code == 422


def test_non_number_limit_is_bad_request(client):
    response = client.get("/test/items?limit=abc")

    assert response.status_code == 400
