# Backend

**Status:** načrtovano (M0). Vsaka komponenta ima svoj `Dockerfile`, `pyproject.toml` in `openapi.yaml` ter se gradi in namešča neodvisno.

| Pot | Vsebina | Lastnik |
|---|---|---|
| `machine-service/` | avtomati, predali, ključi avtomatov | Aleš Močnik |
| `monitoring-service/` | meritve, alarmi, pragovi, e-pošta | Aleš Močnik |
| `sales-service/` | prodaje, statistika, uskladitev z zalogo | Aleš Močnik |
| `product-service/` | katalog izdelkov | Nejc Cekuta |
| `inventory-service/` | zaloga, polnitve, premiki | Nejc Cekuta |
| `gateway/` | API Gateway in Frontend API (brez baze) | Darko Sever |
| `simulator/` | simulator avtomatov | predlog Aleš Močnik (B1) |
| `supabase/` | lokalni Supabase (`config.toml`) | skupno |
| `bootstrap.sql`, `seed.py`, `export_openapi.py` | sheme in vloge, začetni podatki, izvoz OpenAPI | skupno |

Notranja struktura storitve: [docs/01 §9](../docs/01-arhitektura.md#9-struktura-storitve). Končne točke: [docs/03](../docs/03-api-pogodbe.md).
