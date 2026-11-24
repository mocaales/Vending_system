"""Odjemalec za Machine Service (Aleš): ali avtomat in predal obstajata (docs/03 §4)."""

from functools import cache
from uuid import UUID

from app.clients.http import ServiceClient
from app.core.config import machine_url, own_service_token


class MachineClient(ServiceClient):
    def get_machine(self, machine_id: UUID) -> dict | None:
        """MachineOut (`status`, `slots[].slotCode`) ali None za neznan avtomat."""
        return self.get_or_none(f"/v1/machines/{machine_id}")


@cache
def get_machine_client() -> MachineClient:
    return MachineClient("Machine", machine_url(), own_service_token())
