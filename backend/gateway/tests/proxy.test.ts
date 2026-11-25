import { describe, it, expect, beforeAll, afterAll } from "vitest";
import supertest from "supertest";
import express from "express";
import { createServer, Server } from "http";
import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { Pool } from "pg";
import bcrypt from "bcrypt";
import { createPool } from "../src/core/db";
import { createApp } from "../src/app";

let container: StartedPostgreSqlContainer;
let db: Pool;
let request: ReturnType<typeof supertest>;
let mockServer: Server;
let mockPort: number;
let token: string;

beforeAll(async () => {
  // Start mock upstream
  const mock = express();
  mock.use(express.json());
  mock.get("/v1/machines", (_req, res) => res.json({ items: [], total: 0, limit: 50, offset: 0 }));
  mock.get("/v1/machines/slow", (_req, res) => setTimeout(() => res.json({}), 60000)); // before :id
  mock.get("/v1/machines/:id", (_req, res) => res.json({ id: "123", name: "Test" }));
  mock.post("/v1/machines", (_req, res) => res.status(201).json({ id: "new" }));
  mock.get("/v1/products", (_req, res) => res.json({ items: [] }));
  mock.get("/v1/sales", (_req, res) => res.json({ items: [] }));

  mockServer = createServer(mock);
  await new Promise<void>((resolve) => mockServer.listen(0, resolve));
  mockPort = (mockServer.address() as { port: number }).port;

  // Point all services at mock
  process.env.MACHINE_URL = `http://localhost:${mockPort}`;
  process.env.PRODUCT_URL = `http://localhost:${mockPort}`;
  process.env.INVENTORY_URL = `http://localhost:${mockPort}`;
  process.env.MONITORING_URL = `http://localhost:${mockPort}`;
  process.env.SALES_URL = `http://localhost:${mockPort}`;
  process.env.PROXY_TIMEOUT_MS = "500";

  // Start DB + gateway
  container = await new PostgreSqlContainer("postgres:17-alpine").start();
  db = createPool(container.getConnectionUri());

  await db.query(`
    CREATE TYPE operator_status AS ENUM ('ACTIVE', 'INACTIVE');
    CREATE TABLE operators (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      email TEXT NOT NULL UNIQUE,
      name TEXT NOT NULL,
      password_hash TEXT NOT NULL,
      status operator_status NOT NULL DEFAULT 'ACTIVE',
      token_version INTEGER NOT NULL DEFAULT 0,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      deactivated_at TIMESTAMPTZ
    );
  `);

  const hash = await bcrypt.hash("password-123", 4);
  await db.query("INSERT INTO operators (email, name, password_hash) VALUES ($1,$2,$3)", [
    "proxy@test.com",
    "Proxy Tester",
    hash,
  ]);

  const app = createApp(db);
  request = supertest(app);

  const loginRes = await supertest(app)
    .post("/api/v1/auth/login")
    .send({ email: "proxy@test.com", password: "password-123" });
  token = loginRes.body.accessToken;
});

afterAll(async () => {
  await db.end();
  await container.stop();
  await new Promise<void>((resolve) => mockServer.close(() => resolve()));
});

const auth = (chain: ReturnType<ReturnType<typeof supertest>["get"]>) =>
  chain.set("Authorization", `Bearer ${token}`);

describe("Proxy routing", () => {
  it("forwards GET /api/v1/machines to machine-service", async () => {
    const res = await auth(request.get("/api/v1/machines"));
    expect(res.status).toBe(200);
    expect(res.body.items).toBeDefined();
  });

  it("forwards GET /api/v1/machines/:id", async () => {
    const res = await auth(request.get("/api/v1/machines/123"));
    expect(res.status).toBe(200);
    expect(res.body.id).toBe("123");
  });

  it("forwards POST /api/v1/machines", async () => {
    const res = await auth(request.post("/api/v1/machines")).send({ name: "New" });
    expect(res.status).toBe(201);
  });

  it("forwards GET /api/v1/products", async () => {
    const res = await auth(request.get("/api/v1/products"));
    expect(res.status).toBe(200);
  });

  it("forwards GET /api/v1/sales", async () => {
    const res = await auth(request.get("/api/v1/sales"));
    expect(res.status).toBe(200);
  });
});

describe("Blocked internal routes", () => {
  it("blocks POST /api/v1/alerts (internal)", async () => {
    const res = await auth(request.post("/api/v1/alerts")).send({});
    expect(res.status).toBe(404);
    expect(res.body.code).toBe("NOT_FOUND");
  });

  it("blocks POST /api/v1/inventory/m1/A1/decrease (internal)", async () => {
    const res = await auth(request.post("/api/v1/inventory/m1/A1/decrease")).send({});
    expect(res.status).toBe(404);
  });

  it("returns 404 for completely unknown route", async () => {
    const res = await auth(request.get("/api/v1/unknown-service-xyz"));
    expect(res.status).toBe(404);
    expect(res.body.code).toBe("NOT_FOUND");
  });
});

describe("Auth on proxy routes", () => {
  it("returns 401 without token", async () => {
    const res = await request.get("/api/v1/machines");
    expect(res.status).toBe(401);
  });
});

describe("Timeout", () => {
  it("returns 504 when upstream is slow", async () => {
    const res = await auth(request.get("/api/v1/machines/slow"));
    expect(res.status).toBe(504);
    expect(res.body.code).toBe("UPSTREAM_TIMEOUT");
  });
});
