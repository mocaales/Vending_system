"""Začetni podatki prek gatewaya in ključi avtomatov za simulator (docs/03 §4, §6, §7, §9).

Skript se prijavi kot prvi operater, ustvari kategorije, izdelke in avtomate, napolni njihove
predale in zapiše ključe avtomatov v backend/simulator/machines.local.json (ni v gitu).

Ponoven zagon ne ustvari podvojenih zapisov: kategorija in izdelek se poiščeta po imenu,
avtomat ob 409 MACHINE_CODE_TAKEN po kodi, predal se napolni samo, če še ni bil nastavljen.
Ključ avtomata gateway vrne samo enkrat; če ga v datoteki ni več, skript ustvari novega.

Uporaba (iz korena repozitorija, sklad teče):
    set -a; . ./.env; set +a
    python backend/seed.py

Okolje: BOOTSTRAP_OPERATOR_EMAIL, BOOTSTRAP_OPERATOR_PASSWORD (obvezni),
GATEWAY_URL (privzeto http://localhost:8080).
"""

import json
import os
import sys
from datetime import date, timedelta
from pathlib import Path
from urllib.parse import quote

import httpx

KEYS_FILE = Path(__file__).parent / "simulator" / "machines.local.json"

CATEGORIES = ["Mlečni izdelki", "Meso", "Jajca"]

PRODUCTS = [
    {
        "name": "Mleko 1 l",
        "category": "Mlečni izdelki",
        "price": "1.20",
        "grams": 1030,
        "allergens": ["mleko"],
    },
    {
        "name": "Jogurt 180 g",
        "category": "Mlečni izdelki",
        "price": "0.90",
        "grams": 180,
        "allergens": ["mleko"],
    },
    {"name": "Domača klobasa", "category": "Meso", "price": "4.50", "grams": 400, "allergens": []},
    {
        "name": "Jajca 10 kosov",
        "category": "Jajca",
        "price": "3.20",
        "grams": 600,
        "allergens": ["jajca"],
    },
]

# Predal -> ime izdelka v njem.
MACHINES = [
    {
        "code": "MB-FERI",
        "name": "Avtomat FERI",
        "address": "Koroška cesta 46, Maribor",
        "latitude": 46.5590,
        "longitude": 15.6386,
        "slots": {
            "A1": "Mleko 1 l",
            "A2": "Jogurt 180 g",
            "B1": "Domača klobasa",
            "B2": "Jajca 10 kosov",
        },
    },
    {
        "code": "LJ-CENTER",
        "name": "Avtomat Prešernov trg",
        "address": "Prešernov trg 1, Ljubljana",
        "latitude": 46.0514,
        "longitude": 14.5060,
        "slots": {"A1": "Mleko 1 l", "A2": "Jajca 10 kosov"},
    },
]

STOCK = {"quantity": 10, "lowStockThreshold": 2}
SHELF_LIFE_DAYS = 14


class SeedError(Exception):
    """Gateway je odgovoril drugače, kot skript pričakuje."""


def expect(response: httpx.Response, status: int) -> dict:
    """Telo odgovora, če je status pričakovan; sicer SeedError s kodo napake."""
    if response.status_code != status:
        try:
            code = response.json().get("code")
        except ValueError:
            code = None
        request = response.request
        raise SeedError(f"{request.method} {request.url.path}: {response.status_code} {code}")
    return response.json() if response.content else {}


def login(http: httpx.Client, email: str, password: str) -> None:
    """Prijava prvega operaterja; žeton se pošilja pri vseh nadaljnjih klicih."""
    token = expect(
        http.post("/api/v1/auth/login", json={"email": email, "password": password}), 200
    )
    http.headers["Authorization"] = f"Bearer {token['accessToken']}"


def find_by(http: httpx.Client, path: str, field: str, value: str, **params) -> dict | None:
    """Zapis s točno to vrednostjo polja (iskanje `q` vrne tudi delna ujemanja)."""
    page = expect(http.get(path, params={"q": value, "limit": 200, **params}), 200)
    return next((item for item in page["items"] if item[field] == value), None)


def ensure_category(http: httpx.Client, name: str) -> str:
    category = find_by(http, "/api/v1/categories", "name", name)
    if category is None:
        category = expect(http.post("/api/v1/categories", json={"name": name}), 201)
    return category["id"]


def ensure_product(http: httpx.Client, product: dict, category_id: str) -> dict:
    found = find_by(http, "/api/v1/products", "name", product["name"], categoryId=category_id)
    if found is not None:
        return found
    body = {
        "name": product["name"],
        "categoryId": category_id,
        "referencePrice": product["price"],
        "currency": "EUR",
        "weightGrams": product["grams"],
        "allergens": product["allergens"],
        "imageUrl": f"https://placehold.co/400x300?text={quote(product['name'])}",
    }
    return expect(http.post("/api/v1/products", json=body), 201)


def ensure_machine(http: httpx.Client, machine: dict) -> tuple[dict, str | None]:
    """Vrne (avtomat, ključ); ključ je None, če je avtomat že obstajal."""
    body = {field: machine[field] for field in ("code", "name", "address", "latitude", "longitude")}
    body["slots"] = [{"slotCode": slot, "label": name} for slot, name in machine["slots"].items()]
    response = http.post("/api/v1/machines", json=body)
    if response.status_code == 409 and response.json().get("code") == "MACHINE_CODE_TAKEN":
        found = find_by(http, "/api/v1/machines", "code", machine["code"])
        if found is None:
            raise SeedError(f"Machine {machine['code']} exists but is retired.")
        return found, None
    created = expect(response, 201)
    return created, created["machineKey"]


def create_key(http: httpx.Client, machine_id: str) -> str:
    key = expect(http.post(f"/api/v1/machines/{machine_id}/keys", json={"label": "simulator"}), 201)
    return key["machineKey"]


def fill_slot(http: httpx.Client, machine_id: str, slot: str, product: dict) -> None:
    """Napolni predal, ki še ni bil nastavljen; nastavljenega (in prodaj v njem) ne spremeni."""
    url = f"/api/v1/inventory/{machine_id}/{slot}"
    if http.get(url).status_code != 404:
        return
    body = {
        "productId": product["id"],
        "batchCode": f"SEED-{date.today():%Y%m%d}",
        "expiryDate": str(date.today() + timedelta(days=SHELF_LIFE_DAYS)),
        **STOCK,
    }
    expect(http.put(url, json=body), 200)


def read_keys() -> dict[str, str]:
    """Ključi iz prejšnjega zagona: machineId -> machineKey."""
    if not KEYS_FILE.exists():
        return {}
    saved = json.loads(KEYS_FILE.read_text(encoding="utf-8"))
    return {machine["machineId"]: machine["machineKey"] for machine in saved["machines"]}


def seed(http: httpx.Client) -> list[dict]:
    """Ustvari podatke; vrne avtomate za simulator (ključ, predali, izdelki in cene)."""
    category_ids = {name: ensure_category(http, name) for name in CATEGORIES}
    products = {
        product["name"]: ensure_product(http, product, category_ids[product["category"]])
        for product in PRODUCTS
    }

    old_keys = read_keys()
    result = []
    for machine in MACHINES:
        created, key = ensure_machine(http, machine)
        machine_id = created["id"]
        key = key or old_keys.get(machine_id) or create_key(http, machine_id)
        slots = []
        for slot, name in machine["slots"].items():
            product = products[name]
            fill_slot(http, machine_id, slot, product)
            slots.append(
                {
                    "slotId": slot,
                    "productId": product["id"],
                    "unitPrice": product["referencePrice"],
                    "currency": product["currency"],
                }
            )
        result.append(
            {"machineId": machine_id, "code": machine["code"], "machineKey": key, "slots": slots}
        )
        print(f"{machine['code']}: {machine_id}")
    return result


def main(transport: httpx.BaseTransport | None = None) -> int:
    email = os.environ.get("BOOTSTRAP_OPERATOR_EMAIL")
    password = os.environ.get("BOOTSTRAP_OPERATOR_PASSWORD")
    if not email or not password:
        print("Nastavi BOOTSTRAP_OPERATOR_EMAIL in BOOTSTRAP_OPERATOR_PASSWORD (iz .env).")
        return 1
    gateway = os.environ.get("GATEWAY_URL", "http://localhost:8080")

    # transport v testih nadomesti pravi gateway (httpx.MockTransport)
    with httpx.Client(base_url=gateway, timeout=10.0, transport=transport) as http:
        try:
            login(http, email, password)
            machines = seed(http)
        except (SeedError, httpx.HTTPError) as error:
            print(f"Seed ni uspel: {error}")
            return 1

    KEYS_FILE.parent.mkdir(parents=True, exist_ok=True)
    KEYS_FILE.write_text(json.dumps({"machines": machines}, indent=2) + "\n", encoding="utf-8")
    print(f"Datoteka za simulator: {KEYS_FILE}")  # izpis brez šumnikov (konzola Windows)
    return 0


if __name__ == "__main__":
    sys.exit(main())
