"""Odjemalec za Product Service: ali izdelek obstaja in je aktiven (docs/03 §6)."""

from functools import cache
from uuid import UUID

from app.clients.http import ServiceClient
from app.core.config import own_service_token, product_url


class ProductClient(ServiceClient):
    def get_product(self, product_id: UUID) -> dict | None:
        """ProductOut (`status` ACTIVE ali ARCHIVED) ali None za neznan izdelek."""
        return self.get_or_none(f"/v1/products/{product_id}")


@cache
def get_product_client() -> ProductClient:
    return ProductClient("Product", product_url(), own_service_token())
