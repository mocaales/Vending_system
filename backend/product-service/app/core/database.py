"""Povezava do baze (SQLAlchemy 2 + psycopg 3) in migracije (Alembic)."""

from collections.abc import Iterator
from functools import cache
from typing import Annotated

from alembic import command
from alembic.config import Config
from fastapi import Depends
from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import DeclarativeBase, Session

from app.core.config import database_url


class Base(DeclarativeBase):
    """Osnovni razred za tabele storitve."""


@cache
def get_engine() -> Engine:
    """En bazen povezav na proces: 5 stalnih in največ 5 dodatnih povezav."""
    return create_engine(
        database_url(),
        pool_size=5,
        max_overflow=5,
        connect_args={"connect_timeout": 3},
    )


def get_session(engine: Annotated[Engine, Depends(get_engine)]) -> Iterator[Session]:
    """Odvisnost poti: ena seja na zahtevo, ki se po zahtevi zapre."""
    with Session(engine) as session:
        yield session


def run_migrations() -> None:
    """Ob zagonu storitve posodobi tabele na zadnjo migracijo (alembic upgrade head)."""
    command.upgrade(Config("alembic.ini"), "head")
