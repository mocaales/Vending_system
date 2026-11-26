import { Router, Request, Response } from "express";
import { Pool } from "pg";
import { asyncHandler } from "./asyncHandler";

export const createHealthRouter = (db: Pool): Router => {
  const router = Router();

  router.get("/health/live", (_req: Request, res: Response) => {
    res.status(200).json({ status: "ok" });
  });

  router.get(
    "/health/ready",
    asyncHandler(async (_req: Request, res: Response) => {
      try {
        await db.query("SELECT 1");
        res.status(200).json({ status: "ok" });
      } catch {
        res.status(503).json({ status: "unavailable" });
      }
    }),
  );

  return router;
};
