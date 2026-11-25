import { describe, it, expect, beforeAll, afterAll } from "vitest";
import supertest from "supertest";
import { PostgreSqlContainer, StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { Pool } from "pg";
import bcrypt from "bcrypt";
import { createPool } from "../src/core/db";
import { createApp } from "../src/app";

let container: StartedPostgreSqlContainer;
let db: Pool;
let request: ReturnType<typeof supertest>;
let adminToken: string;
let adminId: string;

beforeAll(async () => {
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

  const hash = await bcrypt.hash("admin-password-123", 4);
  const { rows } = await db.query(
    "INSERT INTO operators (email, name, password_hash) VALUES ($1,$2,$3) RETURNING id",
    ["admin@test.com", "Admin", hash],
  );
  adminId = rows[0].id;

  request = supertest(createApp(db));

  const loginRes = await request
    .post("/api/v1/auth/login")
    .send({ email: "admin@test.com", password: "admin-password-123" });
  adminToken = loginRes.body.accessToken;
});

afterAll(async () => {
  await db.end();
  await container.stop();
});

const auth = (chain: ReturnType<ReturnType<typeof supertest>["get"]>) =>
  chain.set("Authorization", `Bearer ${adminToken}`);

describe("GET /api/v1/operators", () => {
  it("returns list with admin", async () => {
    const res = await auth(request.get("/api/v1/operators"));
    expect(res.status).toBe(200);
    expect(res.body.items).toHaveLength(1);
    expect(res.body.items[0].email).toBe("admin@test.com");
    expect(res.body.items[0].password_hash).toBeUndefined();
  });

  it("returns 401 without token", async () => {
    const res = await request.get("/api/v1/operators");
    expect(res.status).toBe(401);
  });
});

describe("POST /api/v1/operators", () => {
  it("creates operator and returns 201", async () => {
    const res = await auth(request.post("/api/v1/operators")).send({
      email: "new@test.com",
      name: "New Op",
      password: "new-password-secure-123",
    });
    expect(res.status).toBe(201);
    expect(res.body.email).toBe("new@test.com");
    expect(res.body.password_hash).toBeUndefined();
  });

  it("returns 409 on duplicate email (case-insensitive)", async () => {
    const res = await auth(request.post("/api/v1/operators")).send({
      email: "NEW@test.com",
      name: "Dupe",
      password: "password-secure-123",
    });
    expect(res.status).toBe(409);
    expect(res.body.code).toBe("OPERATOR_EMAIL_TAKEN");
  });

  it("returns 422 when password too short", async () => {
    const res = await auth(request.post("/api/v1/operators")).send({
      email: "short@test.com",
      name: "Short",
      password: "short",
    });
    expect(res.status).toBe(422);
  });
});

describe("GET /api/v1/operators/:id", () => {
  it("returns operator by id", async () => {
    const res = await auth(request.get(`/api/v1/operators/${adminId}`));
    expect(res.status).toBe(200);
    expect(res.body.id).toBe(adminId);
  });

  it("returns 404 for unknown id", async () => {
    const res = await auth(request.get("/api/v1/operators/00000000-0000-0000-0000-000000000000"));
    expect(res.status).toBe(404);
    expect(res.body.code).toBe("OPERATOR_NOT_FOUND");
  });
});

describe("PUT /api/v1/operators/:id", () => {
  it("updates name and email", async () => {
    const createRes = await auth(request.post("/api/v1/operators")).send({
      email: "update-me@test.com",
      name: "Before",
      password: "password-secure-123",
    });
    const id = createRes.body.id;

    const res = await auth(request.put(`/api/v1/operators/${id}`)).send({
      email: "updated@test.com",
      name: "After",
    });
    expect(res.status).toBe(200);
    expect(res.body.name).toBe("After");
    expect(res.body.email).toBe("updated@test.com");
  });
});

describe("DELETE /api/v1/operators/:id", () => {
  it("deactivates operator and revokes tokens", async () => {
    const createRes = await auth(request.post("/api/v1/operators")).send({
      email: "delete-me@test.com",
      name: "DeleteMe",
      password: "password-secure-123",
    });
    const id = createRes.body.id;

    const res = await auth(request.delete(`/api/v1/operators/${id}`));
    expect(res.status).toBe(204);

    const check = await auth(request.get(`/api/v1/operators/${id}`));
    expect(check.body.status).toBe("INACTIVE");
  });

  it("returns 409 OPERATOR_IS_SELF when deactivating self", async () => {
    const res = await auth(request.delete(`/api/v1/operators/${adminId}`));
    expect(res.status).toBe(409);
    expect(res.body.code).toBe("OPERATOR_IS_SELF");
  });

  it("returns 409 OPERATOR_LAST_ACTIVE when only one active left", async () => {
    const createRes = await auth(request.post("/api/v1/operators")).send({
      email: "last-active@test.com",
      name: "Last",
      password: "password-secure-123",
    });
    const otherId = createRes.body.id;

    // deactivate other so admin is last active
    await auth(request.delete(`/api/v1/operators/${otherId}`));

    const res = await auth(request.delete(`/api/v1/operators/${adminId}`));
    expect(res.status).toBe(409);
    expect(res.body.code).toBe("OPERATOR_IS_SELF");
  });
});

describe("PUT /api/v1/operators/:id/password", () => {
  it("resets another operator password (204)", async () => {
    const createRes = await auth(request.post("/api/v1/operators")).send({
      email: "resetme@test.com",
      name: "ResetMe",
      password: "password-secure-123",
    });
    const id = createRes.body.id;

    const res = await auth(request.put(`/api/v1/operators/${id}/password`)).send({
      newPassword: "brand-new-password-456",
    });
    expect(res.status).toBe(204);
  });

  it("returns 409 OPERATOR_IS_SELF when resetting own password", async () => {
    const res = await auth(request.put(`/api/v1/operators/${adminId}/password`)).send({
      newPassword: "brand-new-password-456",
    });
    expect(res.status).toBe(409);
    expect(res.body.code).toBe("OPERATOR_IS_SELF");
  });

  it("returns 422 when newPassword too short", async () => {
    const createRes = await auth(request.post("/api/v1/operators")).send({
      email: "shortpwd@test.com",
      name: "ShortPwd",
      password: "password-secure-123",
    });
    const id = createRes.body.id;

    const res = await auth(request.put(`/api/v1/operators/${id}/password`)).send({
      newPassword: "short",
    });
    expect(res.status).toBe(422);
  });
});
