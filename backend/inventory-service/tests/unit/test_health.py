"""Zdravje brez baze: live vedno 200, ready 503, ko baza ni dosegljiva."""

from app.core.database import get_engine
from sqlalchemy import create_engine


def test_live_is_up(client):
    response = client.get("/health/live")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_ready_is_unavailable_without_database(client):
    unreachable = create_engine(
        "postgresql+psycopg://user:pass@127.0.0.1:1/none", connect_args={"connect_timeout": 1}
    )
    client.app.dependency_overrides[get_engine] = lambda: unreachable

    response = client.get("/health/ready")

    assert response.status_code == 503
    assert response.json()["code"] == "DEPENDENCY_UNAVAILABLE"
