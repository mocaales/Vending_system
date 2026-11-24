"""Odpis potekle zaloge enega avtomata (docs/03 §7, pravilo B4)."""

from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.clients.machine import MachineClient
from app.domain.expiry import is_expired
from app.domain.stock import check_machine_exists, clear_slot
from app.repository.models import Slot


def remove_expired(session: Session, machine_id: UUID, machines: MachineClient) -> list[dict]:
    """Izprazni predale s poteklo zalogo; za vsakega zapiše en premik REMOVAL.

    Vrne odstranjene serije. Ponovitev vrne prazen seznam, ker je potekla zaloga že odstranjena.
    """
    check_machine_exists(machines, machine_id)

    # Predali z zalogo, zaklenjeni do konca transakcije (sočasna prodaja ali polnitev čaka).
    slots = session.scalars(
        select(Slot)
        .where(Slot.machine_id == machine_id, Slot.quantity > 0)
        .order_by(Slot.slot)
        .with_for_update()
    ).all()

    removed = []
    for stock in slots:
        if stock.expiry_date is not None and is_expired(stock.expiry_date):
            removed.append(
                {
                    "slot": stock.slot,
                    "product_id": stock.product_id,
                    "batch_code": stock.batch_code,
                    "quantity": stock.quantity,
                }
            )
            clear_slot(session, stock)
    session.commit()
    return removed
