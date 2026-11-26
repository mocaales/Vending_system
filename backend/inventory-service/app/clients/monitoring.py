"""Odjemalec za Monitoring Service (Aleš): poročilo nizke zaloge (docs/03 §5)."""

from functools import cache

from app.clients.http import ServiceClient
from app.core.config import monitoring_url, own_service_token


class MonitoringClient(ServiceClient):
    def report_low_stock(self, report: dict) -> bool:
        """POST /v1/alerts; True, ko ga Monitoring sprejme (201 OPENED ali 200)."""
        status = self.post("/v1/alerts", report)
        return status is not None and 200 <= status < 300


@cache
def get_monitoring_client() -> MonitoringClient:
    return MonitoringClient("Monitoring", monitoring_url(), own_service_token())
