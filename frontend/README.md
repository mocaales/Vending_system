# Frontend

Web UI: React, TypeScript, Vite. Javni zemljevid avtomatov (Leaflet) in operaterska nadzorna plošča. Prijava in vsi podatki prek gatewaya (`backend/gateway/`); žeton velja 8 ur, osvežitve ni.

- Tipi odjemalca iz `backend/gateway/openapi.yaml`
- Okolje: `VITE_API_URL` (vidna v brskalniku, brez skrivnosti)
- Zabojnik: večstopenjska gradnja, statične datoteke streže nginx (`frontend/Dockerfile`)
- Struktura in pravila: [docs/01](../docs/01-arhitektura.md#9-struktura-komponent)
