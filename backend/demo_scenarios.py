"""Demo scenariji prodaje in zaloge (docs/02 Demonstracija), samo prek gatewaya.

Scenariji:
  refill                  avtomat, izdelek, polnitev -> premik REFILL
  repeated-sale           ponovljena prodaja -> ena prodaja, en premik SALE
  inventory-down          Inventory ustavljen -> prodaja PENDING, po zagonu APPLIED
  archive-reconciliation  arhiviranje z neusklajeno prodajo -> 409, po RESOLVED 204

Uporaba (iz korena repozitorija, sklad teče):
    set -a; . ./.env; set +a
    python backend/demo_scenarios.py                  # vsi po vrsti
    python backend/demo_scenarios.py inventory-down   # samo izbrani

inventory-down ustavi in znova zažene zabojnik inventory-service (`docker compose`).
Okolje kot pri smoke_test.py: BOOTSTRAP_OPERATOR_EMAIL, BOOTSTRAP_OPERATOR_PASSWORD, GATEWAY_URL.
"""

import os
import subprocess
import sys
import time
from datetime import date, timedelta
from pathlib import Path
from uuid import uuid4

import httpx
from smoke_test import (
    SmokeError,
    create_machine,
    create_product,
    expect,
    login,
    sale_event,
    sale_movements,
    sell,
    wait_until,
)

REPOSITORY = Path(__file__).parents[1]  # docker compose najde docker-compose.yml v korenu
RECOVERY_SECONDS = 120  # Sales ponavlja zmanjšanje z naraščajočim zamikom (5 s, 10 s, 20 s ...)
ATTEMPTS = 5  # poskusi, da Inventory ustavimo pred outboxom Sales (ta pregleda vsako sekundo)


def say(text: str) -> None:
    print(f"  {text}")


def check(condition: bool, message: str) -> None:
    if not condition:
        raise SmokeError(message)


def compose(*args: str) -> None:
    subprocess.run(["docker", "compose", *args], cwd=REPOSITORY, check=True, capture_output=True)


def refill_body(machine_id: str, product_id: str, quantity: int) -> dict:
    return {
        "refillId": str(uuid4()),
        "machineId": machine_id,
        "slot": "A1",
        "productId": product_id,
        "batchCode": "DEMO",
        "expiryDate": str(date.today() + timedelta(days=14)),
        "quantity": quantity,
    }


def prepare(http: httpx.Client, quantity: int) -> tuple[str, str, dict]:
    """Nov avtomat s predalom A1 in nov izdelek; predal napolni s `quantity` kosi."""
    name = f"Demo {uuid4().hex[:6]}"
    machine_id, key = create_machine(http, name)
    product = create_product(http, name)
    body = refill_body(machine_id, product["id"], quantity)
    expect(http.post("/api/v1/inventory/refill", json=body), 201)
    say(f"avtomat in izdelek '{name}', predal A1 napolnjen, kolicina {quantity}")
    return machine_id, key, product


def sale(http: httpx.Client, sale_id: str) -> dict:
    return expect(http.get(f"/api/v1/sales/{sale_id}"), 200)


def sale_state(http: httpx.Client, sale_id: str) -> str:
    return sale(http, sale_id)["inventory"]["state"]


def quantity(http: httpx.Client, machine_id: str) -> int:
    return expect(http.get(f"/api/v1/inventory/{machine_id}/A1"), 200)["quantity"]


# --- scenariji ---------------------------------------------------------------------------


def refill(http: httpx.Client) -> None:
    """Avtomat, izdelek, polnitev: premik REFILL; ista polnitev se ne podvoji."""
    name = f"Demo {uuid4().hex[:6]}"
    machine_id, _ = create_machine(http, name)
    product = create_product(http, name)
    say(f"avtomat in izdelek '{name}'")

    body = refill_body(machine_id, product["id"], 5)
    slot = expect(http.post("/api/v1/inventory/refill", json=body), 201)
    say(f"POST /inventory/refill (5 kosov) -> 201, zaloga {slot['quantity']}")
    slot = expect(http.post("/api/v1/inventory/refill", json=body), 200)
    say(f"ista polnitev (isti refillId) -> 200, zaloga ostane {slot['quantity']}")

    params = {"machineId": machine_id, "type": "REFILL"}
    movements = expect(http.get("/api/v1/inventory/movements", params=params), 200)["items"]
    check(len(movements) == 1, f"REFILL movements: expected 1, got {len(movements)}")
    m = movements[0]
    say(f"premik REFILL: predal {m['slot']}, kolicina {m['quantity']}, serija {m['batchCode']}")


def repeated_sale(http: httpx.Client) -> None:
    """Ponovljena prodaja z istim saleId: ena prodaja, en premik SALE."""
    machine_id, key, product = prepare(http, 5)
    event = sale_event(machine_id, product, quantity=1)

    sell(http, key, event)
    say("prodaja -> 201")
    sell(http, key, event, status=200)
    say("ista prodaja (isti saleId) -> 200")
    changed = dict(event, quantity=2)
    headers = {"X-Machine-Key": key}
    response = http.post("/api/v1/device/sales", json=changed, headers=headers)
    expect(response, 409, "SALE_ID_CONFLICT")
    say("isti saleId z drugo vsebino -> 409 SALE_ID_CONFLICT")

    wait_until(lambda: sale_state(http, event["saleId"]) == "APPLIED", "APPLIED")
    time.sleep(3)  # outbox bi imel čas poslati še enkrat
    sales = expect(http.get("/api/v1/sales", params={"machineId": machine_id}), 200)["total"]
    movements = len(sale_movements(http, machine_id))
    check(sales == 1 and movements == 1, f"expected 1 sale and 1 SALE, got {sales} and {movements}")
    say(f"prodaj: {sales}, premikov SALE: {movements}, zaloga {quantity(http, machine_id)}")


def inventory_down(http: httpx.Client) -> None:
    """Inventory ustavljen po sprejemu prodaje: PENDING, po ponovnem zagonu APPLIED."""
    machine_id, key, product = prepare(http, 10)
    try:
        # Sales ob sprejemu preveri predal v Inventory, zato ga ustavimo šele po 201. Če je
        # outbox hitrejši od ustavitve (prodaja že APPLIED), Inventory zaženemo in ponovimo.
        for _ in range(ATTEMPTS):
            event = sale_event(machine_id, product, quantity=1)
            sell(http, key, event)
            compose("stop", "-t", "0", "inventory-service")
            if sale_state(http, event["saleId"]) == "PENDING":
                break
            say("outbox je bil hitrejsi od ustavitve (APPLIED), poskusim znova")
            compose("up", "-d", "--wait", "inventory-service")
        else:
            raise SmokeError(f"sale was APPLIED before Inventory stopped, {ATTEMPTS} times")
        say("prodaja -> 201, nato docker compose stop inventory-service")
        say("stanje prodaje: PENDING")

        other = sale_event(machine_id, product, quantity=1)
        expect(http.post("/api/v1/device/sales", json=other, headers={"X-Machine-Key": key}), 503)
        say("nova prodaja med izpadom -> 503, nic se ne shrani (simulator ponovi)")
        time.sleep(5)
        check(sale_state(http, event["saleId"]) == "PENDING", "sale left PENDING while down")
        say("po 5 s se vedno PENDING, Sales ponavlja zmanjsanje")
    finally:
        compose("up", "-d", "--wait", "inventory-service")
    say("docker compose up -d --wait inventory-service")

    started = time.monotonic()
    wait_until(lambda: sale_state(http, event["saleId"]) == "APPLIED", "APPLIED", RECOVERY_SECONDS)
    seconds = time.monotonic() - started
    movements = [m for m in sale_movements(http, machine_id) if m["saleId"] == event["saleId"]]
    check(len(movements) == 1, f"SALE movements of the sale: expected 1, got {len(movements)}")
    say(f"stanje prodaje: APPLIED po {seconds:.0f} s, premikov SALE te prodaje: 1")


def archive_reconciliation(http: httpx.Client) -> None:
    """Arhiviranje izdelka z neusklajeno prodajo: 409, po uskladitvi (RESOLVED) 204."""
    machine_id, key, product = prepare(http, 2)
    event = sale_event(machine_id, product, quantity=5)
    sell(http, key, event)
    wait_until(
        lambda: sale_state(http, event["saleId"]) == "NEEDS_RECONCILIATION",
        "NEEDS_RECONCILIATION",
    )
    reason = sale(http, event["saleId"])["inventory"].get("reason")
    left = quantity(http, machine_id)
    say(f"prodaja 5 kosov ob zalogi 2 -> NEEDS_RECONCILIATION ({reason}), zaloga {left}")

    url = f"/api/v1/products/{product['id']}"
    expect(http.delete(url), 409, "PRODUCT_HAS_OPEN_RECONCILIATION")
    say("arhiviranje izdelka -> 409 PRODUCT_HAS_OPEN_RECONCILIATION")

    body = {"resolution": "WRITTEN_OFF", "note": "Demo: razlika odpisana"}
    expect(http.put(f"/api/v1/sales/{event['saleId']}/reconciliation", json=body), 200)
    state = sale_state(http, event["saleId"])
    check(state == "RESOLVED", f"state after reconciliation: expected RESOLVED, got {state}")
    say("PUT /sales/{saleId}/reconciliation (WRITTEN_OFF) -> 200, stanje RESOLVED")

    expect(http.delete(url), 204)
    status = expect(http.get(url), 200)["status"]
    say(f"arhiviranje izdelka -> 204, stanje izdelka {status}")


SCENARIOS = {
    "refill": refill,
    "repeated-sale": repeated_sale,
    "inventory-down": inventory_down,
    "archive-reconciliation": archive_reconciliation,
}


def main(argv: list[str] | None = None, transport: httpx.BaseTransport | None = None) -> int:
    names = (sys.argv[1:] if argv is None else argv) or list(SCENARIOS)
    unknown = [name for name in names if name not in SCENARIOS]
    if unknown:
        print(f"Neznan scenarij: {', '.join(unknown)}. Na voljo: {', '.join(SCENARIOS)}")
        return 1
    email = os.environ.get("BOOTSTRAP_OPERATOR_EMAIL")
    password = os.environ.get("BOOTSTRAP_OPERATOR_PASSWORD")
    if not email or not password:
        print("Nastavi BOOTSTRAP_OPERATOR_EMAIL in BOOTSTRAP_OPERATOR_PASSWORD (iz .env).")
        return 1
    gateway = os.environ.get("GATEWAY_URL", "http://localhost:8080")

    with httpx.Client(base_url=gateway, timeout=15.0, transport=transport) as http:
        name = "prijava"
        try:
            login(http, email, password)
            for name in names:
                scenario = SCENARIOS[name]
                print(f"== {name}: {scenario.__doc__.splitlines()[0]}")
                scenario(http)
                print("OK")
        except (SmokeError, httpx.HTTPError, subprocess.CalledProcessError) as error:
            print(f"FAIL {name}: {error}")
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
