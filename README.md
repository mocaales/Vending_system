# Vending System

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
| `backend/` | 5 poslovnih storitev, API Gateway in Frontend API, simulator, lokalni Supabase in skripte | Aleš, Nejc, Darko ([backend/README.md](backend/README.md)) |
| `docs/` | dokumentacija in potrjena zasnova | skupno |

## Dokumentacija

| Dokument | Vsebina |
|---|---|
| [docs/01-arhitektura.md](docs/01-arhitektura.md) | kako sistem deluje: komponente, tokovi, stanja, varnost |
| [docs/02-razdelitev-dela.md](docs/02-razdelitev-dela.md) | kdo kaj dela, mejniki, demonstracija, odprte odločitve |
| [docs/03-api-pogodbe.md](docs/03-api-pogodbe.md) | API: konvencije, dogodka, končne točke, kode napak |
| [docs/skupina_10.pdf](docs/skupina_10.pdf) | potrjena idejna zasnova (izhodišče obsega) |

## Tehnologije (načrtovano)

| Področje | Izbira |
|---|---|
| Zaledje | Python 3.12, FastAPI, SQLAlchemy 2, Alembic, pytest |
| Spletni vmesnik | React, TypeScript, Vite, Leaflet |
| Baza in prijava | PostgreSQL in Auth (Supabase), ena shema na storitev |
| E-pošta | SMTP (lokalno Mailpit) |
| Zagon | en zabojnik na storitev, Docker Compose, OpenAPI |

## Lokalni zagon (načrtovano)

Ukazi bodo preverjeni, ko bo obstajalo ogrodje projekta.

```bash
supabase start --workdir backend                    # Postgres (54322) in Auth (54321)
cp .env.example .env                                # izpolni vrednosti (supabase status --workdir backend)
set -a; . ./.env; set +a                            # spremenljivke v lupino
psql "$SUPABASE_DB_URL" -f backend/bootstrap.sql    # sheme in vloge
docker compose up --build
python backend/seed.py                              # operater in začetni podatki
docker compose --profile sim up simulator
```

| Naslov | Namen |
|---|---|
| http://localhost:5173 | Web UI |
| http://localhost:8080 | API Gateway |
| http://localhost:8025 | Mailpit |

Registracija uporabnikov je v `backend/supabase/config.toml` izklopljena. Tabele ustvari Alembic vsake storitve; migracij Supabase ne uporabljamo.
