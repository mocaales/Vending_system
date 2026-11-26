"""Tabele baze Inventory (ustvarijo jih migracije v alembic/versions)."""

from datetime import date, datetime
from uuid import UUID, uuid4

from sqlalchemy import BigInteger, DateTime, String, func
from sqlalchemy.dialects.postgresql import JSONB
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
    sale_id: Mapped[UUID | None]  # samo pri SALE
    refill_id: Mapped[UUID | None]  # samo pri REFILL
    occurred_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )


class Decrease(Base):
    """Shranjen rezultat zmanjšanja po prodaji; isti saleId se ne izvede dvakrat."""

    __tablename__ = "decreases"

    sale_id: Mapped[UUID] = mapped_column(primary_key=True)
    machine_id: Mapped[UUID]
    slot: Mapped[str] = mapped_column(String(8))
    product_id: Mapped[UUID]
    quantity: Mapped[int]
    result: Mapped[str] = mapped_column(String(25))  # APPLIED ali NEEDS_RECONCILIATION
    reason: Mapped[str | None] = mapped_column(String(20))
    quantity_after: Mapped[int]


class Refill(Base):
    """Shranjena polnitev; isti refillId ne podvoji količine."""

    __tablename__ = "refills"

    refill_id: Mapped[UUID] = mapped_column(primary_key=True)
    machine_id: Mapped[UUID]
    slot: Mapped[str] = mapped_column(String(8))
    product_id: Mapped[UUID]
    batch_code: Mapped[str] = mapped_column(String(40))
    expiry_date: Mapped[date]
    quantity: Mapped[int]


class LowStockReport(Base):
    """Outbox nizke zaloge: zadnje poročilo za avtomat. Delavec ga pošlje v Monitoring, dokler
    poslana verzija (sent_version) ni enaka zadnji (version)."""

    __tablename__ = "low_stock_reports"

    machine_id: Mapped[UUID] = mapped_column(primary_key=True)
    version: Mapped[int] = mapped_column(BigInteger)  # narašča ob vsaki spremembi seznama
    slots: Mapped[list] = mapped_column(JSONB)  # [{slotCode, productId, quantity, ...}]
    sent_version: Mapped[int] = mapped_column(BigInteger)
