"""Dogodka avtomata po pogodbi: SaleEvent (docs/03 §3.1) in MeasurementIn (docs/03 §3.2)."""

from datetime import datetime
from uuid import uuid4

PAYMENT_METHODS = ["CASH", "CARD"]


def timestamp(now: datetime) -> str:
    """Čas v UTC z `Z`, npr. 2026-11-17T08:30:00Z."""
    return now.strftime("%Y-%m-%dT%H:%M:%SZ")


def sale_event(machine_id: str, slot: dict, payment_method: str, now: datetime) -> dict:
    """Prodaja enega kosa iz predala po ceni iz machines.local.json; saleId je nov UUID."""
    return {
        "saleId": str(uuid4()),
        "machineId": machine_id,
        "slotId": slot["slotId"],
        "productId": slot["productId"],
        "quantity": 1,
        "unitPricePaid": slot["unitPrice"],
        "currency": slot["currency"],
        "paymentMethod": payment_method,
        "occurredAt": timestamp(now),
    }


def measurement_event(machine_id: str, temperature: float, door: str, now: datetime) -> dict:
    """Meritev temperature (niz z eno decimalko) in stanja vrat; measurementId je nov UUID."""
    return {
        "measurementId": str(uuid4()),
        "machineId": machine_id,
        "observedAt": timestamp(now),
        "temperatureC": f"{temperature:.1f}",
        "doorState": door,
    }
