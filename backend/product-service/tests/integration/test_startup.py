"""Zagon prave aplikacije na prazni bazi: migracije, zdravje in OpenAPI."""

from app.core.database import get_engine
from sqlalchemy import text


def test_ready_with_database(client):
    response = client.get("/health/ready")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_migrations_ran_on_startup(client):
    with get_engine().connect() as connection:
        version = connection.execute(text("SELECT version_num FROM alembic_version")).scalar()

    assert version == "0001"


def test_openapi_is_generated_without_health_paths(client):
    response = client.get("/openapi.json")

    assert response.status_code == 200
    paths = response.json()["paths"]
    assert "/health/live" not in paths
    assert "/health/ready" not in paths
