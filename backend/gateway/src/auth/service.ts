import bcrypt from "bcrypt";
import { Pool } from "pg";
import { config } from "../core/config";
import { signToken } from "../core/jwt";
import { ApiError } from "../core/errors";

const BCRYPT_ROUNDS = 12;

interface Operator {
  id: string;
  email: string;
  name: string;
  status: string;
  token_version: number;
  password_hash: string;
}

interface TokenOut {
  accessToken: string;
  tokenType: "Bearer";
  expiresAt: string;
}

const makeToken = (op: Operator): TokenOut => {
  const accessToken = signToken({ sub: op.id, email: op.email, tokenVersion: op.token_version });
  const expiresAt = new Date(Date.now() + config.jwtExpiresInSeconds * 1000).toISOString();
  return { accessToken, tokenType: "Bearer", expiresAt };
};

export const bootstrapOperator = async (db: Pool): Promise<void> => {
  // Read at call time so tests can set env vars after module load
  const email = process.env.BOOTSTRAP_OPERATOR_EMAIL;
  const password = process.env.BOOTSTRAP_OPERATOR_PASSWORD;
  if (!email || !password) return;

  const { rows } = await db.query("SELECT COUNT(*) AS n FROM operators");
  if (parseInt(rows[0].n, 10) > 0) return;

  const hash = await bcrypt.hash(password, BCRYPT_ROUNDS);
  await db.query(
    "INSERT INTO operators (email, name, password_hash) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING",
    [email, "Operater", hash],
  );
  console.log(`[gateway] bootstrap operator created: ${email}`);
};

export const login = async (db: Pool, email: string, password: string): Promise<TokenOut> => {
  const { rows } = await db.query<Operator>(
    "SELECT id, email, name, status, token_version, password_hash FROM operators WHERE email = $1",
    [email],
  );
  const op = rows[0];
  const invalid = new ApiError(401, "INVALID_CREDENTIALS", "Invalid credentials");

  if (!op) {
    // constant-time compare to avoid timing attacks
    await bcrypt.compare(
      password,
      "$2b$12$invalidhashfortimingnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn",
    );
    throw invalid;
  }
  if (op.status !== "ACTIVE") throw invalid;

  const match = await bcrypt.compare(password, op.password_hash);
  if (!match) throw invalid;

  return makeToken(op);
};

export const getMe = async (db: Pool, operatorId: string) => {
  const { rows } = await db.query(
    "SELECT id, email, name, status, created_at FROM operators WHERE id = $1",
    [operatorId],
  );
  if (!rows[0]) throw new ApiError(404, "OPERATOR_NOT_FOUND", "Operator not found");
  return rows[0];
};

export const revokeSession = async (db: Pool, operatorId: string): Promise<void> => {
  await db.query("UPDATE operators SET token_version = token_version + 1 WHERE id = $1", [
    operatorId,
  ]);
};

export const changePassword = async (
  db: Pool,
  operatorId: string,
  currentPassword: string,
  newPassword: string,
): Promise<TokenOut> => {
  const { rows } = await db.query<Operator>(
    "SELECT id, email, name, status, token_version, password_hash FROM operators WHERE id = $1",
    [operatorId],
  );
  const op = rows[0];
  if (!op) throw new ApiError(404, "OPERATOR_NOT_FOUND", "Operator not found");

  const match = await bcrypt.compare(currentPassword, op.password_hash);
  if (!match) {
    throw new ApiError(422, "VALIDATION_ERROR", "Current password is incorrect", [
      { field: "currentPassword", issue: "incorrect password" },
    ]);
  }

  const hash = await bcrypt.hash(newPassword, BCRYPT_ROUNDS);
  const { rows: updated } = await db.query<Operator>(
    "UPDATE operators SET password_hash = $1, token_version = token_version + 1 WHERE id = $2 RETURNING id, email, name, status, token_version",
    [hash, operatorId],
  );

  return makeToken(updated[0]);
};
