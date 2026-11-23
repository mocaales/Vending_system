"""Poslovna pravila izdelkov (docs/03 §6, docs/01 §6.5)."""

from enum import StrEnum
from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.orm import Session, joinedload

from app.clients.sales import SalesClient
from app.core.errors import ApiError
from app.repository.models import Category, Product


class ProductStatus(StrEnum):
    ACTIVE = "ACTIVE"
    ARCHIVED = "ARCHIVED"


def list_products(
    session: Session,
    q: str | None,
    category_id: UUID | None,
    status: ProductStatus,
    limit: int,
    offset: int,
) -> tuple[list[Product], int]:
    """Izdelki s filtri, razvrščeni po nazivu."""
    filters = [Product.status == status]
    if q:
        filters.append(Product.name.icontains(q, autoescape=True))
    if category_id:
        filters.append(Product.category_id == category_id)

    total = session.scalar(select(func.count()).select_from(Product).where(*filters))
    items = session.scalars(
        select(Product)
        .options(joinedload(Product.category))
        .where(*filters)
        .order_by(Product.name, Product.id)
        .limit(limit)
        .offset(offset)
    ).all()
    return list(items), total


def get_product(session: Session, product_id: UUID) -> Product:
    """Vrne tudi arhiviran izdelek."""
    product = session.get(Product, product_id, options=[joinedload(Product.category)])
    if product is None:
        raise ApiError(404, "PRODUCT_NOT_FOUND", "Product not found.")
    return product


def create_product(session: Session, data: dict) -> Product:
    """`data` so polja izdelka (name, category_id, reference_price, ...)."""
    _check_category_exists(session, data["category_id"])
    product = Product(**data, status=ProductStatus.ACTIVE)
    session.add(product)
    session.commit()
    return get_product(session, product.id)


def replace_product(session: Session, product_id: UUID, data: dict) -> Product:
    """Idempotentna zamenjava; arhiviranega izdelka ni mogoče spreminjati."""
    product = get_product(session, product_id)
    if product.status == ProductStatus.ARCHIVED:
        raise ApiError(409, "PRODUCT_ARCHIVED", "Archived product cannot be changed.")
    _check_category_exists(session, data["category_id"])

    for field, value in data.items():
        setattr(product, field, value)
    session.commit()
    return get_product(session, product.id)


def archive_product(session: Session, product_id: UUID, sales: SalesClient) -> None:
    """Logično arhiviranje. Zavrne se, če ima prodaja izdelka odprto uskladitev.

    Izdelek na zalogi se sme arhivirati; zaloga v predalih se proda do praznjenja (B6).
    """
    product = get_product(session, product_id)
    if product.status == ProductStatus.ARCHIVED:
        return  # ponovitev: 204 brez ponovnega preverjanja

    if sales.has_open_reconciliation(product.id):
        raise ApiError(
            409,
            "PRODUCT_HAS_OPEN_RECONCILIATION",
            "A sale of this product still needs reconciliation.",
        )
    product.status = ProductStatus.ARCHIVED
    session.commit()


def _check_category_exists(session: Session, category_id: UUID) -> None:
    if session.get(Category, category_id) is None:
        raise ApiError(422, "REFERENCE_NOT_FOUND", "Category does not exist.")
