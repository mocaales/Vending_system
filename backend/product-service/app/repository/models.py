"""Tabele baze Product (ustvarijo jih migracije v alembic/versions)."""

from decimal import Decimal
from uuid import UUID, uuid4

from sqlalchemy import ARRAY, ForeignKey, Index, Numeric, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class Category(Base):
    __tablename__ = "categories"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(60))
    description: Mapped[str | None] = mapped_column(String(200))


# Ime kategorije je enolično ne glede na velike in male črke ("Mleko" = "mleko").
Index("categories_name_unique", func.lower(Category.name), unique=True)


class Product(Base):
    __tablename__ = "products"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(100))
    category_id: Mapped[UUID] = mapped_column(ForeignKey("categories.id"))
    reference_price: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    currency: Mapped[str] = mapped_column(String(3))
    weight_grams: Mapped[int]
    allergens: Mapped[list[str]] = mapped_column(ARRAY(String(50)))
    image_url: Mapped[str] = mapped_column(String(500))
    status: Mapped[str] = mapped_column(String(10), default="ACTIVE")  # ACTIVE ali ARCHIVED

    category: Mapped[Category] = relationship()

    @property
    def category_name(self) -> str:
        return self.category.name
