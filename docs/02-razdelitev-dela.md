# Razdelitev dela (načrtovano)

Kdo kaj dela in kdaj. Delovanje: [01-arhitektura.md](01-arhitektura.md). Končne točke: [03-api-pogodbe.md](03-api-pogodbe.md). Aleš razvije tri storitve, Nejc dve, Darko pa Frontend (Web UI, Frontend API in API Gateway); pogoj predmeta (vsaj dve storitvi na člana, razvijalec Frontend API vsaj eno) je izpolnjen.

## Aleš Močnik: Machine, Monitoring, Sales (predlog: simulator, B1)

| Področje | Vsebina |
|---|---|
| Koda | `backend/machine-service/`, `backend/monitoring-service/`, `backend/sales-service/`, `backend/simulator/` (B1) |
| Končne točke Machine | `/machines` (GET, POST), `/machines/{id}` (GET, PUT, DELETE), `PUT /machines/{id}/status`, interno preverjanje ključa avtomata |
| Končne točke Monitoring | `POST /measurements`, `GET /measurements/{machineId}`, `GET /monitoring/{machineId}/status`, `/alerts` (GET, POST), `PATCH /alerts/{id}`, `/thresholds/{machineId}` (GET, PUT) |
| Končne točke Sales | `POST /sales`, `GET /sales`, `GET /sales/{saleId}`, `GET /sales/stats`, `GET /sales/stats/{machineId}`; B2: `PATCH /sales/{saleId}/reconciliation` |
| Podatki | avtomati, predali, ključi; meritve, alarmi, pragovi, e-poštni outbox; prodaje, outbox zmanjšanj |
| Opravila | preverjanje `OFFLINE` in `DOOR_OPEN`, pošiljanje e-pošte, pošiljanje `decrease` Inventory |
| Od drugih | Inventory pošilja `LOW_STOCK`; Product (`GET /products/{id}`) in Inventory (predal, `decrease`) za Sales |
| Drugi potrebujejo | pogodba Machine (M1-1), pogodba `POST /alerts` in shema meritve (M1-4), shema dogodka prodaje (M1-5, simulator) |

## Nejc Cekuta: Product, Inventory

| Področje | Vsebina |
|---|---|
| Koda | `backend/product-service/`, `backend/inventory-service/` |
| Končne točke Product | `/products` (GET, POST), `/products/{id}` (GET, PUT, DELETE) |
| Končne točke Inventory | `GET /inventory/{machineId}`, `GET /inventory/product/{productId}`, `/inventory/{machineId}/{slot}` (GET, PUT), `POST /inventory/{machineId}/{slot}/decrease`, `POST /inventory/refill`, `GET /inventory/movements` |
| Podatki | izdelki; zaloga predalov, serije, premiki, outbox nizke zaloge |
| Opravila | pošiljanje `LOW_STOCK` Monitoringu |
| Od drugih | Machine (avtomat, predali), Monitoring (`POST /alerts`) |
| Drugi potrebujejo | `GET /products/{id}` (M1-2), branje predala in `decrease` (M1-3) |

## Darko Sever: Frontend (Web UI, Frontend API in API Gateway)

| Področje | Vsebina |
|---|---|
| Koda | `frontend/`, `backend/gateway/` |
| Končne točke gateway | `GET /public/machines`, `GET /public/machines/{id}/availability`, `GET /operator/dashboard`, `GET /operator/machines/{id}/overview`, `POST /device/sales`, `POST /device/measurements`, zaščitene prehodne poti, `GET /health` |
| Web UI | javni zemljevid, prijava, nadzorna plošča, urejanje avtomatov, izdelkov, zaloge, alarmov in pragov; prikaz `STALE` in `UNAVAILABLE` |
| Podatki | brez lastne baze (gateway ne hrani poslovnih zapisov) |
| Od drugih | pogodbe vseh petih storitev, preverjanje ključa avtomata, Supabase Auth |
| Drugi potrebujejo | modeli pogledov Frontend API (M1-6, Web UI) |

## Skupna pravila

| Tema | Pravilo |
|---|---|
| Končano je, ko | pytest (enotski in integracijski testi), Ruff brez napak, izvožen `openapi.yaml` v mapi komponente, zabojnik v Compose, `/health/live` in `/health/ready`, preverjanje klicateljev na vsaki poti, brez skrivnosti v kodi |
| Pogodbe | sprememba `openapi.yaml` potrebuje pregled porabnikov |
| Veje in commiti | veje `<storitev>/<tema>`, Conventional Commits |
| Pull request | edina pot v `main` (ruleset: brez direktnega pusha in force pusha); pregled drugega člana je priporočen, komentarji morajo biti razrešeni |
| Potek dela | issue na [GitHub Project](https://github.com/users/mocaales/projects/9) → veja → PR z `Closes #<issue>` → squash merge; stolpci To Do, Doing, Done; PR je mogoče združiti le, ko `lint`, `build` in `test` uspejo |
| Skupno | `docker-compose.yml`, `backend/supabase/`, `backend/bootstrap.sql`, `backend/seed.py`, `backend/export_openapi.py`, `docs/`, `README.md`, `.env.example`, `.gitignore`, `.gitattributes` |

## Mejniki

| Mejnik | Rezultat | Nosilec |
|---|---|---|
| M0 | skupno ogrodje: predloga `core/`, `backend/bootstrap.sql`, `docker-compose.yml`, `backend/supabase/config.toml` | Aleš, pregled vsi |
| M1 | pogodbe po vrsti: 1 Machine, 2 Product GET, 3 Inventory predal + `decrease`, 4 `POST /alerts` + meritev, 5 dogodek prodaje, 6 modeli pogledov | 1, 4, 5 Aleš; 2, 3 Nejc; 6 Darko |
| M2 | CRUD in stanja vsake storitve z enotskimi testi (odvisnosti oponašane) | vsak svoje |
| M3 | prijava in prehodne poti na gatewayu; simulator | Darko; simulator B1 |
| M4 | prodajni tok od konca do konca | Aleš, Nejc (gateway: Darko) |
| M5 | opravila Monitoringa, e-pošta, nadzorna plošča, javni zemljevid | Aleš; Darko |
| M6 | zagon iz čistega okolja, ločena ponovna namestitev storitve | vsi |

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

## Odprte odločitve

Predlogi; potrdi ekipa.

| ID | Vprašanje | Predlog |
|---|---|---|
| B1 | Kdo razvije simulator | Aleš |
| B2 | Zaprtje ročne uskladitve prodaje | `PATCH /v1/sales/{saleId}/reconciliation` ali izpustiti |
| B3 | Unikaten `sku` izdelka | odločiti: unikaten `sku` ali dovoljeni podvojeni nazivi |
| B4 | Datum za potekla zalogo | lokalni datum Europe/Ljubljana |
| B5 | Prejemniki e-pošte alarmov | spremenljivka okolja `ALERT_EMAIL_TO` |
| B6 | Arhiviranje izdelka, ki je še na zalogi | dovoljeno; Inventory ni obveščen |

## Dodatki h konceptu

Koncept jih ne določa; načrt jih dodaja.

- Poti naprav `POST /api/v1/device/sales` in `/device/measurements`; predpona `/v1` in `/api/v1`.
- Outbox v Inventory za `LOW_STOCK`, da preživi nedosegljiv Monitoring.
- Odstranitev serije: `PUT /inventory/{m}/{slot}` s količino 0 zapiše `REMOVAL`.
- Predali so po ustvarjanju avtomata fiksni.
- Prijava: brskalnik neposredno v Supabase Auth, gateway samo preverja JWT.
