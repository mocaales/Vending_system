"""X-Trace-Id: prebere se ali ustvari, vrne v odgovoru in zapiše v dnevnik."""

import logging
from uuid import UUID


def test_trace_id_is_created_when_missing(client):
    response = client.get("/test/items")

    UUID(response.headers["X-Trace-Id"])  # ustvarjen nov UUID


def test_received_trace_id_is_returned(client):
    response = client.get("/test/items", headers={"X-Trace-Id": "trace-123"})

    assert response.headers["X-Trace-Id"] == "trace-123"


def test_invalid_trace_id_is_replaced(client):
    response = client.get("/test/items", headers={"X-Trace-Id": "bad id with spaces"})

    assert response.headers["X-Trace-Id"] != "bad id with spaces"
    UUID(response.headers["X-Trace-Id"])


def test_trace_id_is_in_error_body(client):
    response = client.get("/test/conflict", headers={"X-Trace-Id": "trace-456"})

    assert response.json()["traceId"] == "trace-456"


def test_trace_id_is_written_to_log(client, caplog):
    caplog.set_level(logging.INFO)

    client.get("/test/items", headers={"X-Trace-Id": "trace-789"})

    assert any(record.trace_id == "trace-789" for record in caplog.records)
