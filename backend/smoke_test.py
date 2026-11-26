"""Smoke test: glavni tok od prijave do alarma, samo prek gatewaya (docs/01 §10, docs/03).

Koraki (vsak je svoja funkcija):
 1. prijava operaterja
 2. avtomat s predalom A1 ter kategorija in izdelek
 3. polnitev predala (3 kosi) in prag nizke zaloge 2
 4. prodaja s ključem avtomata -> zaloga 2, en premik SALE
 5. ponovljena prodaja z istim saleId -> še vedno en premik SALE
 6. alarm LOW_STOCK v Monitoringu in e-pošta v Mailpit (smoke_email.py, #38)
 7. umik avtomata z zalogo -> 409 MACHINE_HAS_STOCK
 8. prodaja brez dovolj zaloge -> NEEDS_RECONCILIATION; arhiviranje izdelka -> 409
    PRODUCT_HAS_OPEN_RECONCILIATION; po uskladitvi arhiviranje 204
 9. praznjenje predala in umik avtomata -> 204 (pospravi za seboj)
10. odjava; stari žeton nato vrne 401

Uporaba (iz korena repozitorija, sklad teče):
    set -a; . ./.env; set +a
    python backend/smoke_test.py

Okolje: BOOTSTRAP_OPERATOR_EMAIL, BOOTSTRAP_OPERATOR_PASSWORD (obvezni), GATEWAY_URL
(privzeto http://localhost:8080), MAILPIT_URL (privzeto http://localhost:8025).
Vrne 0, ko vsi koraki uspejo, sicer 1 in izpiše korak, ki ni uspel.
"""

import os
import sys
import time
from datetime import UTC, date, datetime, timedelta
from uuid import uuid4

import httpx
import smoke_email

WAIT_SECONDS = 60  # outboxi (Sales, Inventory) delujejo v ozadju, zato koraki počakajo


class SmokeError(Exception):
    """Korak ni dobil pričakovanega odgovora."""


def expect(response: httpx.Response, status: int, code: str | None = None) -> dict:
    """Telo odgovora, če sta status (in koda napake) pričakovana; sicer SmokeError."""
    body = response.json() if response.content else {}
    if response.status_code != status or (code and body.get("code") != code):
        request = response.request
        expected = f"{status} {code}" if code else str(status)
        got = f"{response.status_code} {body.get('code', '')}".strip()
        raise SmokeError(f"{request.method} {request.url.path}: expected {expected}, got {got}")
    return body


def wait_until(check, what: str, timeout: float = WAIT_SECONDS):
    """Ponavlja `check`, dokler ne vrne resnične vrednosti; vrne jo."""
    deadline = time.monotonic() + timeout
    while True:
        result = check()
        if result:
            return result
        if time.monotonic() > deadline:
            raise SmokeError(f"{what}: not reached within {timeout:.0f} s")
        time.sleep(1)


def now() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")


# --- koraki ------------------------------------------------------------------------------


def login(http: httpx.Client, email: str, password: str) -> str:
    token = expect(
        http.post("/api/v1/auth/login", json={"email": email, "password": password}), 200
    )["accessToken"]
    http.headers["Authorization"] = f"Bearer {token}"
    return token


def create_machine(http: httpx.Client, name: str) -> tuple[str, str]:
    """Vrne (machineId, machineKey); ključ je v odgovoru samo enkrat."""
    body = {
        "code": name.upper().replace(" ", "-"),
        "name": name,
        "address": "Smoke test",
        "latitude": 46.5590,
        "longitude": 15.6386,
        "slots": [{"slotCode": "A1"}],
    }
    machine = expect(http.post("/api/v1/machines", json=body), 201)
    return machine["id"], machine["machineKey"]


def create_product(http: httpx.Client, name: str) -> dict:
    category = expect(http.post("/api/v1/categories", json={"name": name}), 201)
    body = {
        "name": name,
        "categoryId": category["id"],
        "referencePrice": "1.20",
        "currency": "EUR",
        "weightGrams": 1030,
        "allergens": ["mleko"],
        "imageUrl": "https://placehold.co/400x300?text=Smoke",
    }
    return expect(http.post("/api/v1/products", json=body), 201)


def refill(http: httpx.Client, machine_id: str, product_id: str) -> None:
    """3 kosi v predal A1 in prag 2: že prva prodaja spusti zalogo na prag."""
    body = {
        "refillId": str(uuid4()),
        "machineId": machine_id,
        "slot": "A1",
        "productId": product_id,
        "batchCode": "SMOKE",
        "expiryDate": str(date.today() + timedelta(days=14)),
        "quantity": 3,
    }
    expect(http.post("/api/v1/inventory/refill", json=body), 201)
    threshold = {"lowStockThreshold": 2}
    expect(http.put(f"/api/v1/inventory/{machine_id}/A1/threshold", json=threshold), 200)


def sale_event(machine_id: str, product: dict, quantity: int) -> dict:
    return {
        "saleId": str(uuid4()),
        "machineId": machine_id,
        "slotId": "A1",
        "productId": product["id"],
        "quantity": quantity,
        "unitPricePaid": product["referencePrice"],
        "currency": "EUR",
        "paymentMethod": "CARD",
        "occurredAt": now(),
    }


def sell(http: httpx.Client, machine_key: str, sale: dict, status: int = 201) -> None:
    """Prodaja prek poti naprav; avtomat se prijavi s ključem, ne z žetonom operaterja."""
    response = http.post("/api/v1/device/sales", json=sale, headers={"X-Machine-Key": machine_key})
    expect(response, status)


def sale_movements(http: httpx.Client, machine_id: str) -> list[dict]:
    params = {"machineId": machine_id, "type": "SALE"}
    return expect(http.get("/api/v1/inventory/movements", params=params), 200)["items"]


def wait_for_decrease(http: httpx.Client, machine_id: str) -> None:
    """Sales zmanjšanje pošlje prek outboxa: počakaj na en premik SALE in zalogo 2."""
    wait_until(lambda: sale_movements(http, machine_id), "SALE movement")
    stock = expect(http.get(f"/api/v1/inventory/{machine_id}/A1"), 200)
    if stock["quantity"] != 2:
        raise SmokeError(f"quantity after sale: expected 2, got {stock['quantity']}")


def repeat_sale(http: httpx.Client, machine_id: str, machine_key: str, sale: dict) -> None:
    """Ista prodaja še enkrat: 200 in brez novega premika (počaka, da bi outbox lahko poslal)."""
    sell(http, machine_key, sale, status=200)
    time.sleep(3)
    count = len(sale_movements(http, machine_id))
    if count != 1:
        raise SmokeError(f"SALE movements after repeated sale: expected 1, got {count}")


def wait_for_low_stock_alert(http: httpx.Client, machine_id: str) -> None:
    def active_alert():
        params = {"machineId": machine_id, "type": "LOW_STOCK", "status": "ACTIVE"}
        return expect(http.get("/api/v1/alerts", params=params), 200)["items"]

    wait_until(active_alert, "LOW_STOCK alert")


def retire_with_stock_is_rejected(http: httpx.Client, machine_id: str) -> None:
    expect(http.delete(f"/api/v1/machines/{machine_id}"), 409, "MACHINE_HAS_STOCK")


def open_reconciliation(http: httpx.Client, machine_id: str, key: str, product: dict) -> str:
    """Prodaja 10 kosov ob zalogi 2: Inventory je ne more izvesti, prodaja čaka na uskladitev."""
    sale = sale_event(machine_id, product, quantity=10)
    sell(http, key, sale)

    def needs_reconciliation():
        found = expect(http.get(f"/api/v1/sales/{sale['saleId']}"), 200)
        return found["inventory"]["state"] == "NEEDS_RECONCILIATION"

    wait_until(needs_reconciliation, "NEEDS_RECONCILIATION")
    return sale["saleId"]


def archive_after_reconciliation(http: httpx.Client, product_id: str, sale_id: str) -> None:
    url = f"/api/v1/products/{product_id}"
    expect(http.delete(url), 409, "PRODUCT_HAS_OPEN_RECONCILIATION")
    body = {"resolution": "WRITTEN_OFF", "note": "Smoke test"}
    expect(http.put(f"/api/v1/sales/{sale_id}/reconciliation", json=body), 200)
    expect(http.delete(url), 204)


def empty_and_retire(http: httpx.Client, machine_id: str) -> None:
    expect(http.delete(f"/api/v1/inventory/{machine_id}/A1"), 204)
    expect(http.delete(f"/api/v1/machines/{machine_id}"), 204)


def logout(http: httpx.Client, token: str) -> None:
    expect(http.delete("/api/v1/auth/session"), 204)
    old = {"Authorization": f"Bearer {token}"}
    expect(http.get("/api/v1/auth/me", headers=old), 401)


# --- glavni tok --------------------------------------------------------------------------


def run(http: httpx.Client, email: str, password: str) -> None:
    name = f"Smoke {uuid4().hex[:8]}"  # enolično ime, da se lahko zažene večkrat

    def step(title, function, *args):
        try:
            result = function(*args)
        except (SmokeError, AssertionError, httpx.HTTPError) as error:  # Assertion: smoke_email
            raise SmokeError(f"{title}: {error}") from None
        print(f"OK  {title}")
        return result

    token = step("prijava", login, http, email, password)
    machine_id, key = step("avtomat", create_machine, http, name)
    product = step("kategorija in izdelek", create_product, http, name)
    step("polnitev in prag", refill, http, machine_id, product["id"])
    sale = sale_event(machine_id, product, quantity=1)
    step("prodaja s kljucem avtomata", sell, http, key, sale)
    step("zmanjsana zaloga, en premik SALE", wait_for_decrease, http, machine_id)
    step("ponovljena prodaja brez novega premika", repeat_sale, http, machine_id, key, sale)
    step("alarm LOW_STOCK", wait_for_low_stock_alert, http, machine_id)
    step("e-posta LOW_STOCK", smoke_email.check_low_stock_email, http, name, name)
    step("umik z zalogo: 409", retire_with_stock_is_rejected, http, machine_id)
    sale_id = step("odprta uskladitev", open_reconciliation, http, machine_id, key, product)
    step(
        "arhiviranje: 409, uskladitev, 204",
        archive_after_reconciliation,
        http,
        product["id"],
        sale_id,
    )
    step("praznjenje in umik avtomata", empty_and_retire, http, machine_id)
    step("odjava, stari zeton 401", logout, http, token)


def main(transport: httpx.BaseTransport | None = None) -> int:
    email = os.environ.get("BOOTSTRAP_OPERATOR_EMAIL")
    password = os.environ.get("BOOTSTRAP_OPERATOR_PASSWORD")
    if not email or not password:
        print("Nastavi BOOTSTRAP_OPERATOR_EMAIL in BOOTSTRAP_OPERATOR_PASSWORD (iz .env).")
        return 1
    gateway = os.environ.get("GATEWAY_URL", "http://localhost:8080")

    # transport v testih nadomesti pravi gateway (httpx.MockTransport)
    with httpx.Client(base_url=gateway, timeout=15.0, transport=transport) as http:
        try:
            run(http, email, password)
        except SmokeError as error:
            print(f"FAIL {error}")
            return 1
    print("Smoke test uspel.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
