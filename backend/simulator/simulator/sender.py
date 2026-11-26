"""Pošiljanje dogodkov na gateway; neposlani čakajo v vrsti in se ponovijo z istim ID-jem."""

import logging

import httpx

SALES_PATH = "/api/v1/device/sales"
MEASUREMENTS_PATH = "/api/v1/device/measurements"

# Gateway ali storitev trenutno ne more sprejeti: dogodek ostane v vrsti (docs/01 §5.1).
RETRY_STATUSES = {429, 502, 503, 504}

logger = logging.getLogger("simulator")


class Sender:
    def __init__(self, http: httpx.Client):
        self.http = http
        self.pending: list[tuple[str, str, dict]] = []  # (pot, ključ avtomata, dogodek)

    def add(self, path: str, machine_key: str, event: dict) -> None:
        self.pending.append((path, machine_key, event))

    def flush(self) -> None:
        """Pošlje vse dogodke v vrsti. 2xx in zavrnjeni (npr. 422) odidejo iz vrste, ob 503,
        504 ali omrežni napaki dogodek ostane in se ob naslednjem klicu pošlje enak."""
        waiting = []
        for index, (path, machine_key, event) in enumerate(self.pending):
            status = self.post(path, machine_key, event)
            if status is None:  # gateway ni dosegljiv: tudi ostali počakajo na naslednji poskus
                waiting.extend(self.pending[index:])
                break
            if status in RETRY_STATUSES:
                waiting.append((path, machine_key, event))
            elif status >= 400:
                logger.warning("%s rejected with %s: %s", path, status, event)
            else:
                logger.info("%s -> %s", path, status)
        if waiting:
            logger.warning("%d event(s) not delivered, will retry", len(waiting))
        self.pending = waiting

    def post(self, path: str, machine_key: str, event: dict) -> int | None:
        """Statusna koda ali None, če gateway ni dosegljiv."""
        try:
            response = self.http.post(path, json=event, headers={"X-Machine-Key": machine_key})
            return response.status_code
        except httpx.HTTPError:
            return None
