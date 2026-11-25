import { describe, it, expect, beforeAll, afterAll } from "vitest";
import supertest from "supertest";
import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { createPool } from "../src/core/db";
import { createApp } from "../src/app";
import { Pool } from "pg";

let container: StartedPostgreSqlContainer;
let db: Pool;
let request: ReturnType<typeof supertest>;

beforeAll(async () => {
  container = await new PostgreSqlContainer("postgres:17-alpine").start();
  db = createPool(container.getConnectionUri());
  const app = createApp(db);
  request = supertest(app);
});

afterAll(async () => {
  await db.end();
  await container.stop();
});

describe("X-Trace-Id", () => {
  it("echoes provided trace id", async () => {
    const res = await request.get("/health/live").set("X-Trace-Id", "test-trace-123");
    expect(res.headers["x-trace-id"]).toBe("test-trace-123");
  });

  it("generates trace id when not provided", async () => {
    const res = await request.get("/health/live");
    expect(res.headers["x-trace-id"]).toBeTruthy();
  });
});

describe("Unknown routes", () => {
  it("returns 404 for route outside /api/v1", async () => {
    const res = await request.get("/totally-unknown-path-xyz");
    expect(res.status).toBe(404);
    expect(res.body.code).toBe("NOT_FOUND");
    expect(res.body.traceId).toBeTruthy();
  });

  it("returns 401 for /api/v1/* without token (proxy requires auth)", async () => {
    const res = await request.get("/api/v1/unknown-route-xyz");
    expect(res.status).toBe(401);
  });
});
