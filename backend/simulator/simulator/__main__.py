"""Zagon: python -m simulator [--machine MB-FERI] [--temperature 9] [--offline-after 60 ...]."""

import logging
import sys
import tempfile
import time
from datetime import UTC, datetime
from pathlib import Path

import httpx

from simulator.config import ConfigError, load_machines, parse_settings
from simulator.device import Simulator
from simulator.sender import Sender

# Zabojnik je zdrav, dokler se ta datoteka osvežuje (healthcheck v docker-compose.yml).
HEARTBEAT = Path(tempfile.gettempdir()) / "simulator-heartbeat"


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    logging.getLogger("httpx").setLevel(logging.WARNING)  # vsak dogodek se izpiše enkrat
    settings = parse_settings(argv)
    try:
        machines = load_machines(settings.machines_file, settings.machine, settings.key)
    except ConfigError as error:
        print(error)
        return 1

    codes = ", ".join(machine["code"] for machine in machines)
    logging.info("Simulating %s via %s", codes, settings.gateway)
    with httpx.Client(base_url=settings.gateway, timeout=5.0) as http:
        simulator = Simulator(machines, Sender(http), settings, datetime.now(UTC))
        try:
            while True:
                simulator.step(datetime.now(UTC))
                HEARTBEAT.touch()
                time.sleep(1)
        except KeyboardInterrupt:
            return 0


if __name__ == "__main__":
    sys.exit(main())
