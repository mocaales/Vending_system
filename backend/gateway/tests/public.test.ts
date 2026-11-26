import { describe, it, expect, beforeAll, afterAll } from "vitest";
import supertest from "supertest";
import express from "express";
import { createServer, Server } from "http";
import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { Pool } from "pg";
import { createPool } from "../src/core/db";
import { createApp } from "../src/app";

let container: StartedPostgreSqlContainer;
let db: Pool;
let request: ReturnType<typeof supertest>;
let mockServer: Server;
let mockPort: number;

const ID_ACTIVE = "00000000-0000-4000-8000-000000000001";
const ID_SERVICE = "00000000-0000-4000-8000-000000000002";
const ID_RETIRED = "00000000-0000-4000-8000-000000000003";
const ID_UNKNOWN = "00000000-0000-4000-9999-000000000000";

const ACTIVE_MACHINE = {
  id: ID_ACTIVE,
  name: "Avtomat A",
  address: "Ulica 1",
  latitude: 46.5,
  longitude: 15.6,
  status: "ACTIVE",
};
const SERVICE_MACHINE = { ...ACTIVE_MACHINE, id: ID_SERVICE, name: "Avtomat B", status: "SERVICE" };
const RETIRED_MACHINE = { ...ACTIVE_MACHINE, id: ID_RETIRED, name: "Avtomat C", status: "RETIRED" };

const INVENTORY_ITEMS = [
  {
    slot: "A1",
    productId: "00000000-0000-4000-8000-000000000011",
    expiryDate: "2099-12-31",
    quantity: 5,
  },
  {
    slot: "A2",
    productId: "00000000-0000-4000-8000-000000000012",
    expiryDate: "2000-01-01",
    quantity: 3,
  }, // expired
  {
    slot: "A3",
    productId: "00000000-0000-4000-8000-000000000013",
    expiryDate: "2099-12-31",
    quantity: 0,
  }, // empty
  { slot: "A4", productId: null, expiryDate: null, quantity: 0 }, // no product
];

beforeAll(async () => {
  const mock = express();
  mock.use(express.json());

  mock.get("/v1/machines", (_req, res) =>
    res.json({ items: [ACTIVE_MACHINE, SERVICE_MACHINE, RETIRED_MACHINE], total: 3 }),
  );
  mock.get(`/v1/machines/${ID_ACTIVE}`, (_req, res) => res.json(ACTIVE_MACHINE));
  mock.get(`/v1/machines/${ID_SERVICE}`, (_req, res) => res.json(SERVICE_MACHINE));
  mock.get(`/v1/machines/${ID_RETIRED}`, (_req, res) => res.json(RETIRED_MACHINE));
  mock.get(`/v1/machines/${ID_UNKNOWN}`, (_req, res) =>
    res.status(404).json({ code: "MACHINE_NOT_FOUND" }),
  );
  mock.get(`/v1/inventory/${ID_ACTIVE}`, (_req, res) =>
    res.json({ items: INVENTORY_ITEMS, total: INVENTORY_ITEMS.length }),
  );
  mock.get(`/v1/inventory/${ID_SERVICE}`, (_req, res) =>
    res.status(503).json({ code: "UNAVAILABLE" }),
  );

  mockServer = createServer(mock);
  await new Promise<void>((resolve) => mockServer.listen(0, resolve));
  mockPort = (mockServer.address() as { port: number }).port;

  process.env.MACHINE_URL = `http://localhost:${mockPort}`;
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

  request = supertest(createApp(db));
});

afterAll(async () => {
  await db.end();
  await container.stop();
  await new Promise<void>((resolve) => mockServer.close(() => resolve()));
});

describe("GET /api/v1/public/machines", () => {
  it("returns ACTIVE and SERVICE machines, hides RETIRED", async () => {
    const res = await request.get("/api/v1/public/machines");
    expect(res.status).toBe(200);
    expect(res.body.items).toHaveLength(2);
    expect(res.body.items.some((m: { id: string }) => m.id === ID_RETIRED)).toBe(false);
  });

  it("marks ACTIVE machine as available:true", async () => {
    const res = await request.get("/api/v1/public/machines");
    const active = res.body.items.find((m: { id: string }) => m.id === ID_ACTIVE);
    expect(active.available).toBe(true);
  });

  it("marks SERVICE machine as available:false", async () => {
    const res = await request.get("/api/v1/public/machines");
    const service = res.body.items.find((m: { id: string }) => m.id === ID_SERVICE);
    expect(service.available).toBe(false);
  });

  it("does not expose keys, alarms or sales", async () => {
    const res = await request.get("/api/v1/public/machines");
    const m = res.body.items[0];
    expect(m.keys).toBeUndefined();
    expect(m.alarms).toBeUndefined();
    expect(m.sales).toBeUndefined();
  });

  it("requires no authentication", async () => {
    const res = await request.get("/api/v1/public/machines");
    expect(res.status).toBe(200);
  });
});

describe("GET /api/v1/public/machines/:id/availability", () => {
  it("returns OK with filtered slots for active machine", async () => {
    const res = await request.get(`/api/v1/public/machines/${ID_ACTIVE}/availability`);
    expect(res.status).toBe(200);
    expect(res.body.status).toBe("OK");
    expect(res.body.slots).toHaveLength(1); // only A1 passes all filters
    expect(res.body.slots[0].slotCode).toBe("A1");
    expect(res.body.slots[0].productId).toBe("00000000-0000-4000-8000-000000000011");
  });

  it("filters out expired slots", async () => {
    const res = await request.get(`/api/v1/public/machines/${ID_ACTIVE}/availability`);
    const slotCodes = res.body.slots.map((s: { slotCode: string }) => s.slotCode);
    expect(slotCodes).not.toContain("A2"); // expired
  });

  it("filters out empty slots", async () => {
    const res = await request.get(`/api/v1/public/machines/${ID_ACTIVE}/availability`);
    const slotCodes = res.body.slots.map((s: { slotCode: string }) => s.slotCode);
    expect(slotCodes).not.toContain("A3"); // quantity 0
  });

  it("does not expose quantity or batchCode", async () => {
    const res = await request.get(`/api/v1/public/machines/${ID_ACTIVE}/availability`);
    const slot = res.body.slots[0];
    expect(slot.quantity).toBeUndefined();
    expect(slot.batchCode).toBeUndefined();
  });

  it("returns UNAVAILABLE when Inventory is down", async () => {
    const res = await request.get(`/api/v1/public/machines/${ID_SERVICE}/availability`);
    expect(res.status).toBe(200);
    expect(res.body.status).toBe("UNAVAILABLE");
    expect(res.body.slots).toBeNull();
  });

  it("returns 404 for RETIRED machine", async () => {
    const res = await request.get(`/api/v1/public/machines/${ID_RETIRED}/availability`);
    expect(res.status).toBe(404);
    expect(res.body.code).toBe("MACHINE_NOT_FOUND");
  });

  it("returns 404 for unknown machine", async () => {
    const res = await request.get(`/api/v1/public/machines/${ID_UNKNOWN}/availability`);
    expect(res.status).toBe(404);
  });

  it("returns 400 for non-UUID machine id", async () => {
    const res = await request.get("/api/v1/public/machines/not-a-uuid/availability");
    expect(res.status).toBe(400);
    expect(res.body.code).toBe("BAD_REQUEST");
  });
});
