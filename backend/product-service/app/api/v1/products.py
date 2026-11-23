"""Poti /v1/products (docs/03 §6)."""

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.api.v1.schemas import ProductIn, ProductOut
from app.clients.sales import SalesClient, get_sales_client
from app.core.database import get_session
from app.core.errors import COMMON_ERRORS, error_docs
from app.core.schemas import Page, PageParams, page_params
from app.core.security import allowed_callers
from app.domain import products
from app.domain.products import ProductStatus

router = APIRouter(prefix="/v1/products", tags=["Products"], responses=error_docs(COMMON_ERRORS))

SessionDep = Annotated[Session, Depends(get_session)]

# Kdo sme klicati (frontend-api je modul gatewaya in uporablja gatewayev žeton).
operator_only = [Depends(allowed_callers(operator=True))]
readers = [Depends(allowed_callers("gateway", "inventory", "sales", operator=True))]
readers_by_id = [
    Depends(allowed_callers("gateway", "inventory", "sales", "monitoring", operator=True))
]

NOT_FOUND = {404: "`PRODUCT_NOT_FOUND`."}
CATEGORY_UNKNOWN = {422: "`VALIDATION_ERROR` or `REFERENCE_NOT_FOUND` (unknown category)."}
ARCHIVED = {409: "`PRODUCT_ARCHIVED`: an archived product cannot be changed."}
OPEN_RECONCILIATION = {409: "`PRODUCT_HAS_OPEN_RECONCILIATION`: a sale still needs reconciliation."}


@router.get(
    "",
    dependencies=readers,
    openapi_extra={"x-callers": ["OPERATOR", "frontend-api", "inventory", "sales"]},
)
def list_products(
    session: SessionDep,
    page: Annotated[PageParams, Depends(page_params)],
    q: Annotated[str | None, Query(max_length=100)] = None,
    category_id: Annotated[UUID | None, Query(alias="categoryId")] = None,
    status: ProductStatus = ProductStatus.ACTIVE,
) -> Page[ProductOut]:
    """Seznam izdelkov, razvrščen po nazivu; privzeto samo aktivni."""
    items, total = products.list_products(session, q, category_id, status, page.limit, page.offset)
    return Page(items=items, total=total, limit=page.limit, offset=page.offset)


@router.get(
    "/{product_id}",
    dependencies=readers_by_id,
    responses=error_docs(NOT_FOUND),
    openapi_extra={"x-callers": ["OPERATOR", "frontend-api", "inventory", "sales", "monitoring"]},
)
def get_product(session: SessionDep, product_id: UUID) -> ProductOut:
    """Vrne tudi arhiviran izdelek (naziv za pretekle prodaje in alarme)."""
    return products.get_product(session, product_id)


@router.post(
    "",
    status_code=201,
    dependencies=operator_only,
    responses=error_docs(CATEGORY_UNKNOWN),
    openapi_extra={"x-callers": ["OPERATOR"]},
)
def create_product(session: SessionDep, body: ProductIn) -> ProductOut:
    """Ni idempotentno; nazivi izdelkov se lahko ponavljajo."""
    return products.create_product(session, body.to_fields())


@router.put(
    "/{product_id}",
    dependencies=operator_only,
    responses=error_docs(NOT_FOUND | ARCHIVED | CATEGORY_UNKNOWN),
    openapi_extra={"x-callers": ["OPERATOR"]},
)
def replace_product(session: SessionDep, product_id: UUID, body: ProductIn) -> ProductOut:
    """Idempotentna zamenjava; nova cena ne spremeni že zabeleženih prodaj."""
    return products.replace_product(session, product_id, body.to_fields())


@router.delete(
    "/{product_id}",
    status_code=204,
    dependencies=operator_only,
    responses=error_docs(NOT_FOUND | OPEN_RECONCILIATION),
    openapi_extra={"x-callers": ["OPERATOR"]},
)
def archive_product(
    session: SessionDep,
    product_id: UUID,
    sales: Annotated[SalesClient, Depends(get_sales_client)],
) -> None:
    """Arhivacija (logični izbris). Pred tem Product vpraša Sales za odprte uskladitve."""
    products.archive_product(session, product_id, sales)
