# Vending System – Web UI

React + TypeScript + Vite spletni vmesnik za sistem upravljanja prodajnih avtomatov.

## Ukazi

```bash
npm ci            # namesti odvisnosti
npm run dev       # razvojni strežnik na http://localhost:5173
npm run build     # produkcijska gradnja v dist/
npm run lint      # ESLint + Prettier preveritev
npm run lint:fix  # samodejni popravki
npm test          # Vitest testi
```

## Konfiguracija

`VITE_API_URL` kaže na gateway (privzeto `http://localhost:8080`). Vite bere `.env` iz korenskega imenika repozitorija (`envDir: '..'`). V kodi ni skrivnosti.

## Več

- Tipi odjemalca iz `backend/gateway/openapi.yaml`
- Zabojnik: večstopenjska gradnja, statične datoteke streže `nginx-unprivileged:alpine` na vratih 5173
- Struktura: [docs/01](../docs/01-arhitektura.md#9-struktura-komponent)
