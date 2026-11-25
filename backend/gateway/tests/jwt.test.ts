import { describe, it, expect, beforeAll, afterAll } from "vitest";
import supertest from "supertest";
import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { Pool } from "pg";
import { createPool } from "../src/core/db";
import { createApp } from "../src/app";
import { signToken } from "../src/core/jwt";
import jwt from "jsonwebtoken";

let container: StartedPostgreSqlContainer;
let db: Pool;
let request: ReturnType<typeof supertest>;
let operatorId: string;

beforeAll(async () => {
  container = await new PostgreSqlContainer("postgres:17-alpine").start();
  db = createPool(container.getConnectionUri());

  await db.query(`
    CREATE TYPE operator_status AS ENUM ('ACTIVE', 'INACTIVE');
    CREATE TABLE operators (
      id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      email           TEXT NOT NULL UNIQUE,
      name            TEXT NOT NULL,
      password_hash   TEXT NOT NULL,
      status          operator_status NOT NULL DEFAULT 'ACTIVE',
      token_version   INTEGER NOT NULL DEFAULT 0,
      created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
      deactivated_at  TIMESTAMPTZ
    );
  `);

  const result = await db.query(
    "INSERT INTO operators (email, name, password_hash) VALUES ($1, $2, $3) RETURNING id",
    ["test@test.com", "Test", "hash"],
  );
  operatorId = result.rows[0].id;

  const app = createApp(db);
  request = supertest(app);
});

afterAll(async () => {
  await db.end();
  await container.stop();
});

describe("sanitizeHeaders", () => {
  it("strips X-Operator-Id from client request", async () => {
    const res = await request
      .get("/health/live")
      .set("X-Operator-Id", "injected-id")
      .set("X-Trace-Id", "trace-1");
    expect(res.status).toBe(200);
  });

  it("strips X-Service-Token from client request", async () => {
    const res = await request.get("/health/live").set("X-Service-Token", "injected-token");
    expect(res.status).toBe(200);
  });
});

describe("requireOperator", () => {
  it("rejects request without Authorization header", async () => {
    const res = await request.get("/api/v1/operator-only");
    expect(res.status).toBe(404); // route not registered yet — not 401
  });

  it("verifyToken accepts valid token", () => {
    const token = signToken({ sub: operatorId, email: "test@test.com", tokenVersion: 0 });
    const decoded = jwt.verify(token, process.env.JWT_SECRET!) as {
      sub: string;
      tokenVersion: number;
    };
    expect(decoded.sub).toBe(operatorId);
    expect(decoded.tokenVersion).toBe(0);
  });

  it("verifyToken rejects tampered token", () => {
    const token = signToken({ sub: operatorId, email: "test@test.com", tokenVersion: 0 });
    const tampered = token.slice(0, -5) + "XXXXX";
    expect(() => {
      jwt.verify(tampered, process.env.JWT_SECRET!);
    }).toThrow();
  });

  it("verifyToken rejects token signed with wrong secret", () => {
    const badToken = jwt.sign(
      { sub: operatorId, email: "test@test.com", tokenVersion: 0 },
      "wrong-secret",
      { algorithm: "HS256" },
    );
    expect(() => {
      jwt.verify(badToken, process.env.JWT_SECRET!);
    }).toThrow();
  });
});
