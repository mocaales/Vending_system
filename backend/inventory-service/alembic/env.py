"""Alembic požene migracije na lastni bazi storitve (isti bazen povezav kot aplikacija)."""

from alembic import context
from app.core.database import Base, get_engine

with get_engine().connect() as connection:
    context.configure(connection=connection, target_metadata=Base.metadata)
    with context.begin_transaction():
        context.run_migrations()
