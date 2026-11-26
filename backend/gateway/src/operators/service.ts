import bcrypt from "bcrypt";
import { Pool } from "pg";
import { ApiError } from "../core/errors";
import { normalizeEmail } from "../core/email";

const BCRYPT_ROUNDS = 12;

interface Operator {
  id: string;
  email: string;
  name: string;
  status: string;
  token_version: number;
  created_at: string;
  deactivated_at: string | null;
}

const sanitize = (op: Operator) => ({
  id: op.id,
  email: op.email,
  name: op.name,
  status: op.status,
  createdAt: op.created_at,
  deactivatedAt: op.deactivated_at ?? undefined,
});

export const listOperators = async (
  db: Pool,
  status: string | undefined,
  limit: number,
  offset: number,
) => {
  const conditions: string[] = [];
  const params: unknown[] = [];

  if (status) {
    params.push(status.toUpperCase());
    conditions.push(`status = $${params.length}::operator_status`);
  }

  const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
  const { rows: items } = await db.query<Operator>(
    `SELECT id, email, name, status, token_version, created_at, deactivated_at
     FROM operators ${where} ORDER BY created_at ASC
     LIMIT $${params.length + 1} OFFSET $${params.length + 2}`,
    [...params, limit, offset],
  );
  const { rows: countRows } = await db.query(
    `SELECT COUNT(*)::int AS total FROM operators ${where}`,
    params,
  );
  return { items: items.map(sanitize), total: countRows[0].total, limit, offset };
};

export const getOperator = async (db: Pool, id: string) => {
  const { rows } = await db.query<Operator>(
    "SELECT id, email, name, status, token_version, created_at, deactivated_at FROM operators WHERE id = $1",
    [id],
  );
  if (!rows[0]) throw new ApiError(404, "OPERATOR_NOT_FOUND", "Operator not found");
  return sanitize(rows[0]);
};

export const createOperator = async (db: Pool, email: string, name: string, password: string) => {
  const hash = await bcrypt.hash(password, BCRYPT_ROUNDS);
  try {
    const { rows } = await db.query<Operator>(
      `INSERT INTO operators (email, name, password_hash)
       VALUES ($1, $2, $3)
       RETURNING id, email, name, status, token_version, created_at, deactivated_at`,
      [normalizeEmail(email), name, hash],
    );
    return sanitize(rows[0]);
  } catch (err) {
    if ((err as { code?: string }).code === "23505")
      throw new ApiError(409, "OPERATOR_EMAIL_TAKEN", "Email already taken");
    throw err;
  }
};

export const updateOperator = async (db: Pool, id: string, email: string, name: string) => {
  try {
    const { rows } = await db.query<Operator>(
      `UPDATE operators SET email = $1, name = $2 WHERE id = $3
       RETURNING id, email, name, status, token_version, created_at, deactivated_at`,
      [normalizeEmail(email), name, id],
    );
    if (!rows[0]) throw new ApiError(404, "OPERATOR_NOT_FOUND", "Operator not found");
    return sanitize(rows[0]);
  } catch (err) {
    if ((err as { code?: string }).code === "23505")
      throw new ApiError(409, "OPERATOR_EMAIL_TAKEN", "Email already taken");
    throw err;
  }
};

export const deactivateOperator = async (db: Pool, id: string, callerId: string): Promise<void> => {
  if (id === callerId) throw new ApiError(409, "OPERATOR_IS_SELF", "Cannot deactivate yourself");

  const { rows } = await db.query<Operator>("SELECT id, status FROM operators WHERE id = $1", [id]);
  if (!rows[0]) throw new ApiError(404, "OPERATOR_NOT_FOUND", "Operator not found");
  if (rows[0].status === "INACTIVE") return;

  const { rows: activeCount } = await db.query(
    "SELECT COUNT(*)::int AS n FROM operators WHERE status = 'ACTIVE'",
  );
  if (activeCount[0].n <= 1)
    throw new ApiError(409, "OPERATOR_LAST_ACTIVE", "Cannot deactivate the last active operator");

  await db.query(
    `UPDATE operators SET status = 'INACTIVE', deactivated_at = now(),
     token_version = token_version + 1 WHERE id = $1`,
    [id],
  );
};

export const resetOperatorPassword = async (
  db: Pool,
  id: string,
  callerId: string,
  newPassword: string,
): Promise<void> => {
  if (id === callerId)
    throw new ApiError(409, "OPERATOR_IS_SELF", "Use /auth/password to change your own password");

  const { rows } = await db.query<Operator>("SELECT id, status FROM operators WHERE id = $1", [id]);
  if (!rows[0]) throw new ApiError(404, "OPERATOR_NOT_FOUND", "Operator not found");
  if (rows[0].status !== "ACTIVE")
    throw new ApiError(409, "INVALID_STATE_TRANSITION", "Operator is not active");

  const hash = await bcrypt.hash(newPassword, BCRYPT_ROUNDS);
  await db.query(
    "UPDATE operators SET password_hash = $1, token_version = token_version + 1 WHERE id = $2",
    [hash, id],
  );
};
