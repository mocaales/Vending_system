# Vending System

[![CI](https://github.com/mocaales/Vending_system/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/mocaales/Vending_system/actions/workflows/ci-cd.yml)

Mikrostoritveni sistem za operaterja, ki upravlja več prodajnih avtomatov (meso, mleko, jajca). Operater v spletni aplikaciji ureja avtomate, izdelke in polnitve ter spremlja prodajo, temperaturo in alarme. Kupec na javni strani vidi zemljevid avtomatov in razpoložljive izdelke. Avtomate nadomešča simulator. Ob previsoki temperaturi, izgubi povezave ali nizki zalogi sistem odpre alarm in pošlje e-pošto.

Projekt pri predmetu Storitveno usmerjene arhitekture (FERI UM), skupina_10, november 2026.

## Ekipa

| Član | Komponente |
|---|---|
| Aleš Močnik | Machine Service, Monitoring Service, Sales Service |
| Nejc Cekuta | Product Service, Inventory Service |
| Darko Sever | Frontend (Web UI, Frontend API in API Gateway) |

## Struktura repozitorija

| Mapa | Vsebina | Lastnik |
|---|---|---|
| `frontend/` | Web UI (React, TypeScript, Vite) | Darko |
| `backend/` | 5 poslovnih storitev, API Gateway in Frontend API, simulator, skripte (seed, izvoz OpenAPI, smoke test) | Aleš, Nejc, Darko ([backend/README.md](backend/README.md)) |
| `docs/` | dokumentacija in potrjena zasnova | skupno |
| `.github/workflows/` | CI (GitHub Actions) | skupno |

## Dokumentacija

| Dokument | Vsebina |
|---|---|
| [docs/01-arhitektura.md](docs/01-arhitektura.md) | kako sistem deluje: komponente, tokovi, stanja, varnost, zagon v Dockerju |
| [docs/02-razdelitev-dela.md](docs/02-razdelitev-dela.md) | kdo kaj dela, mejniki, demonstracija, odprte odločitve |
| [docs/03-api-pogodbe.md](docs/03-api-pogodbe.md) | API: konvencije, dogodka, končne točke, kode napak |
| [docs/skupina_10.pdf](docs/skupina_10.pdf) | potrjena idejna zasnova (izhodišče obsega); nastala pred posodobitvijo zahtev predmeta, zato pri razlikah velja `docs/01`–`03` (tehnologije, baze, prijava, končne točke) |

## Tehnologije (načrtovano)

| Področje | Izbira |
|---|---|
| Zaledje | Java 21 + Spring Boot 4.1 (Machine, Monitoring, Sales), Python 3.12 + FastAPI (Product, Inventory, simulator), Node.js 24 + Express + TypeScript (gateway) |
| Spletni vmesnik | React, TypeScript, Vite, Leaflet |
| Baze | PostgreSQL 17, ena baza v svojem zabojniku na komponento (šest) |
| Prijava | JWT, ki ga izda gateway; računi operaterjev v bazi gatewaya |
| E-pošta | SMTP (lokalno Mailpit) |
| Zagon | en zabojnik na komponento, Docker Compose, OpenAPI na komponento |

## CI (GitHub Actions)

Potek `CI` ([.github/workflows/ci-cd.yml](.github/workflows/ci-cd.yml)) teče ob PR v `main` in ob pushu v `main`. Komponente se odkrijejo samodejno po datoteki: `pyproject.toml`, `pom.xml` ali `package.json` v `backend/*/` ali `package.json` v `frontend/`; vsak `Dockerfile` v `backend/*/` se gradi kot slika. Dokler kode ni, opravila uspejo z obvestilom.

| Oznaka | lint | build | test |
|---|---|---|---|
| `pyproject.toml` (Python) | `ruff check`, `ruff format --check` | `python -m build` | `pytest --cov` |
| `pom.xml` (Java) | `mvn validate` (Spotless/Checkstyle v pom) | `mvn -DskipTests package` | `mvn verify` (JaCoCo v pom) |
| `package.json` (Node, frontend) | `npm run lint` | `npm run build` | `npm test` |
| `Dockerfile` (vsaka `backend/*/`) | — | `docker build` | — |

Napaka v eni komponenti opravilo ustavi z napako, ki našteje komponente, ki niso uspele. Artefakti: `backend-dist`, `backend-jars`, `backend-node-dist`, `frontend-dist` (build) ter `backend-coverage`, `java-coverage`, `node-coverage`, `frontend-coverage` (test).

`lint`, `build` in `test` so obvezna preverjanja za združitev v `main`. Opravilo `smoke` (`docker compose up --wait`, `backend/export_openapi.py --check`, `backend/smoke_test.py`) je načrtovano; postane obvezno preverjanje, ko bo zeleno. Deploy sledi, ko bo aplikacija obstajala (načrtovano).

## Lokalni zagon (načrtovano)

Ukazi bodo preverjeni, ko bo obstajalo ogrodje projekta.

```bash
cp .env.example .env                          # izpolni gesla baz, JWT_SECRET, žetone (openssl rand -hex 32) in prvega operaterja
docker compose up --build --wait              # 6 komponent, 6 baz, Mailpit, frontend
pip install httpx pyyaml                      # enkrat, za skripte v backend/
set -a; . ./.env; set +a                      # spremenljivke v lupino za skripte
python backend/seed.py                        # začetni podatki prek gatewaya, ključi za simulator
docker compose --profile sim up -d simulator
python backend/smoke_test.py                  # glavni tok od prijave do alarma
```

| Naslov | Namen |
|---|---|
| http://localhost:5173 | Web UI |
| http://localhost:8080 | API Gateway |
| http://localhost:8025 | Mailpit |

Registracije ni; prvega operaterja ustvari gateway iz `BOOTSTRAP_OPERATOR_EMAIL` in `BOOTSTRAP_OPERATOR_PASSWORD`. Tabele ustvarijo migracije vsake komponente ob zagonu (Flyway pri Javi, Alembic pri Pythonu, node-pg-migrate pri gatewayu). Podrobnosti: [docs/01 §10](docs/01-arhitektura.md#10-zagon-v-dockerju).
