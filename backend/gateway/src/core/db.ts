import { Pool } from "pg";

export const createPool = (connectionString: string): Pool => {
  const pool = new Pool({ connectionString, max: 5, connectionTimeoutMillis: 5000 });
  pool.on("error", (err) => console.error("[db] idle client error:", err.message));
  return pool;
};
