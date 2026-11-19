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
| `simulator/` | simulator avtomatov | Python 3.12, httpx | predlog Aleš Močnik (B1) |
| `seed.py`, `export_openapi.py`, `smoke_test.py` | začetni podatki prek gatewaya, izvoz in primerjava OpenAPI, preverjanje glavnega toka | Python | skupno |

Struktura komponent: [docs/01 §9](../docs/01-arhitektura.md#9-struktura-komponent). Zagon: [docs/01 §10](../docs/01-arhitektura.md#10-zagon-v-dockerju). Končne točke: [docs/03](../docs/03-api-pogodbe.md).
