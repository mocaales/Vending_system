"""Integracijski testi tečejo na pravi bazi PostgreSQL v zabojniku (Testcontainers)."""

import httpx
import pytest
from app.clients.machine import MachineClient, get_machine_client
from app.clients.product import ProductClient, get_product_client
from app.core.database import Base, get_engine
from fastapi.testclient import TestClient
from sqlalchemy import text
from testcontainers.community.postgres import PostgresContainer
from tests.integration.helpers import SERVICE_TOKENS, add_machine, add_product


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


@pytest.fixture
def services(client):
    """Lažna Machine in Product. Test doda avtomate in izdelke ali nastavi `down` (ne odgovarja)."""
    fake = {"machines": {}, "products": {}, "down": False, "requests": []}

    def handle(request: httpx.Request) -> httpx.Response:
        fake["requests"].append(request)
        if fake["down"]:
            raise httpx.ConnectError("service is down")
        kind, record_id = request.url.path.split("/")[2:4]  # /v1/machines/{id}
        record = fake[kind].get(record_id)
        if record is None:
            return httpx.Response(404, json={"code": "NOT_FOUND"})
        return httpx.Response(200, json=record)

    transport = httpx.MockTransport(handle)
    machines = MachineClient("Machine", "http://machine", "inventory-token", transport)
    products = ProductClient("Product", "http://product", "inventory-token", transport)
    client.app.dependency_overrides[get_machine_client] = lambda: machines
    client.app.dependency_overrides[get_product_client] = lambda: products
    yield fake
    client.app.dependency_overrides.clear()


@pytest.fixture
def machine_id(services):
    """Avtomat s predaloma A1 in A2, ki ga pozna lažni Machine."""
    return add_machine(services)


@pytest.fixture
def product_id(services):
    """Aktiven izdelek, ki ga pozna lažni Product."""
    return add_product(services)


@pytest.fixture(autouse=True)
def empty_tables(client):
    """Vsak test začne s prazno bazo."""
    yield
    tables = ", ".join(table.name for table in Base.metadata.sorted_tables)
    if tables:
        with get_engine().begin() as connection:
            connection.execute(text(f"TRUNCATE {tables} CASCADE"))
