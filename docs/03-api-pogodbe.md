# API pogodbe (načrtovano)

Referenca končnih točk. Osnova: `skupina_10.pdf` ([skupina_10.pdf](skupina_10.pdf)). Delovanje: [01-arhitektura.md](01-arhitektura.md). Lastniki: [02-razdelitev-dela.md](02-razdelitev-dela.md). Točke z oznako "predlog" ali B1–B6 še potrdi ekipa.

## 1. Konvencije

| Tema | Pravilo |
|---|---|
| Poti | storitve `/v1/...`; gateway `/api/v1/...` (pot storitve z dodano predpono `/api`). Tabele spodaj navajajo pot storitve; drugačna pot gatewaya je v opombi. |
| Kdo kliče | `OPERATOR` (JWT prek gatewaya), `MACHINE` (`X-Machine-Key`), `PUBLIC` (brez prijave), ali ime storitve (`X-Service-Token`); `frontend-api` je modul gatewaya in uporablja `GATEWAY_SERVICE_TOKEN` |
| Glave | `X-Trace-Id` povsod; gateway doda `X-Operator-Id` ali `X-Machine-Id` in odstrani enake glave odjemalca |
| Tipi | ID-ji so UUID, `slotCode` je niz (npr. `"A1"`); denar in temperature decimalni niz (`"2.50"`); časi ISO-8601 UTC s `Z`; enumi `UPPER_SNAKE` |
| Zahteve | neznana polja: 422 |
| Seznami | `limit` (privzeto 50, največ 200), `offset`; odgovor `{items, total, limit, offset}` |
| Ponovni poskusi | samo GET in idempotentni klici |
| Zdravje | `/health/live`, `/health/ready` v storitvah (interno); gateway `/health` |

Gateway zavrne vse poti, ki niso naštete. Ključ avtomata velja samo za `POST /api/v1/device/sales` in `POST /api/v1/device/measurements`; operaterski poti `/api/v1/sales` in `/api/v1/measurements` teh dveh POST klicev ne izpostavljata. Poti z opombo "interno" gateway nikoli ne usmeri.

Tabele navajajo samo uspešen odgovor in napake, specifične za pot. Poleg njih povsod veljajo še 400, 500, 503, 504; 401 in 403, kadar pot ni `PUBLIC`; 422 `VALIDATION_ERROR`, kadar obstaja telo ali parameter.

## 2. Napake

Telo napake: `message` je kratko angleško besedilo, UI preslika `code` v slovenščino.

```json
{ "code": "SALE_ID_CONFLICT", "message": "...", "traceId": "...", "details": [ { "field": "quantity", "issue": "must be >= 1" } ] }
```

`details` je samo pri `VALIDATION_ERROR`.

| HTTP | Kode | Kdaj |
|---|---|---|
| 400 | `BAD_REQUEST` | neberljiv JSON, napačen parameter ali UUID |
| 401 | `UNAUTHENTICATED` | manjka ali je neveljaven žeton ali ključ |
| 403 | `FORBIDDEN`, `MACHINE_KEY_MISMATCH`, `CALLER_NOT_ALLOWED` | napačna vloga; ključ druge naprave; klicatelj ni dovoljen za pot |
| 404 | `MACHINE_NOT_FOUND`, `PRODUCT_NOT_FOUND`, `SLOT_NOT_FOUND`, `SALE_NOT_FOUND`, `ALERT_NOT_FOUND`, `NOT_FOUND` | manjkajoč zapis; neznana pot |
| 409 | `SALE_ID_CONFLICT`, `MEASUREMENT_ID_CONFLICT`, `REFILL_ID_CONFLICT` | isti ID, drugačna vsebina |
| 409 | `MACHINE_CODE_TAKEN`, `MACHINE_RETIRED`, `PRODUCT_ARCHIVED` | podvojena oznaka; urejanje umaknjenega ali arhiviranega |
| 409 | `INVALID_STATE_TRANSITION`, `ALERT_NOT_ACTIVE` | nedovoljen prehod stanja |
| 409 | `SLOT_HAS_OTHER_BATCH`, `SLOT_NOT_EMPTY` | polnitev ali menjava izdelka v nepraznem predalu |
| 422 | `VALIDATION_ERROR`, `REFERENCE_NOT_FOUND`, `TIMESTAMP_OUT_OF_RANGE` | neveljavno polje; neznan avtomat, predal ali izdelek; čas več kot 5 min v prihodnosti |
| 429 | `RATE_LIMITED` | samo javne poti |
| 500, 503, 504 | `INTERNAL_ERROR`, `DEPENDENCY_UNAVAILABLE`, `UPSTREAM_TIMEOUT` | napaka; odvisnost ne odgovarja (odjemalec ponovi); časovna omejitev gatewaya |

## 3. Dogodka

### 3.1 Prodaja (`SaleEvent`, lastnik Sales)

Vsa polja so obvezna. `saleId` ustvari simulator in ga hrani do potrjenega sprejema. `slotId` vsebuje `slotCode` predala. `paymentMethod`: `CASH`, `CARD`, `OTHER` (samo podatek). `quantity` 1–100.

```json
{"saleId":"6b1f0c2e-5a34-4d7e-9a10-2f6c1b7d8e01","machineId":"0d9c4a52-3b1e-4f6a-8c27-91e5a7b3d410","slotId":"A1","productId":"c3a8f7d4-12ab-4e55-b6c9-5d0e8a1f2b73","quantity":1,"unitPricePaid":"2.50","currency":"EUR","paymentMethod":"CARD","occurredAt":"2026-11-17T08:30:00Z"}
```

### 3.2 Meritev (`MeasurementIn`, lastnik Monitoring)

`machineId` mora ustrezati ključu avtomata. `doorState`: `OPEN`, `CLOSED`.

```json
{"measurementId":"9e2d7b30-8c41-4a6f-b5d2-0a3f6c8e1d92","machineId":"0d9c4a52-3b1e-4f6a-8c27-91e5a7b3d410","observedAt":"2026-11-17T08:30:00Z","temperatureC":"4.2","doorState":"CLOSED"}
```

## 4. Machine (Aleš)

| Metoda | Pot | Kdo kliče | Opomba | Odgovor |
|---|---|---|---|---|
| GET | `/machines` | OPERATOR, inventory, monitoring, frontend-api | filtri `status`, `bbox`, `q` | 200 |
| GET | `/machines/{id}` | kot zgoraj | | 200; 404 |
| POST | `/machines` | OPERATOR | ni idempotenten; vrne `machineKey` enkrat | 201; 409 `MACHINE_CODE_TAKEN` |
| PUT | `/machines/{id}` | OPERATOR | idempotentna zamenjava | 200; 404, 409 `MACHINE_RETIRED` |
| PUT | `/machines/{id}/status` | OPERATOR | isto stanje: 200 brez spremembe | 200; 404, 409 `INVALID_STATE_TRANSITION`, `MACHINE_RETIRED` |
| DELETE | `/machines/{id}` | OPERATOR | logičen umik; ponovitev 204 | 204; 404 |
| POST | `/internal/machine-keys/verify` | gateway | telo `{machineKey}`, odgovor `{machineId, status}`; samo interno | 200; 401 |

- `MachineCreate`: `code`, `name`, `address`, `latitude`, `longitude`, `slots[{slotCode, label?}]`. Predali se po ustvarjanju ne spreminjajo.
- Status: `ACTIVE`, `SERVICE`, `RETIRED` (končno); brez parametra `status` seznam vrne `ACTIVE` in `SERVICE`.

## 5. Monitoring (Aleš)

| Metoda | Pot | Kdo kliče | Opomba | Odgovor |
|---|---|---|---|---|
| POST | `/measurements` | MACHINE; gateway pot `/api/v1/device/measurements` | idempotentno po `measurementId`: ista vsebina 200 brez ponovne ocene | 201 / 200; 403 `MACHINE_KEY_MISMATCH`, 409 `MEASUREMENT_ID_CONFLICT`, 422 `TIMESTAMP_OUT_OF_RANGE` |
| GET | `/measurements/{machineId}` | OPERATOR, frontend-api | filtra `from`, `to` (največ 7 dni, privzeto 24 h) | 200; 404 |
| GET | `/monitoring/{machineId}/status` | OPERATOR, frontend-api | `reachability`: `ONLINE`, `OFFLINE`, `UNKNOWN`; zadnja meritev | 200; 404 |
| GET | `/alerts` | OPERATOR, frontend-api | filtri `machineId`, `status`, `acknowledged`, `type` | 200 |
| POST | `/alerts` | inventory; samo interno | idempotentno po `version`: starejša ali enaka verzija 200 brez spremembe | 201 / 200; 403 `CALLER_NOT_ALLOWED` |
| PATCH | `/alerts/{id}` | OPERATOR | `action`: `ACKNOWLEDGE` ali `RESOLVE`; ponovitev 200 | 200; 404 `ALERT_NOT_FOUND`, 409 `ALERT_NOT_ACTIVE` |
| GET | `/thresholds/{machineId}` | OPERATOR | brez shranjenih vrne privzete (`isDefault: true`) | 200; 404 |
| PUT | `/thresholds/{machineId}` | OPERATOR | idempotentna zamenjava; `resetTempC` mora biti manjši od `maxTempC` | 200; 404, 422 |

- Pragovi (privzeto): `maxTempC` 8.0, `resetTempC` 7.0, `consecutiveReadings` 2, `doorOpenMaxSeconds` 120, `noDataMaxSeconds` 300.
- Poročilo `POST /alerts`: `machineId`, `type: LOW_STOCK`, `version` (naraščajoče po avtomatu), `slots[{slotCode, productId, quantity, lowStockThreshold}]`. Prazen `slots` razreši alarm.
- Alarm: `status` (`ACTIVE`, `RESOLVED`) in potrditev (`acknowledgedAt`, `acknowledgedBy`) sta ločena. `notification` (`PENDING`, `SENT`, `FAILED`, `attempts`) kaže stanje e-pošte; napaka e-pošte alarma ne spremeni. Pravila odpiranja: [01 §6.2](01-arhitektura.md#62-alarmi).

## 6. Product (Nejc)

| Metoda | Pot | Kdo kliče | Opomba | Odgovor |
|---|---|---|---|---|
| GET | `/products` | OPERATOR, frontend-api, inventory, sales | filtri `q`, `category`, `status` (privzeto `ACTIVE`) | 200 |
| GET | `/products/{id}` | kot zgoraj | vrne tudi arhivirane | 200; 404 `PRODUCT_NOT_FOUND` |
| POST | `/products` | OPERATOR | ni idempotenten; `sku`: B3 | 201 |
| PUT | `/products/{id}` | OPERATOR | idempotentna zamenjava; cena ne spremeni zabeleženih prodaj | 200; 404, 409 `PRODUCT_ARCHIVED` |
| DELETE | `/products/{id}` | OPERATOR | arhivacija; ponovitev 204; B6 | 204; 404 |

`ProductIn`: `name`, `category`, `referencePrice`, `currency` (v prototipu `EUR`), `weightGrams`, `allergens[]`, `imageUrl`.

## 7. Inventory (Nejc)

Predal je `(machineId, slot)`, kjer je `slot` = `slotCode`.

| Metoda | Pot | Kdo kliče | Opomba | Odgovor |
|---|---|---|---|---|
| GET | `/inventory/{machineId}` | OPERATOR, frontend-api | zaloga po predalih | 200; 404 |
| GET | `/inventory/product/{productId}` | OPERATOR, frontend-api | avtomati z izdelkom | 200 |
| GET | `/inventory/{machineId}/{slot}` | OPERATOR, frontend-api, sales | | 200; 404 `SLOT_NOT_FOUND` |
| PUT | `/inventory/{machineId}/{slot}` | OPERATOR | idempotentno; količina 0 zapiše `REMOVAL` | 200; 404, 409 `SLOT_NOT_EMPTY`, 422 `REFERENCE_NOT_FOUND` |
| POST | `/inventory/{machineId}/{slot}/decrease` | sales; samo interno | idempotentno po `saleId`: isto telo 200 s shranjenim rezultatom | 201 / 200; 403, 404, 409 `SALE_ID_CONFLICT` |
| POST | `/inventory/refill` | OPERATOR | idempotentno po `refillId`: količina se ne podvoji | 201 / 200; 404, 409 `REFILL_ID_CONFLICT`, `SLOT_HAS_OTHER_BATCH`, 422 |
| GET | `/inventory/movements` | OPERATOR | filtri `machineId`, `productId`, `type`, `from`, `to` | 200 |

- `SlotSet`: `productId`, `batchCode`, `expiryDate`, `quantity`, `lowStockThreshold`. `RefillIn`: `refillId`, `machineId`, `slot`, `productId`, `batchCode`, `expiryDate`, `quantity`. `DecreaseIn`: `saleId`, `productId`, `quantity`.
- `DecreaseOut`: `result` (`APPLIED`, `NEEDS_RECONCILIATION`), `reason?` (`PRODUCT_MISMATCH`, `INSUFFICIENT_STOCK`), `quantityAfter`. Pri neskladju je odgovor še vedno 201, zaloga se ne zmanjša, količina ni negativna ([01 §5.1](01-arhitektura.md#51-prodaja)).
- Premik (`type`): `SET`, `REFILL`, `REMOVAL`, `SALE`.
- Statične poti (`movements`, `refill`, `product/...`) so registrirane pred potmi z `{machineId}`.

## 8. Sales (Aleš)

| Metoda | Pot | Kdo kliče | Opomba | Odgovor |
|---|---|---|---|---|
| POST | `/sales` | MACHINE; gateway pot `/api/v1/device/sales` | idempotentno po `saleId`: ista vsebina 200 z obstoječim rezultatom | 201 / 200; 403 `MACHINE_KEY_MISMATCH`, 409 `SALE_ID_CONFLICT`, 422 `REFERENCE_NOT_FOUND`, `TIMESTAMP_OUT_OF_RANGE`, 503 |
| GET | `/sales` | OPERATOR, frontend-api | filtri `machineId`, `productId`, `from`, `to`, `reconciliation` | 200 |
| GET | `/sales/{saleId}` | kot zgoraj | vključuje stanje uskladitve | 200; 404 `SALE_NOT_FOUND` |
| GET | `/sales/stats` | kot zgoraj | `from`, `to`, `top` (privzeto 5, največ 20); registrirana pred `/sales/{saleId}` | 200 |
| GET | `/sales/stats/{machineId}` | kot zgoraj | statistika avtomata | 200 |
| PATCH | `/sales/{saleId}/reconciliation` | OPERATOR | predlog (B2); idempotentno | 200; 404, 409 `INVALID_STATE_TRANSITION` |

- Ob sprejemu Sales preveri izdelek in predal (neznan: 422). Če Product ali Inventory ne odgovori: 503, nič se ne shrani, simulator ponovi z istim `saleId`.
- Prodaja je nespremenljiva, spreminja se samo stanje uskladitve (`inventory.state`: `PENDING`, `APPLIED`, `NEEDS_RECONCILIATION`).
- `SalesStatsOut`: `totals[{currency, salesCount, quantity, turnover}]`, `topProducts[{productId, quantity, turnover}]`.

## 9. Gateway in Frontend API (Darko, Frontend)

Brez lastnih poslovnih zapisov. Vsak odsek pogleda ima `status` (`OK`, `STALE`, `UNAVAILABLE`) in `asOf`; odsek, ki ne odgovori, je `null` z `UNAVAILABLE`.

| Metoda | Pot | Kdo kliče | Opomba | Odgovor |
|---|---|---|---|---|
| GET | `/api/v1/public/machines` | PUBLIC | umaknjeni skriti; `SERVICE` nerazpoložljiv | 200; 429 |
| GET | `/api/v1/public/machines/{id}/availability` | PUBLIC | brez potekle zaloge in praznih predalov; ob nedosegljivem Inventory `UNAVAILABLE`, ne prazen seznam | 200; 404, 429 |
| GET | `/api/v1/operator/dashboard` | OPERATOR | avtomati, aktivni alarmi, današnja prodaja | 200 |
| GET | `/api/v1/operator/machines/{id}/overview` | OPERATOR | avtomat, status, meritve, pragovi, alarmi, predali | 200; 404 |
| * | `/api/v1/machines`, `/products`, `/inventory`, `/measurements`, `/monitoring`, `/alerts`, `/thresholds`, `/sales` | OPERATOR | prehodno usmerjanje; veljajo pravila ciljne točke | kot cilj; 504 |
| GET | `/health` | brez | | 200; 503 |

Javni odgovori ne vsebujejo prodaj, alarmov, pragov, meritev, količin, kod serij, ključev ali žetonov. Javni modeli (`PublicMachine`, `PublicAvailability`) so ločeni od internih; pogodbeni test preveri, da imajo samo naštete lastnosti.

## 10. Skupni artefakt

Specifikacije so `backend/<storitev>/openapi.yaml` (machine, monitoring, product, inventory, sales) in `backend/gateway/openapi.yaml`. Izvozijo se iz FastAPI z `backend/export_openapi.py` in se potrdijo v repozitorij; pregledovalec pull requesta preveri, da se izvoz ujema s kodo. `SaleEvent` in `MeasurementIn` sta komponenti specifikacij Sales, Monitoring in gatewaya (brez ločenih datotek); pogodbeni test preveri, da modeli gatewaya ustrezajo tem komponentam. Lastnik datoteke je lastnik storitve; spremembo pregledajo porabniki.
