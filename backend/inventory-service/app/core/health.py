"""Preverjanje zdravja za Docker Compose; poti niso v OpenAPI."""

from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy import Engine, text
from sqlalchemy.exc import SQLAlchemyError

from app.core.database import get_engine
from app.core.errors import ApiError

router = APIRouter(include_in_schema=False)


@router.get("/health/live")
def live() -> dict:
    """Proces teče."""
    return {"status": "UP"}


@router.get("/health/ready")
def ready(engine: Annotated[Engine, Depends(get_engine)]) -> dict:
    """Lastna baza je dosegljiva; sicer 503."""
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
    except SQLAlchemyError:
        raise ApiError(503, "DEPENDENCY_UNAVAILABLE", "Database is not reachable.") from None
    return {"status": "UP"}
