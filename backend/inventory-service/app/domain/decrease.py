"""Zmanjšanje zaloge po prodaji, enkrat na saleId (docs/03 §7, docs/01 §5.1)."""

from enum import StrEnum
from uuid import UUID

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import ApiError
from app.domain.stock import MovementType, add_movement, get_slot
from app.repository.models import Decrease


class DecreaseResult(StrEnum):
    APPLIED = "APPLIED"
    NEEDS_RECONCILIATION = "NEEDS_RECONCILIATION"


class DecreaseReason(StrEnum):
    PRODUCT_MISMATCH = "PRODUCT_MISMATCH"
    INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK"


def decrease_stock(
    session: Session,
    machine_id: UUID,
    slot: str,
    sale_id: UUID,
    product_id: UUID,
    quantity: int,
) -> tuple[Decrease, bool]:
    """Vrne (rezultat, ali je zahteva nova).

    Pri neskladju (drug izdelek, prazen predal ali premalo zaloge) se zaloga ne spremeni in
    nikoli ne postane negativna; rezultat je NEEDS_RECONCILIATION z razlogom.
    """
    # Polja, po katerih se ponovitev primerja s shranjeno zahtevo.
    request = dict(machine_id=machine_id, slot=slot, product_id=product_id, quantity=quantity)

    stored = session.get(Decrease, sale_id)
    if stored is not None:
        return _replay(stored, request), False

    stock = get_slot(session, machine_id, slot, lock=True)  # sočasni klici na predal čakajo
    reason = None
    if stock.product_id != product_id:
        reason = DecreaseReason.PRODUCT_MISMATCH
    elif stock.quantity < quantity:
        reason = DecreaseReason.INSUFFICIENT_STOCK
    else:
        stock.quantity -= quantity
        add_movement(session, stock, MovementType.SALE, quantity, sale_id=sale_id)

    record = Decrease(
        sale_id=sale_id,
        **request,
        result=DecreaseResult.NEEDS_RECONCILIATION if reason else DecreaseResult.APPLIED,
        reason=reason,
        quantity_after=stock.quantity,
    )
    session.add(record)
    try:
        session.commit()
    except IntegrityError:
        # Sočasen klic z istim saleId je bil hitrejši: naše spremembe se zavržejo.
        session.rollback()
        return _replay(session.get(Decrease, sale_id), request), False
    return record, True


def _replay(stored: Decrease, request: dict) -> Decrease:
    """Ponovitev: isto telo vrne shranjen rezultat, drugačno telo 409."""
    if any(getattr(stored, field) != value for field, value in request.items()):
        raise ApiError(409, "SALE_ID_CONFLICT", "saleId was already used with a different body.")
    return stored
