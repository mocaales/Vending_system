"""Poslovna pravila zaloge predalov (docs/03 §7, docs/01 §6.4)."""

from enum import StrEnum
from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.clients.machine import MachineClient
from app.clients.product import ProductClient
from app.core.errors import ApiError
from app.repository.models import Movement, Slot


class MovementType(StrEnum):
    SET = "SET"
    REFILL = "REFILL"
    REMOVAL = "REMOVAL"
    SALE = "SALE"


def list_machine_stock(
    session: Session, machine_id: UUID, limit: int, offset: int
) -> tuple[list[Slot], int]:
    """Predali avtomata, razvrščeni po oznaki predala."""
    return _page(session, Slot.machine_id == machine_id, limit, offset)


def list_product_stock(
    session: Session, product_id: UUID, limit: int, offset: int
) -> tuple[list[Slot], int]:
    """Predali (v vseh avtomatih), v katerih je izdelek."""
    return _page(session, Slot.product_id == product_id, limit, offset)


def check_machine_exists(machines: MachineClient, machine_id: UUID) -> None:
    """Za operaterja: neznan avtomat je 404 (za druge storitve se Machine ne kliče)."""
    if machines.get_machine(machine_id) is None:
        raise ApiError(404, "MACHINE_NOT_FOUND", "Machine not found.")


def get_slot(session: Session, machine_id: UUID, slot: str, lock: bool = False) -> Slot:
    """Predal, ki je že bil nastavljen; `lock` zaklene vrstico do konca transakcije."""
    stock = session.get(Slot, (machine_id, slot), with_for_update=lock, populate_existing=lock)
    if stock is None:
        raise ApiError(404, "SLOT_NOT_FOUND", "Slot not found.")
    return stock


def set_slot(
    session: Session,
    machine_id: UUID,
    slot: str,
    data: dict,
    machines: MachineClient,
    products: ProductClient,
) -> Slot:
    """Nastavi izdelek, serijo, količino in prag predala ter zapiše premik SET.

    `data` so polja predala (product_id, batch_code, expiry_date, quantity, low_stock_threshold).
    Idempotentno: isto telo ne spremeni ničesar in ne zapiše novega premika.
    """
    current = session.get(Slot, (machine_id, slot))
    if current is not None and _same(current, data):
        return current

    check_machine_slot(machines, machine_id, slot)
    check_product_active(products, data["product_id"])

    stock = lock_or_create(session, machine_id, slot)
    if stock.quantity > 0 and stock.product_id != data["product_id"]:
        raise ApiError(409, "SLOT_NOT_EMPTY", "Slot holds another product; empty it first.")

    for field, value in data.items():
        setattr(stock, field, value)
    add_movement(session, stock, MovementType.SET, stock.quantity)
    session.commit()
    return stock


def empty_slot(session: Session, machine_id: UUID, slot: str) -> None:
    """Izprazni predal in zapiše premik REMOVAL z odstranjeno količino; prag ostane."""
    stock = get_slot(session, machine_id, slot, lock=True)
    if stock.product_id is None:
        return  # že prazen: 204 brez premika

    if stock.quantity > 0:
        add_movement(session, stock, MovementType.REMOVAL, stock.quantity)
    stock.product_id = None
    stock.batch_code = None
    stock.expiry_date = None
    stock.quantity = 0
    session.commit()


def set_threshold(session: Session, machine_id: UUID, slot: str, threshold: int) -> Slot:
    """Idempotentna zamenjava praga nizke zaloge."""
    stock = get_slot(session, machine_id, slot, lock=True)
    stock.low_stock_threshold = threshold
    session.commit()
    return stock


def _page(session: Session, condition, limit: int, offset: int) -> tuple[list[Slot], int]:
    total = session.scalar(select(func.count()).select_from(Slot).where(condition))
    items = session.scalars(
        select(Slot)
        .where(condition)
        .order_by(Slot.machine_id, Slot.slot)
        .limit(limit)
        .offset(offset)
    ).all()
    return list(items), total


def _same(stock: Slot, data: dict) -> bool:
    return all(getattr(stock, field) == value for field, value in data.items())


def check_machine_slot(machines: MachineClient, machine_id: UUID, slot: str) -> None:
    """Avtomat in predal morata obstajati v Machine, avtomat ne sme biti umaknjen."""
    machine = machines.get_machine(machine_id)
    if machine is None:
        raise ApiError(404, "MACHINE_NOT_FOUND", "Machine not found.")
    if machine["status"] == "RETIRED":
        raise ApiError(409, "MACHINE_RETIRED", "Machine is retired.")
    if slot not in [s["slotCode"] for s in machine["slots"]]:
        raise ApiError(404, "SLOT_NOT_FOUND", "Machine has no such slot.")


def check_product_active(products: ProductClient, product_id: UUID) -> None:
    """Izdelek mora obstajati; arhiviranega ni mogoče vstaviti ali dopolniti (B6)."""
    product = products.get_product(product_id)
    if product is None:
        raise ApiError(422, "REFERENCE_NOT_FOUND", "Product does not exist.")
    if product["status"] == "ARCHIVED":
        raise ApiError(409, "PRODUCT_ARCHIVED", "Archived product cannot be stocked.")


def lock_or_create(session: Session, machine_id: UUID, slot: str) -> Slot:
    """Zaklene vrstico predala (in jo prej ustvari, če je ni), da sočasni klici čakajo."""
    session.execute(
        insert(Slot)
        .values(machine_id=machine_id, slot=slot, quantity=0, low_stock_threshold=0)
        .on_conflict_do_nothing()
    )
    return get_slot(session, machine_id, slot, lock=True)


def add_movement(
    session: Session,
    stock: Slot,
    type: MovementType,
    quantity: int,
    sale_id: UUID | None = None,
    refill_id: UUID | None = None,
) -> None:
    session.add(
        Movement(
            machine_id=stock.machine_id,
            slot=stock.slot,
            type=type,
            product_id=stock.product_id,
            batch_code=stock.batch_code,
            quantity=quantity,
            sale_id=sale_id,
            refill_id=refill_id,
        )
    )
