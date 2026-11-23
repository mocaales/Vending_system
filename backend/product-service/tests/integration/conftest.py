"""Integracijski testi tečejo na pravi bazi PostgreSQL v zabojniku (Testcontainers)."""

import pytest
from app.core.database import Base, get_engine
from fastapi.testclient import TestClient
from sqlalchemy import text
from testcontainers.community.postgres import PostgresContainer
from tests.integration.helpers import SERVICE_TOKENS


@pytest.fixture(scope="session")
def database_url():
    with PostgresContainer("postgres:17-alpine", driver="psycopg") as postgres:
        yield postgres.get_connection_url()


@pytest.fixture(scope="session")
def client(database_url):
    """Prava aplikacija; ob zagonu (with) se izvedejo migracije."""
    mp = pytest.MonkeyPatch()
    mp.setenv("DATABASE_URL", database_url)
    for variable, token in SERVICE_TOKENS.items():
        mp.setenv(variable, token)
    from app.main import app

    with TestClient(app) as test_client:
        yield test_client
    mp.undo()


@pytest.fixture(autouse=True)
def empty_tables(client):
    """Vsak test začne s prazno bazo."""
    yield
    tables = ", ".join(table.name for table in Base.metadata.sorted_tables)
    if tables:
        with get_engine().begin() as connection:
            connection.execute(text(f"TRUNCATE {tables} CASCADE"))
