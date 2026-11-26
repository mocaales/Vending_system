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

const ID_MACHINE = "00000000-0000-4000-8000-000000000001";
const ID_UNKNOWN = "00000000-0000-4000-9999-000000000000";

const MACHINE = { id: ID_MACHINE, name: "Avtomat 1", status: "ACTIVE" };

beforeAll(async () => {
  const mock = express();
  mock.use(express.json());

  mock.get("/v1/machines", (_req, res) => res.json({ items: [MACHINE], total: 1 }));
  mock.get(`/v1/machines/${ID_MACHINE}`, (_req, res) => res.json(MACHINE));
  mock.get(`/v1/machines/${ID_UNKNOWN}`, (_req, res) =>
    res.status(404).json({ code: "MACHINE_NOT_FOUND" }),
  );

  mock.get("/v1/alerts", (_req, res) => res.json({ items: [], total: 0 }));
  mock.get(`/v1/monitoring/${ID_MACHINE}/status`, (_req, res) =>
    res.json({ reachability: "ONLINE", lastMeasurement: null }),
  );
  mock.get(`/v1/thresholds/${ID_MACHINE}`, (_req, res) =>
    res.json({ maxTempC: "8.0", isDefault: true }),
  );

  mock.get("/v1/sales/stats", (_req, res) => res.json({ totals: [], topProducts: [] }));

  mock.get(`/v1/inventory/${ID_MACHINE}`, (_req, res) => res.json({ items: [], total: 0 }));

  mockServer = createServer(mock);
  await new Promise<void>((resolve) => mockServer.listen(0, resolve));
  mockPort = (mockServer.address() as { port: number }).port;

  process.env.MACHINE_URL = `http://localhost:${mockPort}`;
  process.env.MONITORING_URL = `http://localhost:${mockPort}`;
  process.env.SALES_URL = `http://localhost:${mockPort}`;
  process.env.INVENTORY_URL = `http://localhost:${mockPort}`;

  container = await new PostgreSqlContainer("postgres:17-alpine").start();
  db = createPool(container.getConnectionUri());
  await db.query(`
    CREATE TYPE operator_status AS ENUM ('ACTIVE', 'INACTIVE');
    CREATE TABLE operators (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      email TEXT NOT NULL UNIQUE, name TEXT NOT NULL,
      password_hash TEXT NOT NULL,
      status operator_status NOT NULL DEFAULT 'ACTIVE',
      token_version INTEGER NOT NULL DEFAULT 0,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      deactivated_at TIMESTAMPTZ
    );
  `);
  const hash = await bcrypt.hash("password-123", 4);
  await db.query("INSERT INTO operators (email, name, password_hash) VALUES ($1,$2,$3)", [
    "op@test.com",
    "Op",
    hash,
  ]);

  request = supertest(createApp(db));
  const loginRes = await request
    .post("/api/v1/auth/login")
    .send({ email: "op@test.com", password: "password-123" });
  token = loginRes.body.accessToken;
});

afterAll(async () => {
  await db.end();
  await container.stop();
  await new Promise<void>((resolve) => mockServer.close(() => resolve()));
});

const auth = (chain: ReturnType<ReturnType<typeof supertest>["get"]>) =>
  chain.set("Authorization", `Bearer ${token}`);

describe("GET /api/v1/operator/dashboard", () => {
  it("returns machines, alarms and sales sections", async () => {
    const res = await auth(request.get("/api/v1/operator/dashboard"));
    expect(res.status).toBe(200);
    expect(res.body.machines).toBeDefined();
    expect(res.body.alarms).toBeDefined();
    expect(res.body.sales).toBeDefined();
  });

  it("each section has status and asOf", async () => {
    const res = await auth(request.get("/api/v1/operator/dashboard"));
    for (const key of ["machines", "alarms", "sales"]) {
      expect(res.body[key].status).toMatch(/^(OK|UNAVAILABLE|STALE)$/);
      expect(res.body[key].asOf).toBeTruthy();
    }
  });

  it("returns OK sections when services respond", async () => {
    const res = await auth(request.get("/api/v1/operator/dashboard"));
    expect(res.body.machines.status).toBe("OK");
    expect(res.body.machines.data.items).toHaveLength(1);
  });

  it("returns 401 without token", async () => {
    const res = await request.get("/api/v1/operator/dashboard");
    expect(res.status).toBe(401);
  });
});

describe("GET /api/v1/operator/machines/:id/overview", () => {
  it("returns all sections for existing machine", async () => {
    const res = await auth(request.get(`/api/v1/operator/machines/${ID_MACHINE}/overview`));
    expect(res.status).toBe(200);
    expect(res.body.machine).toBeDefined();
    expect(res.body.monitoring).toBeDefined();
    expect(res.body.thresholds).toBeDefined();
    expect(res.body.alarms).toBeDefined();
    expect(res.body.inventory).toBeDefined();
  });

  it("machine section has OK status and machine data", async () => {
    const res = await auth(request.get(`/api/v1/operator/machines/${ID_MACHINE}/overview`));
    expect(res.body.machine.status).toBe("OK");
    expect(res.body.machine.data.id).toBe(ID_MACHINE);
  });

  it("returns 404 for unknown machine", async () => {
    const res = await auth(request.get(`/api/v1/operator/machines/${ID_UNKNOWN}/overview`));
    expect(res.status).toBe(404);
    expect(res.body.code).toBe("MACHINE_NOT_FOUND");
  });

  it("returns UNAVAILABLE section when service fails, rest still returned", async () => {
    const origMonUrl = process.env.MONITORING_URL;
    process.env.MONITORING_URL = "http://localhost:1";
    process.env.PROXY_TIMEOUT_MS = "200";

    const res = await auth(request.get(`/api/v1/operator/machines/${ID_MACHINE}/overview`));
    expect(res.status).toBe(200);
    expect(res.body.monitoring.status).toBe("UNAVAILABLE");
    expect(res.body.machine.status).toBe("OK");

    process.env.MONITORING_URL = origMonUrl;
    process.env.PROXY_TIMEOUT_MS = undefined;
  });

  it("returns 401 without token", async () => {
    const res = await request.get(`/api/v1/operator/machines/${ID_MACHINE}/overview`);
    expect(res.status).toBe(401);
  });

  it("returns 400 for non-UUID machine id", async () => {
    const res = await auth(request.get("/api/v1/operator/machines/not-a-uuid/overview"));
    expect(res.status).toBe(400);
    expect(res.body.code).toBe("BAD_REQUEST");
  });
});
