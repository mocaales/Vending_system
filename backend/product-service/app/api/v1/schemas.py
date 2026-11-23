"""Telesa zahtev in odgovorov (docs/03 §6). JSON polja so v camelCase (npr. categoryId)."""

from decimal import Decimal
from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, BeforeValidator, ConfigDict, Field, HttpUrl, StringConstraints
from pydantic.alias_generators import to_camel

from app.core.schemas import StrictModel
from app.domain.products import ProductStatus


def _decimal_to_text(value):
    """Denar iz baze (Decimal) zapiše kot niz z dvema decimalkama, npr. "1.80"."""
    return f"{value:.2f}" if isinstance(value, Decimal) else value


# Denar je decimalni niz, nenegativen, z največ dvema decimalkama (docs/03 §1).
Money = Annotated[
    str,
    StringConstraints(pattern=r"^\d{1,10}(\.\d{1,2})?$"),
    BeforeValidator(_decimal_to_text),  # zadnji v seznamu, zato se izvede najprej
]
Currency = Annotated[str, StringConstraints(pattern=r"^[A-Z]{3}$")]
Allergen = Annotated[str, StringConstraints(min_length=1, max_length=50)]


class CategoryIn(StrictModel):
    name: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=60)]
    description: Annotated[str, StringConstraints(max_length=200)] | None = None


class CategoryOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    name: str
    description: str | None


class ProductIn(StrictModel):
    """Telo za ustvarjanje in zamenjavo izdelka; vsa polja so obvezna, `sku` ne obstaja."""

    model_config = ConfigDict(alias_generator=to_camel)

    name: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=100)]
    category_id: UUID
    reference_price: Money
    currency: Currency
    weight_grams: Annotated[int, Field(ge=1, le=100000)]
    allergens: Annotated[list[Allergen], Field(max_length=20)]
    image_url: Annotated[HttpUrl, Field(max_length=500)]

    def to_fields(self) -> dict:
        """Polja za tabelo products: denar kot Decimal, URL kot niz."""
        return {
            "name": self.name,
            "category_id": self.category_id,
            "reference_price": Decimal(self.reference_price),
            "currency": self.currency,
            "weight_grams": self.weight_grams,
            "allergens": self.allergens,
            "image_url": str(self.image_url),
        }


class ProductOut(BaseModel):
    # Iz baze bere polja po imenu (category_id), v JSON jih zapiše v camelCase (categoryId).
    model_config = ConfigDict(from_attributes=True, alias_generator=to_camel, validate_by_name=True)

    id: UUID
    name: str
    category_id: UUID
    category_name: str
    reference_price: Money
    currency: str
    weight_grams: int
    allergens: list[str]
    image_url: str
    status: ProductStatus
