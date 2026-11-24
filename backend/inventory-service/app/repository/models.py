"""Tabele baze Inventory (ustvarijo jih migracije v alembic/versions)."""

from datetime import date, datetime
from uuid import UUID, uuid4

from sqlalchemy import DateTime, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class Slot(Base):
    """Zaloga enega predala (machineId, slot). Prazen predal nima izdelka in ima količino 0."""

    __tablename__ = "slots"

    machine_id: Mapped[UUID] = mapped_column(primary_key=True)
    slot: Mapped[str] = mapped_column(String(8), primary_key=True)  # slotCode iz Machine
    product_id: Mapped[UUID | None]
    batch_code: Mapped[str | None] = mapped_column(String(40))
    expiry_date: Mapped[date | None]
    quantity: Mapped[int] = mapped_column(default=0)
    low_stock_threshold: Mapped[int] = mapped_column(default=0)


class Movement(Base):
    """Zapis vsake spremembe zaloge: SET, REFILL, REMOVAL ali SALE."""

    __tablename__ = "movements"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    machine_id: Mapped[UUID]
    slot: Mapped[str] = mapped_column(String(8))
    type: Mapped[str] = mapped_column(String(10))
    product_id: Mapped[UUID]
    batch_code: Mapped[str | None] = mapped_column(String(40))
    quantity: Mapped[int]  # nastavljena, dodana, odstranjena ali prodana količina
    occurred_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
