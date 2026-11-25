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

describe("GET /health/live", () => {
  it("returns 200", async () => {
    const res = await request.get("/health/live");
    expect(res.status).toBe(200);
    expect(res.body.status).toBe("ok");
  });
});

describe("GET /health/ready", () => {
  it("returns 200 when db is reachable", async () => {
    const res = await request.get("/health/ready");
    expect(res.status).toBe(200);
    expect(res.body.status).toBe("ok");
  });
});
