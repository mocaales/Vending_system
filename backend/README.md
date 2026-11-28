# Backend

**Status:** načrtovano (M0). Vsaka komponenta ima svoj `Dockerfile`, svojo bazo PostgreSQL v Compose in `openapi.yaml` ter se gradi in namešča neodvisno. Datoteka gradnje je po tehnologiji: `pom.xml` (Java), `pyproject.toml` (Python), `package.json` (Node).

| Pot | Vsebina | Tehnologija | Lastnik |
|---|---|---|---|
| `machine-service/` | avtomati, predali, ključi avtomatov | Spring Boot 4.1 (Java 21) | Aleš Močnik |
| `monitoring-service/` | meritve, alarmi, pragovi, e-pošta | Spring Boot 4.1 (Java 21) | Aleš Močnik |
| `sales-service/` | prodaje, statistika, uskladitev, dnevni zaključki | Spring Boot 4.1 (Java 21) | Aleš Močnik |
| `product-service/` | katalog izdelkov in kategorije | FastAPI (Python 3.12) | Nejc Cekuta |
| `inventory-service/` | zaloga, polnitve, odpis, premiki | FastAPI (Python 3.12) | Nejc Cekuta |
| `gateway/` | API Gateway, Frontend API, računi operaterjev | Express (Node.js 24) | Darko Sever |
| `simulator/` | simulator avtomatov | Python 3.12, httpx | Nejc Cekuta |
| `seed.py`, `export_openapi.py` | začetni podatki prek gatewaya, izvoz in primerjava OpenAPI | Python | Nejc Cekuta |
| `smoke_test.py` | preverjanje glavnega toka od prijave do alarma | Python | Nejc Cekuta (opravilo `smoke` v CI: Darko Sever) |
| `demo_scenarios.py` | demo scenariji prek gatewaya (prodaja in zaloga) | Python | Nejc Cekuta |

Struktura komponent: [docs/01 §9](../docs/01-arhitektura.md#9-struktura-komponent). Zagon: [docs/01 §10](../docs/01-arhitektura.md#10-zagon-v-dockerju). Končne točke: [docs/03](../docs/03-api-pogodbe.md).

## Lokalni zagon

Iz korena repozitorija:

```bash
cp .env.example .env            # .env ni v gitu
openssl rand -hex 32            # za vsako prazno skrivnost v .env (gesla baz, JWT_SECRET, žetoni)
docker compose up -d --wait     # šest baz, Mailpit in komponente, ki so že na main
docker compose down -v          # ustavi in izbriše tudi podatke baz
```

Mailpit (prejete e-pošte alarmov): http://localhost:8025. Baze in storitve od zunaj niso dosegljive.

## Začetni podatki

Ko sklad teče, iz korena repozitorija:

```bash
pip install httpx                # enkrat
set -a; . ./.env; set +a         # BOOTSTRAP_OPERATOR_EMAIL in BOOTSTRAP_OPERATOR_PASSWORD v lupino
python backend/seed.py           # kategorije, izdelki, avtomati in zaloga prek gatewaya
```

Ključi avtomatov za simulator so v `backend/simulator/machines.local.json` (ni v gitu). Ponoven zagon ne ustvari podvojenih zapisov in ne spremeni že nastavljenih predalov. Gateway privzeto kliče na `http://localhost:8080` (drugače `GATEWAY_URL`).

## Simulator

Po `seed.py` simulator s ključi iz `machines.local.json` pošilja meritve (vsako minuto) in prodaje prek gatewaya:

```bash
docker compose --profile sim up -d simulator       # vsi avtomati iz seed.py
pip install -e backend/simulator                   # ali lokalno, s parametri:
python -m simulator --machine MB-FERI --temperature 9.5           # alarm TEMPERATURE_HIGH
python -m simulator --door OPEN                                   # alarm DOOR_OPEN
python -m simulator --offline-after 60 --offline-for 400          # izguba povezave, alarm OFFLINE
```

Neposlane dogodke (503, 504, gateway ni dosegljiv) ponavlja z istim ID-jem, dokler ne dobi 2xx. Vsi parametri: `python -m simulator --help`; enako se nastavijo z okoljem (`SIM_TEMPERATURE`, `SIM_DOOR`, `SIM_SALE_EVERY`, ...).

## Smoke test

Glavni tok od prijave do alarma samo prek gatewaya (tudi opravilo `smoke` v CI):

```bash
set -a; . ./.env; set +a
python backend/smoke_test.py     # vsak korak izpiše OK; ob napaki FAIL <korak> in koda 1
```

Ustvari svoj avtomat in izdelek z enoličnim imenom, zato se lahko zažene večkrat; na koncu avtomat izprazni in umakne.

## Demo scenariji

Scenariji prodaje in zaloge iz [docs/02 Demonstracija](../docs/02-razdelitev-dela.md#demonstracija), samo prek gatewaya. Vsak ustvari svoj avtomat (predal A1) in izdelek z imenom `Demo <id>`, zato se lahko zažene večkrat; zapisi ostanejo in so vidni v Web UI (Zaloga, Premiki, Izdelki).

```bash
set -a; . ./.env; set +a
python backend/demo_scenarios.py                        # vsi štirje po vrsti (okoli 40 s)
python backend/demo_scenarios.py inventory-down         # samo izbrani
```

| Scenarij | Koraki | Dokaz |
|---|---|---|
| `refill` | avtomat, izdelek, polnitev 5 kosov, ista polnitev še enkrat | 201 in 200, zaloga ostane 5, en premik `REFILL` |
| `repeated-sale` | prodaja, ista prodaja (isti `saleId`), isti `saleId` z drugo vsebino | 201, 200, 409 `SALE_ID_CONFLICT`; ena prodaja, en premik `SALE` |
| `inventory-down` | prodaja, takoj `docker compose stop inventory-service`, nova prodaja, ponoven zagon | `PENDING`, nova prodaja 503 (nič se ne shrani), po zagonu `APPLIED` |
| `archive-reconciliation` | prodaja 5 kosov ob zalogi 2, arhiviranje izdelka, uskladitev, arhiviranje | `NEEDS_RECONCILIATION`, 409 `PRODUCT_HAS_OPEN_RECONCILIATION`, `RESOLVED`, 204 |

Pričakovani izpis (imena in sekunde se razlikujejo):

```text
== refill: Avtomat, izdelek, polnitev: premik REFILL; ista polnitev se ne podvoji.
  avtomat in izdelek 'Demo 0bc64b'
  POST /inventory/refill (5 kosov) -> 201, zaloga 5
  ista polnitev (isti refillId) -> 200, zaloga ostane 5
  premik REFILL: predal A1, kolicina 5, serija DEMO
OK
== repeated-sale: Ponovljena prodaja z istim saleId: ena prodaja, en premik SALE.
  avtomat in izdelek 'Demo 0550f8', predal A1 napolnjen, kolicina 5
  prodaja -> 201
  ista prodaja (isti saleId) -> 200
  isti saleId z drugo vsebino -> 409 SALE_ID_CONFLICT
  prodaj: 1, premikov SALE: 1, zaloga 4
OK
== inventory-down: Inventory ustavljen po sprejemu prodaje: PENDING, po ponovnem zagonu APPLIED.
  avtomat in izdelek 'Demo d33299', predal A1 napolnjen, kolicina 10
  prodaja -> 201, nato docker compose stop inventory-service
  stanje prodaje: PENDING
  nova prodaja med izpadom -> 503, nic se ne shrani (simulator ponovi)
  po 5 s se vedno PENDING, Sales ponavlja zmanjsanje
  docker compose up -d --wait inventory-service
  stanje prodaje: APPLIED po 4 s, premikov SALE te prodaje: 1
OK
== archive-reconciliation: Arhiviranje izdelka z neusklajeno prodajo: 409, po uskladitvi (RESOLVED) 204.
  avtomat in izdelek 'Demo 5e626e', predal A1 napolnjen, kolicina 2
  prodaja 5 kosov ob zalogi 2 -> NEEDS_RECONCILIATION (INSUFFICIENT_STOCK), zaloga 2
  arhiviranje izdelka -> 409 PRODUCT_HAS_OPEN_RECONCILIATION
  PUT /sales/{saleId}/reconciliation (WRITTEN_OFF) -> 200, stanje RESOLVED
  arhiviranje izdelka -> 204, stanje izdelka ARCHIVED
OK
```

Zakaj Inventory ustavimo šele po prodaji: Sales ob sprejemu prodaje preveri predal v Inventory, zato bi prodaja med izpadom dobila 503 in se ne bi shranila. Shranjeno prodajo Inventoryju sporoči outbox Sales (pregled vsako sekundo, ob napaki ponovitev po 5 s, 10 s, 20 s ...). Če je outbox hitrejši od ustavitve, skript izpiše `outbox je bil hitrejsi od ustavitve (APPLIED), poskusim znova` in ponovi z novo prodajo (največ 5-krat). Inventory na koncu vedno znova zažene, tudi ob napaki. Ob napaki skript izpiše `FAIL <scenarij>: <zahteva, pričakovano, dobljeno>` in vrne 1.
