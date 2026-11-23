"""Poslovna pravila kategorij (docs/03 §6, docs/01 §6.5)."""

from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import ApiError
from app.repository.models import Category


def list_categories(
    session: Session, q: str | None, limit: int, offset: int
) -> tuple[list[Category], int]:
    """Kategorije, razvrščene po imenu; `q` išče po imenu ne glede na velike in male črke."""
    query = select(Category)
    if q:
        query = query.where(Category.name.icontains(q, autoescape=True))

    total = session.scalar(select(func.count()).select_from(query.subquery()))
    items = session.scalars(
        query.order_by(Category.name, Category.id).limit(limit).offset(offset)
    ).all()
    return list(items), total


def get_category(session: Session, category_id: UUID) -> Category:
    category = session.get(Category, category_id)
    if category is None:
        raise ApiError(404, "CATEGORY_NOT_FOUND", "Category not found.")
    return category


def create_category(session: Session, name: str, description: str | None) -> Category:
    category = Category(name=name, description=description)
    session.add(category)
    _save(session)
    return category


def replace_category(
    session: Session, category_id: UUID, name: str, description: str | None
) -> Category:
    """Idempotentna zamenjava: isto telo da isti rezultat."""
    category = get_category(session, category_id)
    category.name = name
    category.description = description
    _save(session)
    return category


def delete_category(session: Session, category_id: UUID) -> None:
    """Trajen izbris; ponovitev vrne 404."""
    category = get_category(session, category_id)
    session.delete(category)
    session.commit()


def _save(session: Session) -> None:
    """Shrani spremembo; enolični indeks imena zavrne podvojeno ime (tudi ob sočasnih zahtevah)."""
    try:
        session.commit()
    except IntegrityError:
        session.rollback()
        raise ApiError(409, "CATEGORY_NAME_TAKEN", "Category name is already taken.") from None
