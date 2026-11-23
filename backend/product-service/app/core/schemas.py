"""Skupni modeli: telo zahteve brez neznanih polj in seznam s paginacijo."""

from dataclasses import dataclass
from typing import Annotated

from fastapi import Query
from pydantic import BaseModel, ConfigDict


class StrictModel(BaseModel):
    """Osnova za telesa zahtev: neznano polje vrne 422 VALIDATION_ERROR."""

    model_config = ConfigDict(extra="forbid")


class Page[T](BaseModel):
    """Odgovor seznama: {items, total, limit, offset}."""

    items: list[T]
    total: int
    limit: int
    offset: int


@dataclass
class PageParams:
    limit: int
    offset: int


def page_params(
    limit: Annotated[int, Query(ge=1, le=200)] = 50,
    offset: Annotated[int, Query(ge=0)] = 0,
) -> PageParams:
    """Odvisnost poti za parametra `limit` (privzeto 50, največ 200) in `offset`."""
    return PageParams(limit=limit, offset=offset)
