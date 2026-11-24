"""Poti /v1/inventory (docs/03 §7).

Statične poti (`product/...`, `refill`, `movements`) so registrirane pred potmi z
`{machine_id}`, sicer bi se npr. "movements" bral kot oznaka avtomata.
"""

from datetime import datetime
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Path, Query, Response
from sqlalchemy.orm import Session

from app.api.v1.schemas import (
    DecreaseIn,
    DecreaseOut,
    MovementOut,
    RefillIn,
    SlotOut,
    SlotSet,
    ThresholdIn,
)
from app.clients.machine import MachineClient, get_machine_client
from app.clients.product import ProductClient, get_product_client
from app.core.database import get_session
from app.core.errors import COMMON_ERRORS, error_docs
from app.core.schemas import Page, PageParams, page_params
from app.core.security import Caller, allowed_callers
from app.domain import decrease, movements, refill, stock
from app.domain.stock import MovementType

router = APIRouter(prefix="/v1/inventory", tags=["Stock"], responses=error_docs(COMMON_ERRORS))

SessionDep = Annotated[Session, Depends(get_session)]
PageDep = Annotated[PageParams, Depends(page_params)]
SlotCode = Annotated[str, Path(min_length=1, max_length=8)]
Machines = Annotated[MachineClient, Depends(get_machine_client)]
Products = Annotated[ProductClient, Depends(get_product_client)]

# Kdo sme klicati (frontend-api je modul gatewaya in uporablja gatewayev žeton).
operator_only = [Depends(allowed_callers(operator=True))]
operator_or_frontend = [Depends(allowed_callers("gateway", operator=True))]
slot_readers = [Depends(allowed_callers("gateway", "sales", operator=True))]
machine_readers = allowed_callers("gateway", "machine", operator=True)

MACHINE_NOT_FOUND = {404: "`MACHINE_NOT_FOUND`."}
SLOT_NOT_FOUND = {404: "`SLOT_NOT_FOUND`."}
SALE_ID_CONFLICT = {409: "`SALE_ID_CONFLICT`: the same `saleId` with a different body."}
REFILL_ERRORS = {
    404: "`MACHINE_NOT_FOUND` or `SLOT_NOT_FOUND` (the machine has no such slot).",
    409: "`REFILL_ID_CONFLICT`, `SLOT_HAS_OTHER_BATCH`, `PRODUCT_ARCHIVED` or `MACHINE_RETIRED`.",
    422: "`VALIDATION_ERROR` (also an expired batch or more than 1000 items) or "
    "`REFERENCE_NOT_FOUND` (unknown product).",
}
SET_ERRORS = {
    404: "`MACHINE_NOT_FOUND` or `SLOT_NOT_FOUND` (the machine has no such slot).",
    409: "`SLOT_NOT_EMPTY`, `PRODUCT_ARCHIVED` or `MACHINE_RETIRED`.",
    422: "`VALIDATION_ERROR` or `REFERENCE_NOT_FOUND` (unknown product).",
}


# --- statične poti (pred {machine_id}) ---------------------------------------------------


@router.get(
    "/product/{product_id}",
    dependencies=operator_or_frontend,
    openapi_extra={"x-callers": ["OPERATOR", "frontend-api"]},
)
def list_product_stock(session: SessionDep, page: PageDep, product_id: UUID) -> Page[SlotOut]:
    """Predali z izdelkom v vseh avtomatih (UI jih prikaže pred arhiviranjem izdelka)."""
    items, total = stock.list_product_stock(session, product_id, page.limit, page.offset)
    return Page(items=items, total=total, limit=page.limit, offset=page.offset)


@router.post(
    "/refill",
    status_code=201,
    dependencies=operator_only,
    responses={
        200: {"model": SlotOut, "description": "Repeated refill; the quantity is not added again."},
        **error_docs(REFILL_ERRORS),
    },
    openapi_extra={"x-callers": ["OPERATOR"]},
)
def refill_slot(
    session: SessionDep, body: RefillIn, response: Response, machines: Machines, products: Products
) -> SlotOut:
    """Polnitev predala (premik REFILL); idempotentno po refillId. Vrne predal po polnitvi."""
    result, created = refill.refill_slot(session, body.model_dump(), machines, products)
    response.status_code = 201 if created else 200
    return result


@router.get(
    "/movements",
    dependencies=operator_only,
    openapi_extra={"x-callers": ["OPERATOR"]},
)
def list_movements(
    session: SessionDep,
    page: PageDep,
    machine_id: Annotated[UUID | None, Query(alias="machineId")] = None,
    product_id: Annotated[UUID | None, Query(alias="productId")] = None,
    type: MovementType | None = None,
    since: Annotated[datetime | None, Query(alias="from")] = None,
    until: Annotated[datetime | None, Query(alias="to")] = None,
) -> Page[MovementOut]:
    """Premiki zaloge, najnovejši najprej; `from` je vključen, `to` ne."""
    items, total = movements.list_movements(
        session, machine_id, product_id, type, since, until, page.limit, page.offset
    )
    return Page(items=items, total=total, limit=page.limit, offset=page.offset)


# --- poti z {machine_id} -----------------------------------------------------------------


@router.get(
    "/{machine_id}",
    responses=error_docs(MACHINE_NOT_FOUND),
    openapi_extra={"x-callers": ["OPERATOR", "frontend-api", "machine"]},
)
def list_machine_stock(
    session: SessionDep,
    page: PageDep,
    machine_id: UUID,
    caller: Annotated[Caller, Depends(machine_readers)],
    machines: Machines,
) -> Page[SlotOut]:
    """Zaloga avtomata po predalih.

    Druge storitve dobijo odgovor brez klica v Machine (neznan avtomat: prazen seznam);
    za operaterja Inventory prej preveri avtomat (neznan: 404).
    """
    if caller.operator_id is not None:
        stock.check_machine_exists(machines, machine_id)
    items, total = stock.list_machine_stock(session, machine_id, page.limit, page.offset)
    return Page(items=items, total=total, limit=page.limit, offset=page.offset)


@router.get(
    "/{machine_id}/{slot}",
    dependencies=slot_readers,
    responses=error_docs(SLOT_NOT_FOUND),
    openapi_extra={"x-callers": ["OPERATOR", "frontend-api", "sales"]},
)
def get_slot(session: SessionDep, machine_id: UUID, slot: SlotCode) -> SlotOut:
    """Izpraznjen predal se vrne brez izdelka; nikoli nastavljen predal: 404."""
    return stock.get_slot(session, machine_id, slot)


@router.put(
    "/{machine_id}/{slot}",
    dependencies=operator_only,
    responses=error_docs(SET_ERRORS),
    openapi_extra={"x-callers": ["OPERATOR"]},
)
def set_slot(
    session: SessionDep,
    machine_id: UUID,
    slot: SlotCode,
    body: SlotSet,
    machines: Machines,
    products: Products,
) -> SlotOut:
    """Nastavi predal (premik SET); idempotentno. Prej preveri avtomat v Machine in izdelek."""
    return stock.set_slot(session, machine_id, slot, body.model_dump(), machines, products)


@router.put(
    "/{machine_id}/{slot}/threshold",
    dependencies=operator_only,
    responses=error_docs(SLOT_NOT_FOUND),
    openapi_extra={"x-callers": ["OPERATOR"]},
)
def set_threshold(
    session: SessionDep, machine_id: UUID, slot: SlotCode, body: ThresholdIn
) -> SlotOut:
    """Idempotentna zamenjava praga nizke zaloge; vrne predal."""
    return stock.set_threshold(session, machine_id, slot, body.low_stock_threshold)


@router.delete(
    "/{machine_id}/{slot}",
    status_code=204,
    dependencies=operator_only,
    responses=error_docs(SLOT_NOT_FOUND),
    openapi_extra={"x-callers": ["OPERATOR"]},
)
def empty_slot(session: SessionDep, machine_id: UUID, slot: SlotCode) -> None:
    """Izprazni predal (premik REMOVAL); že prazen predal: 204 brez premika."""
    stock.empty_slot(session, machine_id, slot)


@router.post(
    "/{machine_id}/{slot}/decrease",
    status_code=201,
    dependencies=[Depends(allowed_callers("sales"))],
    response_model_exclude_none=True,
    responses={
        200: {"model": DecreaseOut, "description": "Repeated request; the stored result."},
        **error_docs(SLOT_NOT_FOUND | SALE_ID_CONFLICT),
    },
    openapi_extra={"x-callers": ["sales"]},
)
def decrease_stock(
    session: SessionDep, machine_id: UUID, slot: SlotCode, body: DecreaseIn, response: Response
) -> DecreaseOut:
    """Samo interno: delavec outboxa Sales enkrat na saleId.

    Neskladje vrne 201 z NEEDS_RECONCILIATION; zaloga se takrat ne spremeni.
    Ponovitev z istim telesom vrne 200 s shranjenim rezultatom.
    """
    result, created = decrease.decrease_stock(
        session, machine_id, slot, body.sale_id, body.product_id, body.quantity
    )
    response.status_code = 201 if created else 200
    return result
