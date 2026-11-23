"""Integracijski testi tečejo na pravi bazi PostgreSQL v zabojniku (Testcontainers)."""

import pytest
from fastapi.testclient import TestClient
from testcontainers.community.postgres import PostgresContainer


@pytest.fixture(scope="session")
def database_url():
    with PostgresContainer("postgres:17-alpine", driver="psycopg") as postgres:
        yield postgres.get_connection_url()


@pytest.fixture(scope="session")
def client(database_url):
    """Prava aplikacija; ob zagonu (with) se izvedejo migracije."""
    mp = pytest.MonkeyPatch()
    mp.setenv("DATABASE_URL", database_url)
    from app.main import app

    with TestClient(app) as test_client:
        yield test_client
    mp.undo()
