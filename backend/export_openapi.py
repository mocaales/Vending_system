"""Izvoz specifikacij OpenAPI storitev v backend/<storitev>/openapi.yaml (docs/03 §10).

Storitve specifikacijo generirajo iz kode in jo strežejo na /openapi.json. Skript jo prebere
iz tekočih zabojnikov in jo zapiše v openapi.yaml, a samo, če se vsebina razlikuje; ponoven
izvoz brez sprememb kode datotek ne spremeni. Z --check ničesar ne zapiše in vrne napako, če se
katera razlikuje ali storitev ni dosegljiva. Gateway ni na seznamu: njegov openapi.yaml je
izvor (spec-first), zato ga skript nikoli ne prepiše.

Uporaba:
    python backend/export_openapi.py               # vse storitve
    python backend/export_openapi.py --check       # samo primerjava (CI)
    python backend/export_openapi.py product inventory

Storitve so dosegljive samo v omrežju Compose, zato skript teče v njem, npr.:
    docker run --rm --network container:$(docker compose ps -q inventory-service) \
        -v "$PWD:/repo" -w /repo python:3.12-slim \
        sh -c "pip install -q httpx pyyaml && python backend/export_openapi.py --check"
Naslove lahko zamenjaš s spremenljivkami MACHINE_URL, MONITORING_URL, PRODUCT_URL,
INVENTORY_URL in SALES_URL. Skrivnosti skript ne potrebuje (/openapi.json je brez žetona).
"""

import argparse
import os
import sys
from pathlib import Path

import httpx
import yaml

BACKEND = Path(__file__).parent
SERVICES = ["machine", "monitoring", "product", "inventory", "sales"]


def service_url(name: str) -> str:
    """Naslov storitve iz okolja ali ime zabojnika v omrežju Compose."""
    return os.environ.get(f"{name.upper()}_URL", f"http://{name}-service:8000")


def spec_path(name: str) -> Path:
    return BACKEND / f"{name}-service" / "openapi.yaml"


def fetch_spec(http: httpx.Client, name: str) -> dict | None:
    """Specifikacija tekoče storitve ali None, če storitev ne odgovori pravilno."""
    try:
        response = http.get(f"{service_url(name)}/openapi.json")
        response.raise_for_status()
        return response.json()
    except (httpx.HTTPError, ValueError):
        return None


def read_spec(path: Path) -> dict | None:
    if not path.exists():
        return None
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def write_spec(path: Path, spec: dict) -> None:
    """Vrstni red ključev ostane kot v /openapi.json, šumniki ostanejo berljivi."""
    with path.open("w", encoding="utf-8", newline="\n") as file:
        yaml.safe_dump(spec, file, sort_keys=False, allow_unicode=True)


def export(http: httpx.Client, names: list[str], check: bool) -> bool:
    """Izvozi ali primerja storitve; vrne True, ko je vse v redu."""
    ok = True
    for name in names:
        spec = fetch_spec(http, name)
        if spec is None:
            print(f"{name}: ni dosegljiva ({service_url(name)})")
            ok = False
        elif spec == read_spec(spec_path(name)):  # primerja vsebino, ne oblike zapisa
            print(f"{name}: brez sprememb")
        elif check:
            print(f"{name}: openapi.yaml se razlikuje od /openapi.json")
            ok = False
        else:
            write_spec(spec_path(name), spec)
            print(f"{name}: zapisan {spec_path(name).relative_to(BACKEND.parent)}")
    return ok


def main(argv: list[str] | None = None, transport: httpx.BaseTransport | None = None) -> int:
    parser = argparse.ArgumentParser(description="Izvoz OpenAPI storitev v openapi.yaml.")
    parser.add_argument("--check", action="store_true", help="samo primerja, nič ne zapiše")
    parser.add_argument("services", nargs="*", help=f"storitve (privzeto vse: {SERVICES})")
    args = parser.parse_args(argv)

    unknown = [name for name in args.services if name not in SERVICES]
    if unknown:
        parser.error(f"neznana storitev: {', '.join(unknown)}; gateway se ne izvaža")

    # transport v testih nadomesti prave storitve (httpx.MockTransport)
    with httpx.Client(timeout=5.0, transport=transport) as http:
        ok = export(http, args.services or SERVICES, args.check)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
