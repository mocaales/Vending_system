"""Polnitev predala, enkrat na refillId (docs/03 §7, docs/01 §6.4)."""

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.clients.machine import MachineClient
from app.clients.product import ProductClient
from app.core.errors import ApiError
from app.domain.expiry import is_expired
from app.domain.low_stock import record_low_stock
from app.domain.stock import (
    MovementType,
    add_movement,
    check_machine_slot,
    check_product_active,
    get_slot,
    lock_or_create,
)
from app.repository.models import Refill, Slot

MAX_QUANTITY = 1000  # največ kosov v enem predalu


def refill_slot(
    session: Session, data: dict, machines: MachineClient, products: ProductClient
) -> tuple[Slot, bool]:
    """Doda kose v predal in zapiše premik REFILL. Vrne (predal, ali je polnitev nova).

    `data` so polja polnitve (refill_id, machine_id, slot, product_id, batch_code, expiry_date,
    quantity). Prazen predal dobi novo serijo; nepraznega je mogoče dopolniti samo z isto serijo.
    """
    stored = session.get(Refill, data["refill_id"])
    if stored is not None:
        return _replay(session, stored, data), False

    if is_expired(data["expiry_date"]):
        raise ApiError(422, "VALIDATION_ERROR", "Batch has already expired.")
    check_machine_slot(machines, data["machine_id"], data["slot"])
    check_product_active(products, data["product_id"])

    stock = lock_or_create(session, data["machine_id"], data["slot"])
    if stock.quantity > 0:
        batch = (stock.product_id, stock.batch_code, stock.expiry_date)
        if batch != (data["product_id"], data["batch_code"], data["expiry_date"]):
            raise ApiError(409, "SLOT_HAS_OTHER_BATCH", "Slot holds another batch.")
        if stock.quantity + data["quantity"] > MAX_QUANTITY:
            raise ApiError(422, "VALIDATION_ERROR", f"Slot holds at most {MAX_QUANTITY} items.")
        stock.quantity += data["quantity"]
    else:
        stock.product_id = data["product_id"]
        stock.batch_code = data["batch_code"]
        stock.expiry_date = data["expiry_date"]
        stock.quantity = data["quantity"]
    add_movement(session, stock, MovementType.REFILL, data["quantity"], refill_id=data["refill_id"])
    record_low_stock(session, data["machine_id"])

    session.add(Refill(**data))
    try:
        session.commit()
    except IntegrityError:
        # Sočasen klic z istim refillId je bil hitrejši: naše spremembe se zavržejo.
        session.rollback()
        return _replay(session, session.get(Refill, data["refill_id"]), data), False
    return stock, True


def _replay(session: Session, stored: Refill, data: dict) -> Slot:
    """Ponovitev: isto telo vrne predal brez podvojitve količine, drugačno telo 409."""
    if any(getattr(stored, field) != value for field, value in data.items()):
        raise ApiError(
            409, "REFILL_ID_CONFLICT", "refillId was already used with a different body."
        )
    return get_slot(session, stored.machine_id, stored.slot)
