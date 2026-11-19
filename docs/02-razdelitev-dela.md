# Razdelitev dela (načrtovano)

Kdo kaj dela in kdaj. Delovanje: [01-arhitektura.md](01-arhitektura.md). Končne točke: [03-api-pogodbe.md](03-api-pogodbe.md). Aleš razvije tri storitve (Java, Spring Boot), Nejc dve storitve (Python, FastAPI), simulator in pomožne skripte ter del Web UI, Darko pa preostali Web UI, Frontend API in API Gateway (Express). Pogoja predmeta (vsaj dve storitvi na člana, razvijalec Frontend API vsaj eno; vsaj tri ogrodja) sta izpolnjena.

## Aleš Močnik: Machine, Monitoring, Sales, Compose

| Področje | Vsebina |
|---|---|
| Koda | `backend/machine-service/`, `backend/monitoring-service/`, `backend/sales-service/`; Java 21, Spring Boot 4.1, Maven |
| Zagon | `docker-compose.yml`, `.env.example` (lastnik datoteke Aleš; vsak član doda svojo storitev) |
| Končne točke Machine | `/machines` (GET, POST), `/machines/{id}` (GET, PUT, DELETE), `PUT /machines/{id}/status`, `/machines/{id}/keys` (GET, POST), `DELETE /machines/{id}/keys/{keyId}`, interno preverjanje ključa avtomata |
| Končne točke Monitoring | `POST /measurements`, `/measurements/{machineId}` (GET, DELETE), `GET /monitoring/{machineId}/status`, `/alerts` (GET, POST), `PUT /alerts/{id}/acknowledgement`, `PUT /alerts/{id}/resolution`, `/thresholds/{machineId}` (GET, PUT, DELETE) |
| Končne točke Sales | `POST /sales`, `GET /sales`, `GET /sales/{saleId}`, `GET /sales/stats`, `GET /sales/stats/{machineId}`, `PUT /sales/{saleId}/reconciliation`, `/sales/closings` (GET, POST, DELETE), `/sales/closings/{id}` (GET, PUT, DELETE) |
| Podatki | tri baze: avtomati, predali, ključi; meritve, alarmi, pragovi, e-poštni outbox; prodaje, dnevni zaključki, outbox zmanjšanj; migracije Flyway |
| Opravila | preverjanje `OFFLINE` in `DOOR_OPEN`, pošiljanje e-pošte, pošiljanje `decrease` Inventory |
| Od drugih | Inventory: `LOW_STOCK`, zaloga avtomata (umik), predal in `decrease` (Sales); Product: `GET /products/{id}` (Sales, e-pošta Monitoringa) |
| Drugi potrebujejo | pogodba Machine (M1-1), pogodba `POST /alerts` in shema meritve (M1-4), shema dogodka prodaje (M1-5, simulator), `GET /sales` s filtrom `reconciliation` (Product) |

## Nejc Cekuta: Product, Inventory, simulator, skripte, del Web UI

| Področje | Vsebina |
|---|---|
| Koda | `backend/product-service/`, `backend/inventory-service/`; Python 3.12, FastAPI (Pydantic v2, SQLAlchemy 2, psycopg 3, httpx); predloga `core` za Python |
| Simulator | `backend/simulator/`; Python 3.12, httpx; emulira avtomate prek gatewaya |
| Skripte | `backend/seed.py`, `backend/export_openapi.py` |
| Web UI | urejanje izdelkov in kategorij; zaloga (pregled, polnitev, premiki, odpis) |
| Končne točke Product | `/products` (GET, POST), `/products/{id}` (GET, PUT, DELETE), `/categories` (GET, POST), `/categories/{id}` (GET, PUT, DELETE) |
| Končne točke Inventory | `GET /inventory/{machineId}`, `GET /inventory/product/{productId}`, `/inventory/{machineId}/{slot}` (GET, PUT, DELETE), `PUT /inventory/{machineId}/{slot}/threshold`, `POST /inventory/{machineId}/{slot}/decrease`, `POST /inventory/refill`, `GET /inventory/movements`, `DELETE /inventory/expired` |
| Podatki | dve bazi: izdelki, kategorije; zaloga predalov, serije, premiki, outbox nizke zaloge; migracije Alembic |
| Opravila | pošiljanje `LOW_STOCK` Monitoringu |
| Od drugih | Machine (avtomat, predali), Monitoring (`POST /alerts`), Sales (`GET /sales`, odprte uskladitve), gateway (simulator, Web UI) |
| Drugi potrebujejo | `GET /products/{id}` (M1-2), branje predala in `decrease` (M1-3), `GET /inventory/{machineId}` (Machine) |

## Darko Sever: Frontend (Web UI, Frontend API in API Gateway)

| Področje | Vsebina |
|---|---|
| Koda | `frontend/`, `backend/gateway/`; Node.js 24, Express, TypeScript |
| CI | opravilo `smoke` (Compose iz praznih baz, izvoz OpenAPI, smoke test) |
| Končne točke gateway | `POST /auth/login`, `GET /auth/me`, `PUT /auth/password`, `DELETE /auth/session`, `/operators` (GET, POST), `/operators/{id}` (GET, PUT, DELETE), `PUT /operators/{id}/password`, `GET /public/machines`, `GET /public/machines/{id}/availability`, `GET /operator/dashboard`, `GET /operator/machines/{id}/overview`, `POST /device/sales`, `POST /device/measurements`, zaščitene prehodne poti, `/health/live`, `/health/ready` |
| Web UI | javni zemljevid, prijava, nadzorna plošča, urejanje avtomatov s ključi, alarmov in pragov, operaterjev (vključno z ponastavitvijo gesla), dnevnih zaključkov in uskladitev; prikaz `STALE` in `UNAVAILABLE`. Izdelki, kategorije in zaloga so v Nejčevem delu Web UI |
| Podatki | lastna baza z računi operaterjev; poslovnih zapisov gateway ne hrani |
| Od drugih | pogodbe vseh petih storitev, preverjanje ključa avtomata |
| Drugi potrebujejo | modeli pogledov Frontend API (M1-6), `backend/gateway/openapi.yaml` (Web UI) |

## Skupna pravila

| Tema | Pravilo |
|---|---|
| Končano je, ko | testi (pytest, JUnit, Vitest; baza v Testcontainers) uspejo, lint (Ruff, Spotless, ESLint) je brez napak, `openapi.yaml` v mapi komponente je enak `/openapi.json` (`backend/export_openapi.py --check`), zabojnik z lastno bazo teče v Compose, `/health/live` in `/health/ready` delujeta, vsaka pot preverja klicatelja, v kodi ni skrivnosti |
| Pogodbe | sprememba `openapi.yaml` potrebuje pregled porabnikov |
| Veje in commiti | veje `<storitev>/<tema>`, Conventional Commits |
| Pull request | edina pot v `main` (ruleset: brez direktnega pusha in force pusha); pregled drugega člana je priporočen, komentarji morajo biti razrešeni |
| Potek dela | issue na [GitHub Project](https://github.com/users/mocaales/projects/9) → veja → PR z `Closes #<issue>` → squash merge; stolpci To Do, Doing, Done; PR je mogoče združiti le, ko `lint`, `build` in `test` uspejo |
| Lastniki skupnih datotek | `docker-compose.yml` Aleš; `backend/seed.py`, `backend/export_openapi.py`, `backend/smoke_test.py`, `backend/simulator/` Nejc; opravilo `smoke` v CI Darko |
| Skupno | `.github/workflows/`, `docs/`, `README.md`, `.env.example`, `.gitignore`, `.gitattributes` |

### Delo brez čakanja

Nihče ne čaka na drugega člana. Vsaka storitev se razvija po pogodbah v [03](03-api-pogodbe.md) in po `openapi.yaml` lastnika, ko ta obstaja; druge storitve se medtem oponašajo. Integracija poteka ob združevanju v `main`. Vsak član doda svojo storitev v `docker-compose.yml`, ko je njen skelet pripravljen. Priporočen vrstni red je polje »Vrstni red« na projektni tabli; med issue ni blokirajočih povezav.

## Mejniki

| Mejnik | Rezultat | Nosilec |
|---|---|---|
| M0 | skupno ogrodje: predloga `core` po tehnologiji, `docker-compose.yml` s šestimi bazami, `.env.example`, CI za Java, Python in Node | Aleš (Java, Compose, CI), Nejc (Python, predloga `core`), Darko (Node); pregled vsi |
| M1 | pogodbe po vrsti: 1 Machine, 2 Product GET, 3 Inventory predal + `decrease`, 4 `POST /alerts` + meritev, 5 dogodek prodaje, 6 modeli pogledov | 1, 4, 5 Aleš; 2, 3 Nejc; 6 Darko |
| M2 | CRUD in stanja vsake storitve z enotskimi testi (odvisnosti oponašane), tudi ključi, kategorije, odpis in dnevni zaključki | vsak svoje |
| M3 | prijava, računi operaterjev in prehodne poti na gatewayu; simulator | Darko; simulator Nejc |
| M4 | prodajni tok od konca do konca; `backend/smoke_test.py` | Aleš, Nejc (gateway: Darko) |
| M5 | opravila Monitoringa, e-pošta, nadzorna plošča, javni zemljevid, upravljanje operaterjev in zaključkov | Aleš; Darko |
| M6 | zagon iz čistega okolja (`docker compose up --wait` in smoke test), ločena ponovna namestitev storitve | vsi |

## Demonstracija

| Scenarij | Nosilec | Dokaz |
|---|---|---|
| avtomat, izdelek, polnitev | Aleš, Nejc, UI Darko | premik `REFILL` |
| nakup s statistiko | Aleš, UI Darko | `GET /sales/stats` |
| ponovljena prodaja | Aleš, Nejc | ena prodaja, en premik `SALE` |
| nedosegljiv Inventory | Aleš, Nejc | `PENDING`, nato `APPLIED` |
| temperaturni alarm | Aleš | `TEMPERATURE_HIGH`, e-pošta v Mailpit |
| izguba in obnovitev povezave | Aleš | `OFFLINE` odprt in razrešen |
| odpoved e-pošte | Aleš | ponovni poskusi, pošiljanje po obnovitvi SMTP |
| tuj ključ avtomata | Darko, Aleš | 403 `MACHINE_KEY_MISMATCH` |
| umik avtomata z zalogo | Aleš, Nejc | 409 `MACHINE_HAS_STOCK` |
| arhiviranje izdelka z neskladjem, nato uskladitev | Nejc, Aleš | 409 `PRODUCT_HAS_OPEN_RECONCILIATION`, po `RESOLVED` 204 |
| prijava in odjava operaterja | Darko | po odjavi stari žeton 401 |
| zagon iz čistega okolja | vsi | `backend/smoke_test.py` uspe |

## Sprejete odločitve

Sprejela ekipa 2026-11-19.

| ID | Odločitev |
|---|---|
| B1 | Simulator razvija Nejc (Python 3.12, httpx). |
| B2 | Zaprtje uskladitve prodaje je `PUT /sales/{saleId}/reconciliation`. |
| B3 | Izdelek nima polja `sku` (prototip); nazivi izdelkov se lahko ponavljajo. |
| B4 | Zaloga je potekla, ko je lokalni datum v Europe/Ljubljana po `expiryDate`; izdelek velja do konca dneva poteka. Pravilo velja za javno razpoložljivost in za `DELETE /inventory/expired` ([01 §6.4](01-arhitektura.md#64-predal-in-polnitev)). |
| B5 | Prejemniki e-pošte alarmov so v `ALERT_EMAIL_TO`: en ali več naslovov, ločenih z vejico. |
| B6 | Izdelek na zalogi se sme arhivirati; zaloga v predalih se proda do praznjenja. Nastavitev predala ali polnitev z arhiviranim izdelkom: 409 `PRODUCT_ARCHIVED`. Web UI pred arhiviranjem prikaže število predalov z izdelkom ([01 §6.5](01-arhitektura.md#65-izdelek-in-kategorija)). |

## Dodatki h konceptu

Koncept jih ne določa; načrt jih dodaja.

- Poti naprav `POST /api/v1/device/sales` in `/device/measurements`; predpona `/v1` in `/api/v1`.
- Outbox v Inventory za `LOW_STOCK`, da preživi nedosegljiv Monitoring.
- Predali so po ustvarjanju avtomata fiksni.
- Prijava: gateway izda JWT in hrani račune operaterjev v svoji bazi; vsaka komponenta ima svojo bazo PostgreSQL v Dockerju.
- Zaprtje uskladitve prodaje: `PUT /sales/{saleId}/reconciliation`.
- Ponastavitev gesla drugemu operaterju: `PUT /api/v1/operators/{id}/password`.
- Za zahteve predmeta (vsaj dvakrat GET, POST, PUT, DELETE na komponento; vsaka storitev kliče storitev drugega člana) so dodani: ključi avtomatov, kategorije izdelkov, dnevni zaključki, ločena potrditev in razrešitev alarma, praznjenje predala in odpis potekle zaloge, čiščenje meritev, ponastavitev pragov, računi operaterjev, preverjanji pred umikom avtomata in arhiviranjem izdelka ter nazivi izdelkov v e-pošti. Podrobnosti: [03](03-api-pogodbe.md), [01 §11](01-arhitektura.md#11-zahteve-predmeta-in-vzorci).
