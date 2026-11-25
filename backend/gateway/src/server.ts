import { config } from "./core/config";
import { createPool } from "./core/db";
import { createApp } from "./app";
import { bootstrapOperator } from "./auth/service";
import migrate from "node-pg-migrate";
import path from "path";

const start = async (): Promise<void> => {
  const db = createPool(config.databaseUrl);

  await migrate({
    databaseUrl: config.databaseUrl,
    migrationsTable: "pgmigrations",
    dir: path.join(__dirname, "..", "migrations"),
    direction: "up",
    log: (msg) => console.log("[migrate]", msg),
  });

  await bootstrapOperator(db);

  const app = createApp(db);

  app.listen(config.port, () => {
    console.log(`[gateway] listening on port ${config.port}`);
  });
};

start().catch((err) => {
  console.error("[gateway] startup failed", err);
  process.exit(1);
});
