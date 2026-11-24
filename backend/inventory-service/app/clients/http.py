"""Skupni del odjemalcev za druge storitve: notranji žeton, X-Trace-Id in časovna omejitev."""

import httpx

from app.core.errors import ApiError
from app.core.trace import TRACE_HEADER, current_trace_id


class ServiceClient:
    def __init__(
        self, name: str, base_url: str, token: str, transport: httpx.BaseTransport | None = None
    ):
        # Ena povezava za vse klice; transport v testih nadomesti pravo storitev (MockTransport).
        self.name = name
        self.http = httpx.Client(
            base_url=base_url,
            headers={"X-Service-Token": token},
            timeout=3.0,
            transport=transport,
        )

    def get_or_none(self, path: str) -> dict | None:
        """GET zapisa; 404 vrne None. Če storitev ne odgovori pravilno: 503 (odjemalec ponovi)."""
        try:
            response = self.http.get(path, headers={TRACE_HEADER: current_trace_id()})
            if response.status_code == 404:
                return None
            response.raise_for_status()
            return response.json()
        except (httpx.HTTPError, ValueError):
            raise ApiError(
                503, "DEPENDENCY_UNAVAILABLE", f"{self.name} is not reachable."
            ) from None
