"""Nastavitve simulatorja (ukazna vrstica ali okolje) in avtomati iz machines.local.json."""

import argparse
import json
import os
from pathlib import Path

DEFAULT_MACHINES_FILE = Path(__file__).parents[1] / "machines.local.json"


class ConfigError(Exception):
    """Avtomatov ni mogoče prebrati ali izbrati."""


def parse_settings(argv: list[str] | None = None) -> argparse.Namespace:
    """Vsak parameter ima privzeto vrednost iz okolja, ukazna vrstica jo preglasi."""
    env = os.environ.get
    parser = argparse.ArgumentParser(description="Simulator avtomatov prek gatewaya.")
    parser.add_argument("--gateway", default=env("GATEWAY_URL", "http://localhost:8080"))
    parser.add_argument(
        "--machines-file", type=Path, default=Path(env("MACHINES_FILE", DEFAULT_MACHINES_FILE))
    )
    parser.add_argument(
        "--machine", default=env("SIM_MACHINE"), help="koda avtomata (privzeto vsi iz datoteke)"
    )
    parser.add_argument(
        "--key", default=env("SIM_MACHINE_KEY"), help="drug ključ avtomata (samo z --machine)"
    )
    parser.add_argument(
        "--temperature", type=float, default=float(env("SIM_TEMPERATURE", "4.0")), help="°C"
    )
    parser.add_argument("--door", choices=["CLOSED", "OPEN"], default=env("SIM_DOOR", "CLOSED"))
    parser.add_argument(
        "--measurement-every",
        type=float,
        default=float(env("SIM_MEASUREMENT_EVERY", "60")),
        help="sekunde med meritvami",
    )
    parser.add_argument(
        "--sale-every",
        type=float,
        default=float(env("SIM_SALE_EVERY", "30")),
        help="povprečne sekunde med prodajami enega avtomata",
    )
    parser.add_argument(
        "--offline-after",
        type=float,
        default=float(env("SIM_OFFLINE_AFTER", "0")),
        help="izguba povezave: po koliko sekundah se začne",
    )
    parser.add_argument(
        "--offline-for",
        type=float,
        default=float(env("SIM_OFFLINE_FOR", "0")),
        help="izguba povezave: koliko sekund traja (0 = brez)",
    )
    settings = parser.parse_args(argv)
    if settings.key and not settings.machine:
        parser.error("--key velja samo skupaj z --machine")
    return settings


def load_machines(path: Path, code: str | None = None, key: str | None = None) -> list[dict]:
    """Avtomati, ki jih je zapisal seed.py; `code` izbere enega, `key` zamenja njegov ključ."""
    if not path.exists():
        raise ConfigError(f"{path} ne obstaja; najprej zaženi python backend/seed.py.")
    machines = json.loads(path.read_text(encoding="utf-8"))["machines"]
    if code is not None:
        machines = [machine for machine in machines if machine["code"] == code]
        if not machines:
            raise ConfigError(f"Avtomata {code} ni v {path}.")
    if key is not None:
        machines[0]["machineKey"] = key
    return machines
