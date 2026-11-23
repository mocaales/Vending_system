"""Poti /v1/categories (docs/03 §6)."""

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.api.v1.schemas import CategoryIn, CategoryOut
from app.core.database import get_session
from app.core.errors import COMMON_ERRORS, error_docs
from app.core.schemas import Page, PageParams, page_params
from app.core.security import allowed_callers
from app.domain import categories

router = APIRouter(
    prefix="/v1/categories", tags=["Categories"], responses=error_docs(COMMON_ERRORS)
)

SessionDep = Annotated[Session, Depends(get_session)]

# Kdo sme klicati: operater (prek gatewaya) ali frontend-api (modul gatewaya, gatewayev žeton).
operator_only = [Depends(allowed_callers(operator=True))]
operator_or_frontend = [Depends(allowed_callers("gateway", operator=True))]

NOT_FOUND = {404: "`CATEGORY_NOT_FOUND`."}
NAME_TAKEN = {409: "`CATEGORY_NAME_TAKEN`: another category already has this name."}


@router.get(
    "",
    dependencies=operator_or_frontend,
    openapi_extra={"x-callers": ["OPERATOR", "frontend-api"]},
)
def list_categories(
    session: SessionDep,
    page: Annotated[PageParams, Depends(page_params)],
    q: Annotated[str | None, Query(max_length=100)] = None,
) -> Page[CategoryOut]:
    """Seznam kategorij, razvrščen po imenu."""
    items, total = categories.list_categories(session, q, page.limit, page.offset)
    return Page(items=items, total=total, limit=page.limit, offset=page.offset)


@router.get(
    "/{category_id}",
    dependencies=operator_or_frontend,
    responses=error_docs(NOT_FOUND),
    openapi_extra={"x-callers": ["OPERATOR", "frontend-api"]},
)
def get_category(session: SessionDep, category_id: UUID) -> CategoryOut:
    return categories.get_category(session, category_id)


@router.post(
    "",
    status_code=201,
    dependencies=operator_only,
    responses=error_docs(NAME_TAKEN),
    openapi_extra={"x-callers": ["OPERATOR"]},
)
def create_category(session: SessionDep, body: CategoryIn) -> CategoryOut:
    """Ni idempotentno: vsak klic ustvari novo kategorijo."""
    return categories.create_category(session, body.name, body.description)


@router.put(
    "/{category_id}",
    dependencies=operator_only,
    responses=error_docs(NOT_FOUND | NAME_TAKEN),
    openapi_extra={"x-callers": ["OPERATOR"]},
)
def replace_category(session: SessionDep, category_id: UUID, body: CategoryIn) -> CategoryOut:
    """Idempotentna zamenjava imena in opisa."""
    return categories.replace_category(session, category_id, body.name, body.description)


@router.delete(
    "/{category_id}",
    status_code=204,
    dependencies=operator_only,
    responses=error_docs(NOT_FOUND),
    openapi_extra={"x-callers": ["OPERATOR"]},
)
def delete_category(session: SessionDep, category_id: UUID) -> None:
    """Trajen izbris; ponovitev vrne 404."""
    categories.delete_category(session, category_id)
