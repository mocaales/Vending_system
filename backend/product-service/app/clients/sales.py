"""Odjemalec za Sales Service (Aleš): preverjanje odprtih uskladitev pred arhiviranjem izdelka."""

from functools import cache
from uuid import UUID

import httpx

from app.core.config import own_service_token, sales_url
from app.core.errors import ApiError
from app.core.trace import TRACE_HEADER, current_trace_id


class SalesClient:
    def __init__(self, base_url: str, token: str, transport: httpx.BaseTransport | None = None):
        # Ena povezava za vse klice; transport v testih nadomesti pravi Sales (MockTransport).
        self.http = httpx.Client(
            base_url=base_url,
            headers={"X-Service-Token": token},
            timeout=3.0,
            transport=transport,
        )

    def has_open_reconciliation(self, product_id: UUID) -> bool:
        """Ali ima kakšna prodaja izdelka stanje NEEDS_RECONCILIATION (docs/03 §6)."""
        params = {
            "productId": str(product_id),
            "reconciliation": "NEEDS_RECONCILIATION",
            "limit": 1,
        }
        try:
            response = self.http.get(
                "/v1/sales", params=params, headers={TRACE_HEADER: current_trace_id()}
            )
            response.raise_for_status()
            return len(response.json()["items"]) > 0
        except (httpx.HTTPError, ValueError, KeyError):
            # Sales ne odgovori ali odgovori napačno: izdelek ostane aktiven, odjemalec ponovi.
            raise ApiError(503, "DEPENDENCY_UNAVAILABLE", "Sales is not reachable.") from None


@cache
def get_sales_client() -> SalesClient:
    return SalesClient(sales_url(), own_service_token())
