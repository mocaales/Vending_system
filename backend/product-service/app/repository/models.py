"""Tabele baze Product (ustvari jih migracija v alembic/versions)."""

from uuid import UUID, uuid4

from sqlalchemy import Index, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class Category(Base):
    __tablename__ = "categories"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(60))
    description: Mapped[str | None] = mapped_column(String(200))


# Ime kategorije je enolično ne glede na velike in male črke ("Mleko" = "mleko").
Index("categories_name_unique", func.lower(Category.name), unique=True)
