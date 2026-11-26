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
