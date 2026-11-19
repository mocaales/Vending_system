# API pogodbe (načrtovano)

Referenca končnih točk. Izhodišče za obseg: [skupina_10.pdf](skupina_10.pdf), ki je starejši od posodobitve zahtev; pri razlikah velja ta dokument. Delovanje: [01-arhitektura.md](01-arhitektura.md). Lastniki: [02-razdelitev-dela.md](02-razdelitev-dela.md). Sprejete odločitve: [02](02-razdelitev-dela.md#sprejete-odločitve).

## 1. Konvencije

| Tema | Pravilo |
|---|---|
| Poti | storitve `/v1/...`; gateway `/api/v1/...` (pot storitve z dodano predpono `/api`). Tabele spodaj navajajo pot storitve; drugačna pot gatewaya je v opombi. |
| Kdo kliče | `OPERATOR` (JWT gatewaya), `MACHINE` (`X-Machine-Key`), `PUBLIC` (brez prijave), ali ime storitve (`X-Service-Token`); `frontend-api` je modul gatewaya in uporablja `GATEWAY_SERVICE_TOKEN` |
| Glave | `X-Trace-Id` povsod; `Authorization: Bearer <accessToken>` za `OPERATOR`; gateway doda `X-Operator-Id` ali `X-Machine-Id` in odstrani enake glave odjemalca |
| Tipi | ID-ji so UUID, `slotCode` je niz (npr. `"A1"`); denar in temperature decimalni niz (`"2.50"`); časi ISO-8601 UTC s `Z`; datumi `YYYY-MM-DD` (UTC; `expiryDate` je lokalni datum Europe/Ljubljana, [01 §6.4](01-arhitektura.md#64-predal-in-polnitev)); enumi `UPPER_SNAKE` |
| Zahteve | neznana polja: 422; omejitve polj so navedene samo pri modelu |
| Seznami | `limit` (privzeto 50, največ 200), `offset`; odgovor `{items, total, limit, offset}` |
| Ponovni poskusi | samo GET in idempotentni klici |
| Brisanje | `DELETE` je logičen umik ali arhivacija (ponovitev 204), razen kjer opomba pove, da je izbris trajen (ponovitev 404) |
| Zdravje | `/health/live`, `/health/ready` v vseh komponentah (v storitvah interno); niso v OpenAPI |

Gateway zavrne vse poti, ki niso naštete. Ključ avtomata velja samo za `POST /api/v1/device/sales` in `POST /api/v1/device/measurements`; operaterski poti `/api/v1/sales` in `/api/v1/measurements` teh dveh POST klicev ne izpostavljata. Prehodno usmerjanje velja samo za vrstice s klicateljem `OPERATOR`; vrstic z imenom storitve ali opombo "interno" gateway nikoli ne usmeri.

Tabele navajajo samo uspešen odgovor in napake, specifične za pot. Poleg njih povsod veljajo še 400, 500, 503, 504; 401 in 403, kadar pot ni `PUBLIC`; 422 `VALIDATION_ERROR`, kadar obstaja telo ali parameter.

Branje med storitvami (`GET` s klicateljem druge storitve) nikoli ne sproži nadaljnjega klica; s tem ni zank (npr. Machine → Inventory → Machine).

## 2. Napake

Telo napake: `message` je kratko angleško besedilo, UI preslika `code` v slovenščino.

```json
{ "code": "SALE_ID_CONFLICT", "message": "...", "traceId": "...", "details": [ { "field": "quantity", "issue": "must be >= 1" } ] }
```

`details` je samo pri `VALIDATION_ERROR`.

| HTTP | Kode | Kdaj |
|---|---|---|
| 400 | `BAD_REQUEST` | neberljiv JSON, napačen parameter ali UUID |
| 401 | `UNAUTHENTICATED` | manjka ali je neveljaven ali preklican žeton ali ključ |
| 401 | `INVALID_CREDENTIALS` | napačna prijava; enak odgovor za neznan e-naslov, napačno geslo in deaktiviran račun |
| 403 | `FORBIDDEN`, `MACHINE_KEY_MISMATCH`, `CALLER_NOT_ALLOWED` | napačna vloga; ključ druge naprave; klicatelj ni dovoljen za pot |
| 404 | `MACHINE_NOT_FOUND`, `PRODUCT_NOT_FOUND`, `SLOT_NOT_FOUND`, `SALE_NOT_FOUND`, `ALERT_NOT_FOUND`, `CATEGORY_NOT_FOUND`, `KEY_NOT_FOUND`, `CLOSING_NOT_FOUND`, `OPERATOR_NOT_FOUND`, `NOT_FOUND` | manjkajoč zapis; neznana pot |
| 409 | `SALE_ID_CONFLICT`, `MEASUREMENT_ID_CONFLICT`, `REFILL_ID_CONFLICT` | isti ID, drugačna vsebina |
| 409 | `MACHINE_CODE_TAKEN`, `CATEGORY_NAME_TAKEN`, `OPERATOR_EMAIL_TAKEN` | podvojena oznaka, ime kategorije ali e-naslov (tudi deaktiviranega operaterja) |
| 409 | `MACHINE_RETIRED`, `PRODUCT_ARCHIVED` | urejanje umaknjenega ali arhiviranega |
| 409 | `INVALID_STATE_TRANSITION`, `ALERT_NOT_ACTIVE`, `ALERT_TYPE_NOT_RESOLVABLE` | nedovoljen prehod stanja; potrditev razrešenega alarma; ročna razrešitev alarma `LOW_STOCK` |
| 409 | `SLOT_HAS_OTHER_BATCH`, `SLOT_NOT_EMPTY` | polnitev ali menjava izdelka v nepraznem predalu |
| 409 | `MACHINE_HAS_STOCK`, `CATEGORY_IN_USE`, `PRODUCT_HAS_OPEN_RECONCILIATION` | umik avtomata z zalogo; izbris kategorije z izdelki; arhivacija izdelka z odprto uskladitvijo |
| 409 | `KEY_LIMIT_REACHED` | avtomat ima že 3 aktivne ključe |
| 409 | `CLOSING_EXISTS`, `CLOSING_CONFIRMED`, `CLOSING_HAS_OPEN_RECONCILIATION` | zaključek za ta dan in obseg že obstaja; sprememba ali izbris potrjenega zaključka; potrditev z neusklajenimi prodajami |
| 409 | `OPERATOR_IS_SELF`, `OPERATOR_LAST_ACTIVE` | deaktivacija ali ponastavitev gesla samega sebe (za geslo: `PUT /auth/password`); deaktivacija zadnjega aktivnega operaterja |
| 422 | `VALIDATION_ERROR`, `REFERENCE_NOT_FOUND`, `TIMESTAMP_OUT_OF_RANGE` | neveljavno polje; neznan avtomat, predal, izdelek ali kategorija; čas več kot 5 min v prihodnosti |
| 429 | `RATE_LIMITED` | javne poti in prijava (glava `Retry-After`) |
| 500, 503, 504 | `INTERNAL_ERROR`, `DEPENDENCY_UNAVAILABLE`, `UPSTREAM_TIMEOUT` | napaka; odvisnost ne odgovarja (odjemalec ponovi); časovna omejitev gatewaya |

## 3. Dogodka

### 3.1 Prodaja (`SaleEvent`, lastnik Sales)

Vsa polja so obvezna. `saleId` ustvari simulator in ga hrani do potrjenega sprejema. `slotId` vsebuje `slotCode` predala. `paymentMethod`: `CASH`, `CARD`, `OTHER` (samo podatek). `quantity` 1–100. `slotId` je `slotCode` predala, 1–8 znakov. `currency` je tri velike črke (ISO 4217). Denar je nenegativen niz z največ 2 decimalkama.

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
| POST | `/machines` | OPERATOR | ni idempotenten; vrne prvi `machineKey` enkrat | 201; 409 `MACHINE_CODE_TAKEN` |
| PUT | `/machines/{id}` | OPERATOR | idempotentna zamenjava | 200; 404, 409 `MACHINE_RETIRED` |
| PUT | `/machines/{id}/status` | OPERATOR | isto stanje: 200 brez spremembe | 200; 404, 409 `INVALID_STATE_TRANSITION`, `MACHINE_RETIRED` |
| DELETE | `/machines/{id}` | OPERATOR | logičen umik; prej `GET /v1/inventory/{machineId}`; umik prekliče vse ključe; ponovitev 204 brez preverjanja | 204; 404, 409 `MACHINE_HAS_STOCK`, 503 |
| GET | `/machines/{id}/keys` | OPERATOR | samo metapodatki, nikoli skrivnost | 200; 404 |
| POST | `/machines/{id}/keys` | OPERATOR | ni idempotenten; nov ključ, stari ostanejo veljavni; vrne `machineKey` enkrat | 201; 404, 409 `MACHINE_RETIRED`, `KEY_LIMIT_REACHED` |
| DELETE | `/machines/{id}/keys/{keyId}` | OPERATOR | preklic, takoj neveljaven; dovoljen tudi za zadnji ključ; ponovitev 204 | 204; 404 `KEY_NOT_FOUND` |
| POST | `/internal/machine-keys/verify` | gateway | telo `{machineKey}`, odgovor `{machineId, status}`; preklican ključ ali umaknjen avtomat: 401; samo interno | 200; 401 |

- `MachineCreate`: `code` (1–32), `name` (1–100), `address` (1–200), `latitude`, `longitude`, `slots[{slotCode, label?}]` (1–100 predalov; `slotCode` 1–8, `label` največ 40). Predali se po ustvarjanju ne spreminjajo. Odgovor `POST /machines`: `MachineOut` + `machineKey`, `keyId`.
- `KeyIn`: `label?` (največ 60). `KeyOut`: `keyId`, `label`, `hint` (zadnji 4 znaki), `status` (`ACTIVE`, `REVOKED`), `createdAt`, `revokedAt?`. `KeyCreated` = `KeyOut` + `machineKey` (samo v odgovoru ustvarjanja).
- Menjava ključa: ustvari nov ključ, nastavi ga v simulatorju, prekliči starega. Aktivnih ključev je največ 3.
- Status: `ACTIVE`, `SERVICE`, `RETIRED` (končno); brez parametra `status` seznam vrne `ACTIVE` in `SERVICE`. Avtomat se umakne le, če je v Inventory vsaka količina 0 ali ni zapisov; zaloga se prej izprazni (`DELETE /inventory/{machineId}/{slot}`).

## 5. Monitoring (Aleš)

| Metoda | Pot | Kdo kliče | Opomba | Odgovor |
|---|---|---|---|---|
| POST | `/measurements` | MACHINE; gateway pot `/api/v1/device/measurements` | idempotentno po `measurementId`: ista vsebina 200 brez ponovne ocene | 201 / 200; 403 `MACHINE_KEY_MISMATCH`, 409 `MEASUREMENT_ID_CONFLICT`, 422 `TIMESTAMP_OUT_OF_RANGE` |
| GET | `/measurements/{machineId}` | OPERATOR, frontend-api | filtra `from`, `to` (največ 7 dni, privzeto 24 h) | 200; 404 |
| DELETE | `/measurements/{machineId}` | OPERATOR | čiščenje; obvezen `before`, starejši od 7 dni; alarmov ne briše; ponovitev 200 z `deleted: 0` | 200 `{deleted}`; 404, 422 |
| GET | `/monitoring/{machineId}/status` | OPERATOR, frontend-api | `reachability`: `ONLINE`, `OFFLINE`, `UNKNOWN`; zadnja meritev | 200; 404 |
| GET | `/alerts` | OPERATOR, frontend-api | filtri `machineId`, `status`, `acknowledged`, `type` | 200 |
| POST | `/alerts` | inventory; samo interno | idempotentno po `version`: starejša ali enaka verzija 200 brez spremembe | 201 / 200; 403 `CALLER_NOT_ALLOWED` |
| PUT | `/alerts/{id}/acknowledgement` | OPERATOR | brez telesa; ponovitev 200 brez spremembe (ohrani prvo potrditev) | 200; 404 `ALERT_NOT_FOUND`, 409 `ALERT_NOT_ACTIVE` |
| PUT | `/alerts/{id}/resolution` | OPERATOR | ročna razrešitev samo za `TEMPERATURE_HIGH`, `DOOR_OPEN`, `OFFLINE`; ne zahteva potrditve; ponovitev na razrešenem 200 brez spremembe | 200; 404 `ALERT_NOT_FOUND`, 409 `ALERT_TYPE_NOT_RESOLVABLE` |
| GET | `/thresholds/{machineId}` | OPERATOR | brez shranjenih vrne privzete (`isDefault: true`) | 200; 404 |
| PUT | `/thresholds/{machineId}` | OPERATOR | idempotentna zamenjava; `resetTempC` mora biti manjši od `maxTempC` | 200; 404, 422 |
| DELETE | `/thresholds/{machineId}` | OPERATOR | ponastavi na privzete; ponovitev 204 | 204; 404 |

- Pragovi (privzeto): `maxTempC` 8.0, `resetTempC` 7.0, `consecutiveReadings` 2, `doorOpenMaxSeconds` 120, `noDataMaxSeconds` 300.
- Poročilo `POST /alerts`: `machineId`, `type: LOW_STOCK`, `version` (naraščajoče po avtomatu), `slots[{slotCode, productId, quantity, lowStockThreshold}]`. Prazen `slots` razreši alarm. `LOW_STOCK` razrešuje samo Inventory.
- `ResolveIn`: `note?` (največ 500). Potrditev nima telesa; `acknowledgedBy` je `X-Operator-Id`.
- Alarm: `status` (`ACTIVE`, `RESOLVED`) in potrditev (`acknowledgedAt`, `acknowledgedBy`) sta ločena. Razrešitev: `resolvedAt`, `resolvedBy?`, `resolution` (`AUTO`, `MANUAL`), `resolutionNote?`. Če pogoj po ročni razrešitvi še traja, ga naslednja ocena odpre kot nov alarm. `notification` (`PENDING`, `SENT`, `FAILED`, `attempts`) kaže stanje e-pošte; napaka e-pošte alarma ne spremeni. Pravila odpiranja: [01 §6.2](01-arhitektura.md#62-alarmi).
- E-pošto alarmov pošlje Monitoring na naslove iz `ALERT_EMAIL_TO` (en ali več, ločenih z vejico; [01 §10](01-arhitektura.md#10-zagon-v-dockerju)).
- E-pošta `LOW_STOCK` vsebuje nazive izdelkov iz `GET /v1/products/{id}`; če Product ne odgovori, vsebuje `productId` in se ne zadrži.

## 6. Product (Nejc)

| Metoda | Pot | Kdo kliče | Opomba | Odgovor |
|---|---|---|---|---|
| GET | `/products` | OPERATOR, frontend-api, inventory, sales | filtri `q`, `categoryId`, `status` (privzeto `ACTIVE`) | 200 |
| GET | `/products/{id}` | kot zgoraj in monitoring | vrne tudi arhivirane | 200; 404 `PRODUCT_NOT_FOUND` |
| POST | `/products` | OPERATOR | ni idempotenten; brez `sku` (nazivi se lahko ponavljajo) | 201; 422 `REFERENCE_NOT_FOUND` (kategorija) |
| PUT | `/products/{id}` | OPERATOR | idempotentna zamenjava; cena ne spremeni zabeleženih prodaj | 200; 404, 409 `PRODUCT_ARCHIVED`, 422 `REFERENCE_NOT_FOUND` |
| DELETE | `/products/{id}` | OPERATOR | arhivacija; prej `GET /v1/sales?productId=&reconciliation=NEEDS_RECONCILIATION&limit=1`; ponovitev 204 brez preverjanja; sme arhivirati izdelek na zalogi ([01 §6.5](01-arhitektura.md#65-izdelek-in-kategorija)) | 204; 404, 409 `PRODUCT_HAS_OPEN_RECONCILIATION`, 503 |
| GET | `/categories` | OPERATOR, frontend-api | filter `q`; razvrščeno po imenu | 200 |
| GET | `/categories/{id}` | kot zgoraj | | 200; 404 `CATEGORY_NOT_FOUND` |
| POST | `/categories` | OPERATOR | ni idempotenten | 201; 409 `CATEGORY_NAME_TAKEN` |
| PUT | `/categories/{id}` | OPERATOR | idempotentna zamenjava | 200; 404, 409 `CATEGORY_NAME_TAKEN` |
| DELETE | `/categories/{id}` | OPERATOR | trajen izbris, samo če nanjo ne kaže noben izdelek (tudi arhiviran); ponovitev 404 | 204; 404, 409 `CATEGORY_IN_USE` |

- `CategoryIn`: `name` (1–60, enolično ne glede na velike in male črke), `description?` (največ 200). `CategoryOut`: `id`, `name`, `description`.
- `ProductIn`: `name` (1–100), `categoryId`, `referencePrice`, `currency` (v prototipu `EUR`), `weightGrams`, `allergens[]`, `imageUrl`. `ProductOut` vrne še `categoryName` in `status`.

## 7. Inventory (Nejc)

Predal je `(machineId, slot)`, kjer je `slot` = `slotCode`. Prazen predal nima izdelka in ima količino 0; prag nizke zaloge se ohrani.

| Metoda | Pot | Kdo kliče | Opomba | Odgovor |
|---|---|---|---|---|
| GET | `/inventory/{machineId}` | OPERATOR, frontend-api, machine | zaloga po predalih; za klicatelja `machine` brez klica v Machine, neznan avtomat vrne prazen seznam | 200; 404 |
| GET | `/inventory/product/{productId}` | OPERATOR, frontend-api | avtomati z izdelkom | 200 |
| GET | `/inventory/{machineId}/{slot}` | OPERATOR, frontend-api, sales | | 200; 404 `SLOT_NOT_FOUND` |
| PUT | `/inventory/{machineId}/{slot}` | OPERATOR | idempotentno; količina vsaj 1; praznjenje je `DELETE` | 200; 404, 409 `SLOT_NOT_EMPTY`, `PRODUCT_ARCHIVED`, 422 `REFERENCE_NOT_FOUND` |
| PUT | `/inventory/{machineId}/{slot}/threshold` | OPERATOR | idempotentna zamenjava praga; nova ocena nizke zaloge | 200; 404 `SLOT_NOT_FOUND` |
| DELETE | `/inventory/{machineId}/{slot}` | OPERATOR | izprazni predal, zapiše `REMOVAL` z odstranjeno količino; že prazen: 204 brez premika | 204; 404 `SLOT_NOT_FOUND` |
| DELETE | `/inventory/expired` | OPERATOR | obvezen `machineId`; izprazni predale, katerih zaloga je potekla (pravilo: [01 §6.4](01-arhitektura.md#64-predal-in-polnitev)), po en `REMOVAL`; ponovitev 200 s praznim seznamom | 200; 404 `MACHINE_NOT_FOUND` |
| POST | `/inventory/{machineId}/{slot}/decrease` | sales; samo interno | idempotentno po `saleId`: isto telo 200 s shranjenim rezultatom | 201 / 200; 403, 404, 409 `SALE_ID_CONFLICT` |
| POST | `/inventory/refill` | OPERATOR | idempotentno po `refillId`: količina se ne podvoji | 201 / 200; 404, 409 `REFILL_ID_CONFLICT`, `SLOT_HAS_OTHER_BATCH`, `PRODUCT_ARCHIVED`, 422 |
| GET | `/inventory/movements` | OPERATOR | filtri `machineId`, `productId`, `type`, `from`, `to` | 200 |

- `SlotSet`: `productId`, `batchCode`, `expiryDate`, `quantity` (1–1000), `lowStockThreshold`. `ThresholdIn`: `lowStockThreshold` (0–1000); odgovor je predal. `RefillIn`: `refillId`, `machineId`, `slot`, `productId`, `batchCode`, `expiryDate`, `quantity`. `DecreaseIn`: `saleId`, `productId`, `quantity`. `ExpiredOut`: `removed[{slot, productId, batchCode, quantity}]`.
- `DecreaseOut`: `result` (`APPLIED`, `NEEDS_RECONCILIATION`), `reason?` (`PRODUCT_MISMATCH`, `INSUFFICIENT_STOCK`), `quantityAfter`. Pri neskladju je odgovor še vedno 201, zaloga se ne zmanjša, količina ni negativna ([01 §5.1](01-arhitektura.md#51-prodaja)).
- Premik (`type`): `SET`, `REFILL`, `REMOVAL`, `SALE`.
- Nizka zaloga: predal z izdelkom in količino do praga je v poročilu `POST /v1/alerts` (outbox). Sprememba praga in praznjenje predala sprožita novo poročilo; predal brez izdelka v poročilu ni.
- Statične poti (`movements`, `refill`, `expired`, `product/...`) so registrirane pred potmi z `{machineId}`.

## 8. Sales (Aleš)

| Metoda | Pot | Kdo kliče | Opomba | Odgovor |
|---|---|---|---|---|
| POST | `/sales` | MACHINE; gateway pot `/api/v1/device/sales` | idempotentno po `saleId`: ista vsebina 200 z obstoječim rezultatom | 201 / 200; 403 `MACHINE_KEY_MISMATCH`, 409 `SALE_ID_CONFLICT`, 422 `REFERENCE_NOT_FOUND`, `TIMESTAMP_OUT_OF_RANGE`, 503 |
| GET | `/sales` | OPERATOR, frontend-api, product | filtri `machineId`, `productId`, `from`, `to`, `reconciliation` | 200 |
| GET | `/sales/{saleId}` | OPERATOR, frontend-api | vključuje stanje uskladitve | 200; 404 `SALE_NOT_FOUND` |
| GET | `/sales/stats` | OPERATOR, frontend-api | `from`, `to`, `top` (privzeto 5, največ 20) | 200 |
| GET | `/sales/stats/{machineId}` | OPERATOR, frontend-api | statistika avtomata | 200 |
| PUT | `/sales/{saleId}/reconciliation` | OPERATOR | `NEEDS_RECONCILIATION` → `RESOLVED`; isto telo na rešeni prodaji 200; drugo telo 409 | 200; 404 `SALE_NOT_FOUND`, 409 `INVALID_STATE_TRANSITION` |
| POST | `/sales/closings` | OPERATOR | ni idempotenten; ustvari `DRAFT` z izračunanimi vsotami | 201; 404 `MACHINE_NOT_FOUND`, 409 `CLOSING_EXISTS`, 422 |
| GET | `/sales/closings` | OPERATOR, frontend-api | filtri `from`, `to` (datum), `machineId`, `status` | 200 |
| GET | `/sales/closings/{id}` | kot zgoraj | | 200; 404 `CLOSING_NOT_FOUND` |
| PUT | `/sales/closings/{id}` | OPERATOR | `DRAFT`: zamenja opombo in preračuna vsote; `status: CONFIRMED` potrdi; isto telo na potrjenem 200 | 200; 404 `CLOSING_NOT_FOUND`, 409 `CLOSING_CONFIRMED`, `CLOSING_HAS_OPEN_RECONCILIATION` |
| DELETE | `/sales/closings/{id}` | OPERATOR | trajen izbris samo `DRAFT`; ponovitev 404 | 204; 404 `CLOSING_NOT_FOUND`, 409 `CLOSING_CONFIRMED` |
| DELETE | `/sales/closings` | OPERATOR | obvezen `date`; izbriše vse `DRAFT` tega dne, potrjene preskoči; ponovitev 200 z `deleted: 0` | 200 `{deleted}`; 422 |

- Statične poti (`stats`, `closings`) so registrirane pred `/sales/{saleId}`.
- `X-Machine-Id` nastavi gateway iz ključa avtomata; `machineId` v telesu mora ustrezati (sicer 403, glej tabelo).
- Ob sprejemu Sales preveri izdelek in predal (neznan: 422). Če Product ali Inventory ne odgovori: 503, nič se ne shrani, simulator ponovi z istim `saleId`.
- Prodaja je nespremenljiva, spreminja se samo stanje uskladitve (`inventory.state`: `PENDING`, `APPLIED`, `NEEDS_RECONCILIATION`, `RESOLVED`). Edini ročni prehod: `NEEDS_RECONCILIATION` → `RESOLVED` (končno); iz `PENDING` in `APPLIED` je 409 `INVALID_STATE_TRANSITION`. Filter `reconciliation` sprejme ta štiri stanja.
- `SaleOut` = polja `SaleEvent` + `receivedAt` (čas sprejema v Sales), `inventory{state, reason?}` (`reason`: `PRODUCT_MISMATCH`, `INSUFFICIENT_STOCK`) in `reconciliation?`.
- `ReconciliationIn`: `resolution` (`ADJUSTED`: zaloga je bila popravljena v Inventory; `WRITTEN_OFF`: razlika se sprejme brez popravka), `note` (1–500). Sales zaloge ne spreminja. `reconciliation?` vsebuje `resolution`, `note`, `resolvedAt`, `resolvedBy`.
- Časovni filtri: pri prodajah in statistiki sta `from`/`to` `date-time` (pri prodajah `from` vključen, `to` izključen); pri zaključkih sta `from`/`to` datuma, oba vključena.
- Statistika avtomata (`GET /sales/stats/{machineId}`) brez prodaj vrne prazen `totals` in `topProducts`, zaključek za tak avtomat ima ničelne zneske; Sales ne kliče Machine.
- `SalesStatsOut`: `totals[{currency, salesCount, quantity, turnover}]`, `topProducts[{productId, quantity, turnover}]`.
- Dnevni zaključek: `ClosingIn` (POST): `date` (UTC dan pred današnjim), `machineId?` (brez: vsi avtomati), `note?` (največ 500). `ClosingUpdate` (PUT): `note?`, `status` (`DRAFT` ali `CONFIRMED`). `ClosingOut`: `id`, `date`, `machineId?`, `status`, `note`, `totals[{currency, salesCount, quantity, turnover}]`, `openReconciliations`, `createdAt`, `createdBy`, `updatedAt`, `confirmedAt?`, `confirmedBy?`.
- En zaključek na par (`date`, `machineId`); vsote so posnetek prodaj z `occurredAt` v tem dnevu. Stanje: `DRAFT` → `CONFIRMED` (končno; potrditev zahteva `openReconciliations` = 0 in še zadnji preračun). Poznejša prodaja potrjenih vsot ne spremeni.

## 9. Gateway in Frontend API (Darko, Frontend)

Gateway ima lastno bazo samo za račune operaterjev; poslovnih zapisov nima. Izda žeton `accessToken` (JWT) in ga preverja; prvi operater nastane ob prvem zagonu iz okolja, javne registracije ni, vloga je samo `OPERATOR`. Vsak odsek pogleda ima `status` (`OK`, `STALE`, `UNAVAILABLE`) in `asOf`; odsek, ki ne odgovori, je `null` z `UNAVAILABLE`.

| Metoda | Pot | Kdo kliče | Opomba | Odgovor |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | PUBLIC | 5 neuspešnih poskusov v 15 min za par naslov IP + e-naslov, nato 429 | 200 `TokenOut`; 401 `INVALID_CREDENTIALS`, 429 |
| DELETE | `/api/v1/auth/session` | OPERATOR | odjava: prekliče vse žetone operaterja; ponovitev z istim žetonom 401 | 204 |
| GET | `/api/v1/auth/me` | OPERATOR | trenutni operater | 200 |
| PUT | `/api/v1/auth/password` | OPERATOR | menja lastno geslo, prekliče stare žetone in vrne nov; napačno trenutno geslo: 422 z `details` | 200 `TokenOut`; 422 |
| GET | `/api/v1/operators` | OPERATOR | filter `status`; razvrščeno po e-naslovu | 200 |
| GET | `/api/v1/operators/{id}` | OPERATOR | | 200; 404 `OPERATOR_NOT_FOUND` |
| POST | `/api/v1/operators` | OPERATOR | ni idempotenten | 201; 409 `OPERATOR_EMAIL_TAKEN` |
| PUT | `/api/v1/operators/{id}` | OPERATOR | idempotentna zamenjava `name`, `email` | 200; 404, 409 `OPERATOR_EMAIL_TAKEN` |
| PUT | `/api/v1/operators/{id}/password` | OPERATOR | ponastavi geslo drugemu operaterju (sebi: `PUT /auth/password`); cilj mora biti `ACTIVE`; zviša njegov `token_version`, zato njegovi obstoječi žetoni dobijo 401 | 204; 404 `OPERATOR_NOT_FOUND`, 409 `OPERATOR_IS_SELF`, 409 `INVALID_STATE_TRANSITION` (cilj ni `ACTIVE`), 422 |
| DELETE | `/api/v1/operators/{id}` | OPERATOR | logična deaktivacija in preklic žetonov; ponovitev 204 | 204; 404, 409 `OPERATOR_IS_SELF`, `OPERATOR_LAST_ACTIVE` |
| GET | `/api/v1/public/machines` | PUBLIC | umaknjeni skriti; `SERVICE` nerazpoložljiv | 200; 429 |
| GET | `/api/v1/public/machines/{id}/availability` | PUBLIC | brez potekle zaloge in praznih predalov; ob nedosegljivem Inventory `UNAVAILABLE`, ne prazen seznam | 200; 404, 429 |
| GET | `/api/v1/operator/dashboard` | OPERATOR | avtomati, aktivni alarmi, današnja prodaja | 200 |
| GET | `/api/v1/operator/machines/{id}/overview` | OPERATOR | avtomat, status, meritve, pragovi, alarmi, predali | 200; 404 |
| * | `/api/v1/machines` (z `/{id}/keys`), `/products`, `/categories`, `/inventory`, `/measurements`, `/monitoring`, `/alerts`, `/thresholds`, `/sales` (z `/closings`) | OPERATOR | prehodno usmerjanje; veljajo pravila ciljne točke | kot cilj; 504 |
| GET | `/health/live`, `/health/ready` | brez | `ready`: dosegljiva baza gatewaya | 200; 503 |

- `LoginIn`: `email` (največ 254), `password` (največ 128). `TokenOut`: `accessToken`, `tokenType` (`Bearer`), `expiresAt`. Žeton velja 8 ur; osvežitve ni.
- `OperatorIn` (POST): `email` (največ 254, enolično ne glede na velike in male črke), `name` (1–100), `password` (12–128). `OperatorUpdate` (PUT): `email`, `name`. `PasswordIn`: `currentPassword`, `newPassword` (12–128). `PasswordResetIn` (PUT `/operators/{id}/password`): `newPassword` (12–128).
- `OperatorOut`: `id`, `email`, `name`, `status` (`ACTIVE`, `DEACTIVATED`), `createdAt`, `deactivatedAt?`. Geslo ali njegov zgoščen zapis ni v nobenem odgovoru.
- Operater: `ACTIVE` → `DEACTIVATED` (končno; e-naslov ostane zaseden). Deaktiviran operater se ne more prijaviti, njegovi žetoni so preklicani. Vsaj en aktiven operater vedno ostane.
- Javni odgovori ne vsebujejo prodaj, alarmov, pragov, meritev, količin, kod serij, ključev ali žetonov. Javni modeli (`PublicMachine`, `PublicAvailability`) so ločeni od internih; pogodbeni test preveri, da imajo samo naštete lastnosti.

## 10. Skupni artefakt

Specifikacije so `backend/<storitev>/openapi.yaml` (machine, monitoring, product, inventory, sales) in `backend/gateway/openapi.yaml`. Storitve jo generirajo iz kode (FastAPI, springdoc) in jo interno strežejo na `/openapi.json`; `backend/export_openapi.py` jo iz tekočih zabojnikov zapiše v `openapi.yaml`, z `--check` pa CI preveri, da sta enaki. Gateway je obraten: `backend/gateway/openapi.yaml` je izvor, po katerem gateway preverja zahteve, in je skupna pogodba za Web UI (tipi odjemalca so generirani iz nje, test v `frontend/` preveri ujemanje). `SaleEvent` in `MeasurementIn` sta komponenti specifikacij Sales, Monitoring in gatewaya (brez ločenih datotek); pogodbeni test preveri, da modeli gatewaya ustrezajo tem komponentam. Lastnik datoteke je lastnik komponente; spremembo pregledajo porabniki.

## 11. Pokritost metod (pogoj predmeta)

Vsaka komponenta ima vsaj dve lastni točki GET, POST, PUT in DELETE. Prehodno usmerjanje gatewaya in poti naprav (`/device/*`) se ne štejejo.

| Komponenta | GET | POST | PUT | DELETE |
|---|---|---|---|---|
| Machine | `/machines`, `/machines/{id}`, `/machines/{id}/keys` | `/machines`, `/machines/{id}/keys`, `/internal/machine-keys/verify` | `/machines/{id}`, `/machines/{id}/status` | `/machines/{id}`, `/machines/{id}/keys/{keyId}` |
| Monitoring | `/measurements/{machineId}`, `/monitoring/{machineId}/status`, `/alerts`, `/thresholds/{machineId}` | `/measurements`, `/alerts` | `/alerts/{id}/acknowledgement`, `/alerts/{id}/resolution`, `/thresholds/{machineId}` | `/thresholds/{machineId}`, `/measurements/{machineId}` |
| Product | `/products`, `/products/{id}`, `/categories`, `/categories/{id}` | `/products`, `/categories` | `/products/{id}`, `/categories/{id}` | `/products/{id}`, `/categories/{id}` |
| Inventory | `/inventory/{machineId}`, `/inventory/product/{productId}`, `/inventory/{machineId}/{slot}`, `/inventory/movements` | `/inventory/{machineId}/{slot}/decrease`, `/inventory/refill` | `/inventory/{machineId}/{slot}`, `/inventory/{machineId}/{slot}/threshold` | `/inventory/{machineId}/{slot}`, `/inventory/expired` |
| Sales | `/sales`, `/sales/{saleId}`, `/sales/stats`, `/sales/stats/{machineId}`, `/sales/closings`, `/sales/closings/{id}` | `/sales`, `/sales/closings` | `/sales/{saleId}/reconciliation`, `/sales/closings/{id}` | `/sales/closings/{id}`, `/sales/closings` |
| Gateway | `/auth/me`, `/operators`, `/operators/{id}`, `/public/machines`, `/public/machines/{id}/availability`, `/operator/dashboard`, `/operator/machines/{id}/overview` | `/auth/login`, `/operators` | `/auth/password`, `/operators/{id}`, `/operators/{id}/password` | `/auth/session`, `/operators/{id}` |
