"""Pomoč za teste: glave klicateljev, lažni avtomati in izdelki ter branje premikov."""

from datetime import timedelta
from uuid import uuid4

from app.core.database import get_engine
from app.domain.expiry import local_today
from app.repository.models import Movement
from sqlalchemy import select
from sqlalchemy.orm import Session

OPERATOR_ID = "3f2b8c1e-7d4a-4c9e-9b1f-0a2d3e4f5a6b"
OPERATOR = {"X-Service-Token": "gateway-token", "X-Operator-Id": OPERATOR_ID}
FRONTEND_API = {"X-Service-Token": "gateway-token"}
MACHINE = {"X-Service-Token": "machine-token"}
SALES = {"X-Service-Token": "sales-token"}
MONITORING = {"X-Service-Token": "monitoring-token"}

# Žetoni, ki jih testna aplikacija pozna (spremenljivka okolja -> vrednost).
SERVICE_TOKENS = {
    "GATEWAY_SERVICE_TOKEN": "gateway-token",
    "MACHINE_SERVICE_TOKEN": "machine-token",
    "SALES_SERVICE_TOKEN": "sales-token",
    "MONITORING_SERVICE_TOKEN": "monitoring-token",
}


def add_machine(services, slots=("A1", "A2"), status="ACTIVE"):
    machine_id = str(uuid4())
    services["machines"][machine_id] = {
        "id": machine_id,
        "status": status,
        "slots": [{"slotCode": code} for code in slots],
    }
    return machine_id


def add_product(services, status="ACTIVE"):
    product_id = str(uuid4())
    services["products"][product_id] = {"id": product_id, "status": status}
    return product_id


def slot_body(product_id, **changes):
    body = {
        "productId": product_id,
        "batchCode": "L2026-11-17",
        "expiryDate": "2026-12-01",
        "quantity": 8,
        "lowStockThreshold": 2,
    }
    return body | changes


def put_slot(client, machine_id, slot, body):
    return client.put(f"/v1/inventory/{machine_id}/{slot}", json=body, headers=OPERATOR)


def movements(machine_id):
    with Session(get_engine()) as session:
        rows = session.scalars(
            select(Movement).where(Movement.machine_id == machine_id).order_by(Movement.occurred_at)
        ).all()
        return [(m.type, m.quantity) for m in rows]


def refill_body(machine_id, product_id, **changes):
    """Polnitev predala A1 z 5 kosi serije, ki poteče čez 30 dni."""
    body = {
        "refillId": str(uuid4()),
        "machineId": machine_id,
        "slot": "A1",
        "productId": product_id,
        "batchCode": "R2026-11",
        "expiryDate": str(local_today() + timedelta(days=30)),
        "quantity": 5,
    }
    return body | changes


def refill(client, body):
    return client.post("/v1/inventory/refill", json=body, headers=OPERATOR)
