"""Pregled premikov zaloge (docs/03 §7)."""

from datetime import datetime
from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.domain.stock import MovementType
from app.repository.models import Movement


def list_movements(
    session: Session,
    machine_id: UUID | None,
    product_id: UUID | None,
    type: MovementType | None,
    since: datetime | None,
    until: datetime | None,
    limit: int,
    offset: int,
) -> tuple[list[Movement], int]:
    """Premiki s filtri, najnovejši najprej; `since` je vključen, `until` ne."""
    filters = []
    if machine_id:
        filters.append(Movement.machine_id == machine_id)
    if product_id:
        filters.append(Movement.product_id == product_id)
    if type:
        filters.append(Movement.type == type)
    if since:
        filters.append(Movement.occurred_at >= since)
    if until:
        filters.append(Movement.occurred_at < until)

    total = session.scalar(select(func.count()).select_from(Movement).where(*filters))
    items = session.scalars(
        select(Movement)
        .where(*filters)
        .order_by(Movement.occurred_at.desc(), Movement.id)
        .limit(limit)
        .offset(offset)
    ).all()
    return list(items), total
