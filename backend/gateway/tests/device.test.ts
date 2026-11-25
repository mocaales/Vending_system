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

const MACHINE_ID = "aabbccdd-1234-5678-9abc-aabbccddeeff";
const VALID_KEY = "valid-machine-key-abc123";
const WRONG_KEY = "wrong-key-xyz";

const SALE_EVENT = {
  saleId: "6b1f0c2e-5a34-4d7e-9a10-2f6c1b7d8e01",
  machineId: MACHINE_ID,
  slotId: "A1",
  productId: "c3a8f7d4-12ab-4e55-b6c9-5d0e8a1f2b73",
  quantity: 1,
  unitPricePaid: "2.50",
  currency: "EUR",
  paymentMethod: "CARD",
  occurredAt: "2026-11-17T08:30:00Z",
};

const MEASUREMENT = {
  measurementId: "9e2d7b30-8c41-4a6f-b5d2-0a3f6c8e1d92",
  machineId: MACHINE_ID,
  observedAt: "2026-11-17T08:30:00Z",
  temperatureC: "4.2",
  doorState: "CLOSED",
};

beforeAll(async () => {
  const mock = express();
  mock.use(express.json());

  // Machine key verification
  mock.post("/v1/internal/machine-keys/verify", (req, res) => {
    const { machineKey } = req.body ?? {};
    if (machineKey === VALID_KEY) return res.json({ machineId: MACHINE_ID, status: "ACTIVE" });
    return res.status(401).json({ code: "UNAUTHENTICATED", message: "Invalid key", traceId: "t" });
  });

  // Sales service
  mock.post("/v1/sales", (req, res) => {
    const machineId = req.headers["x-machine-id"];
    if (!machineId) return res.status(400).json({ code: "BAD_REQUEST", message: "no machine id" });
    return res.status(201).json({ saleId: req.body.saleId });
  });

  // Monitoring service
  mock.post("/v1/measurements", (req, res) => {
    const machineId = req.headers["x-machine-id"];
    if (!machineId) return res.status(400).json({ code: "BAD_REQUEST", message: "no machine id" });
    return res.status(201).json({ measurementId: req.body.measurementId });
  });

  mockServer = createServer(mock);
  await new Promise<void>((resolve) => mockServer.listen(0, resolve));
  mockPort = (mockServer.address() as { port: number }).port;

  process.env.MACHINE_URL = `http://localhost:${mockPort}`;
  process.env.SALES_URL = `http://localhost:${mockPort}`;
  process.env.MONITORING_URL = `http://localhost:${mockPort}`;

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
    "dev@test.com",
    "Dev",
    hash,
  ]);

  request = supertest(createApp(db));
});

afterAll(async () => {
  await db.end();
  await container.stop();
  await new Promise<void>((resolve) => mockServer.close(() => resolve()));
});

describe("POST /api/v1/device/sales", () => {
  it("forwards sale with valid key", async () => {
    const res = await request
      .post("/api/v1/device/sales")
      .set("X-Machine-Key", VALID_KEY)
      .send(SALE_EVENT);
    expect(res.status).toBe(201);
    expect(res.body.saleId).toBe(SALE_EVENT.saleId);
  });

  it("returns 401 without key header", async () => {
    const res = await request.post("/api/v1/device/sales").send(SALE_EVENT);
    expect(res.status).toBe(401);
    expect(res.body.code).toBe("UNAUTHENTICATED");
  });

  it("returns 401 with invalid key", async () => {
    const res = await request
      .post("/api/v1/device/sales")
      .set("X-Machine-Key", WRONG_KEY)
      .send(SALE_EVENT);
    expect(res.status).toBe(401);
  });

  it("returns 403 when body machineId does not match key", async () => {
    const res = await request
      .post("/api/v1/device/sales")
      .set("X-Machine-Key", VALID_KEY)
      .send({ ...SALE_EVENT, machineId: "00000000-0000-0000-0000-000000000000" });
    expect(res.status).toBe(403);
    expect(res.body.code).toBe("MACHINE_KEY_MISMATCH");
  });
});

describe("POST /api/v1/device/measurements", () => {
  it("forwards measurement with valid key", async () => {
    const res = await request
      .post("/api/v1/device/measurements")
      .set("X-Machine-Key", VALID_KEY)
      .send(MEASUREMENT);
    expect(res.status).toBe(201);
    expect(res.body.measurementId).toBe(MEASUREMENT.measurementId);
  });

  it("returns 401 without key header", async () => {
    const res = await request.post("/api/v1/device/measurements").send(MEASUREMENT);
    expect(res.status).toBe(401);
  });

  it("returns 403 when body machineId does not match key", async () => {
    const res = await request
      .post("/api/v1/device/measurements")
      .set("X-Machine-Key", VALID_KEY)
      .send({ ...MEASUREMENT, machineId: "00000000-0000-0000-0000-000000000000" });
    expect(res.status).toBe(403);
    expect(res.body.code).toBe("MACHINE_KEY_MISMATCH");
  });
});

describe("Operator POST blocked", () => {
  it("POST /api/v1/sales is blocked for operators (device only)", async () => {
    const loginRes = await request
      .post("/api/v1/auth/login")
      .send({ email: "dev@test.com", password: "password-123" });
    const token = loginRes.body.accessToken;

    const res = await request
      .post("/api/v1/sales")
      .set("Authorization", `Bearer ${token}`)
      .send(SALE_EVENT);
    expect(res.status).toBe(404);
    expect(res.body.code).toBe("NOT_FOUND");
  });

  it("POST /api/v1/measurements is blocked for operators (device only)", async () => {
    const loginRes = await request
      .post("/api/v1/auth/login")
      .send({ email: "dev@test.com", password: "password-123" });
    const token = loginRes.body.accessToken;

    const res = await request
      .post("/api/v1/measurements")
      .set("Authorization", `Bearer ${token}`)
      .send(MEASUREMENT);
    expect(res.status).toBe(404);
    expect(res.body.code).toBe("NOT_FOUND");
  });
});
