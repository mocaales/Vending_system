"""Telesa zahtev in odgovorov (docs/03 §7). JSON polja so v camelCase (npr. lowStockThreshold)."""

from datetime import date, datetime
from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, StringConstraints
from pydantic.alias_generators import to_camel

from app.core.schemas import StrictModel
from app.domain.decrease import DecreaseReason, DecreaseResult
from app.domain.stock import MovementType

Threshold = Annotated[int, Field(ge=0, le=1000)]
BatchCode = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=40)]


class SlotSet(StrictModel):
    """Telo za nastavitev predala; vsa polja so obvezna. Praznjenje je DELETE."""

    model_config = ConfigDict(alias_generator=to_camel)

    product_id: UUID
    batch_code: BatchCode
    expiry_date: date  # lokalni datum Europe/Ljubljana
    quantity: Annotated[int, Field(ge=1, le=1000)]
    low_stock_threshold: Threshold


class ThresholdIn(StrictModel):
    model_config = ConfigDict(alias_generator=to_camel)

    low_stock_threshold: Threshold


class SlotOut(BaseModel):
    # Iz baze bere polja po imenu (machine_id), v JSON jih zapiše v camelCase (machineId).
    model_config = ConfigDict(from_attributes=True, alias_generator=to_camel, validate_by_name=True)

    machine_id: UUID
    slot: str
    product_id: UUID | None
    batch_code: str | None
    expiry_date: date | None
    quantity: int
    low_stock_threshold: int


class DecreaseIn(StrictModel):
    """Ena prodaja iz outboxa Sales."""

    model_config = ConfigDict(alias_generator=to_camel)

    sale_id: UUID
    product_id: UUID
    quantity: Annotated[int, Field(ge=1, le=100)]


class DecreaseOut(BaseModel):
    model_config = ConfigDict(from_attributes=True, alias_generator=to_camel, validate_by_name=True)

    result: DecreaseResult
    reason: DecreaseReason | None = None  # samo pri NEEDS_RECONCILIATION
    quantity_after: int


class RefillIn(StrictModel):
    """Polnitev predala; isti refillId z istim telesom ne podvoji količine."""

    model_config = ConfigDict(alias_generator=to_camel)

    refill_id: UUID
    machine_id: UUID
    slot: Annotated[str, StringConstraints(min_length=1, max_length=8)]
    product_id: UUID
    batch_code: BatchCode
    expiry_date: date  # lokalni datum Europe/Ljubljana; potekla serija vrne 422
    quantity: Annotated[int, Field(ge=1, le=1000)]


class MovementOut(BaseModel):
    model_config = ConfigDict(from_attributes=True, alias_generator=to_camel, validate_by_name=True)

    id: UUID
    machine_id: UUID
    slot: str
    type: MovementType
    product_id: UUID
    batch_code: str | None
    quantity: int
    sale_id: UUID | None
    refill_id: UUID | None
    occurred_at: datetime


class RemovedOut(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, validate_by_name=True)

    slot: str
    product_id: UUID
    batch_code: str | None
    quantity: int


class ExpiredOut(BaseModel):
    """Odstranjene potekle serije; ponovitev vrne prazen seznam."""

    removed: list[RemovedOut]
