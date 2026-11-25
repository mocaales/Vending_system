import { describe, it, expect, beforeAll, afterAll } from "vitest";
import supertest from "supertest";
import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { Pool } from "pg";
import bcrypt from "bcrypt";
import { createPool } from "../src/core/db";
import { createApp } from "../src/app";
import { bootstrapOperator } from "../src/auth/service";

let container: StartedPostgreSqlContainer;
let db: Pool;
let request: ReturnType<typeof supertest>;

const PASSWORD = "correct-password-123";

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

  const hash = await bcrypt.hash(PASSWORD, 4); // low rounds for speed in tests
  await db.query("INSERT INTO operators (email, name, password_hash) VALUES ($1, $2, $3)", [
    "op@test.com",
    "Test Op",
    hash,
  ]);

  const app = createApp(db);
  request = supertest(app);
});

afterAll(async () => {
  await db.end();
  await container.stop();
});

// helper
const loginAs = (email = "op@test.com", password = PASSWORD) =>
  request.post("/api/v1/auth/login").send({ email, password });

describe("POST /api/v1/auth/login", () => {
  it("returns 200 with token on valid credentials", async () => {
    const res = await loginAs();
    expect(res.status).toBe(200);
    expect(res.body.accessToken).toBeTruthy();
    expect(res.body.tokenType).toBe("Bearer");
    expect(res.body.expiresAt).toBeTruthy();
    expect(res.body.password_hash).toBeUndefined();
  });

  it("returns 401 on wrong password", async () => {
    const res = await loginAs("op@test.com", "wrong-password");
    expect(res.status).toBe(401);
    expect(res.body.code).toBe("INVALID_CREDENTIALS");
  });

  it("returns 401 on unknown email", async () => {
    const res = await loginAs("nobody@test.com", "any");
    expect(res.status).toBe(401);
    expect(res.body.code).toBe("INVALID_CREDENTIALS");
  });

  it("returns 422 when email missing", async () => {
    const res = await request.post("/api/v1/auth/login").send({ password: PASSWORD });
    expect(res.status).toBe(422);
    expect(res.body.code).toBe("VALIDATION_ERROR");
  });
});

describe("GET /api/v1/auth/me", () => {
  it("returns operator info with valid token", async () => {
    const { body } = await loginAs();
    const res = await request
      .get("/api/v1/auth/me")
      .set("Authorization", `Bearer ${body.accessToken}`);
    expect(res.status).toBe(200);
    expect(res.body.email).toBe("op@test.com");
    expect(res.body.password_hash).toBeUndefined();
  });

  it("returns 401 without token", async () => {
    const res = await request.get("/api/v1/auth/me");
    expect(res.status).toBe(401);
  });
});

describe("DELETE /api/v1/auth/session", () => {
  it("revokes token — subsequent request gets 401", async () => {
    const { body } = await loginAs();
    const token = body.accessToken;

    const revoke = await request
      .delete("/api/v1/auth/session")
      .set("Authorization", `Bearer ${token}`);
    expect(revoke.status).toBe(204);

    const me = await request.get("/api/v1/auth/me").set("Authorization", `Bearer ${token}`);
    expect(me.status).toBe(401);
  });
});

describe("PUT /api/v1/auth/password", () => {
  it("changes password and returns new token", async () => {
    const { body } = await loginAs();
    const res = await request
      .put("/api/v1/auth/password")
      .set("Authorization", `Bearer ${body.accessToken}`)
      .send({ currentPassword: PASSWORD, newPassword: "new-password-secure-123" });
    expect(res.status).toBe(200);
    expect(res.body.accessToken).toBeTruthy();

    // restore for other tests
    await request
      .put("/api/v1/auth/password")
      .set("Authorization", `Bearer ${res.body.accessToken}`)
      .send({ currentPassword: "new-password-secure-123", newPassword: PASSWORD });
  });

  it("returns 422 on wrong current password", async () => {
    const { body } = await loginAs();
    const res = await request
      .put("/api/v1/auth/password")
      .set("Authorization", `Bearer ${body.accessToken}`)
      .send({ currentPassword: "wrong", newPassword: "new-password-secure-123" });
    expect(res.status).toBe(422);
    expect(res.body.details[0].field).toBe("currentPassword");
  });

  it("returns 422 when newPassword too short", async () => {
    const { body } = await loginAs();
    const res = await request
      .put("/api/v1/auth/password")
      .set("Authorization", `Bearer ${body.accessToken}`)
      .send({ currentPassword: PASSWORD, newPassword: "short" });
    expect(res.status).toBe(422);
    expect(res.body.details[0].field).toBe("newPassword");
  });
});

describe("bootstrapOperator", () => {
  it("creates operator when table is empty", async () => {
    const freshContainer = await new PostgreSqlContainer("postgres:17-alpine").start();
    const freshDb = createPool(freshContainer.getConnectionUri());
    await freshDb.query(`
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

    process.env.BOOTSTRAP_OPERATOR_EMAIL = "boot@test.com";
    process.env.BOOTSTRAP_OPERATOR_PASSWORD = "bootstrap-password-123";

    await bootstrapOperator(freshDb);

    const { rows } = await freshDb.query("SELECT email FROM operators");
    expect(rows).toHaveLength(1);
    expect(rows[0].email).toBe("boot@test.com");

    await freshDb.end();
    await freshContainer.stop();
  });
});
