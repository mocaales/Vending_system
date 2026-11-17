# Frontend

Web UI: React, TypeScript, Vite. Javni zemljevid avtomatov (Leaflet) in operaterska nadzorna plošča. Prijava neposredno v Supabase Auth, vsi podatki prek gatewaya (`backend/gateway/`).

- Tipi odjemalca iz `backend/gateway/openapi.yaml`
- Okolje: `VITE_API_URL`, `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY` (vidne v brskalniku, brez skrivnosti)
- Struktura in pravila: [docs/01](../docs/01-arhitektura.md#9-struktura-storitve)
