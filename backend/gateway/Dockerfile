FROM node:24-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

FROM node:24-alpine AS runner
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY --from=builder /app/dist ./dist
COPY migrations/ ./migrations/
COPY openapi.yaml ./

RUN addgroup -S app && adduser -S app -G app
USER app

EXPOSE 8000
CMD ["node", "dist/server.js"]
