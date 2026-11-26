import { Router, Request, Response, NextFunction } from "express";
import { Pool } from "pg";
import { requireOperator } from "../core/jwt";
import { ApiError } from "../core/errors";
import { asyncHandler } from "../core/asyncHandler";
import { normalizeEmail } from "../core/email";
import { recordFailure, isBlocked, clearAttempts } from "./rateLimit";
import { login, getMe, revokeSession, changePassword } from "./service";

export const createAuthRouter = (db: Pool): Router => {
  const router = Router();

  router.post(
    "/api/v1/auth/login",
    asyncHandler(async (req: Request, res: Response, next: NextFunction) => {
      const { email, password } = req.body ?? {};
      if (!email || !password) {
        return next(
          new ApiError(422, "VALIDATION_ERROR", "email and password are required", [
            ...(!email ? [{ field: "email", issue: "required" }] : []),
            ...(!password ? [{ field: "password", issue: "required" }] : []),
          ]),
        );
      }

      const normalizedEmail = normalizeEmail(String(email));
      const ip = req.ip ?? "unknown";
      const { blocked, retryAfter } = isBlocked(ip, normalizedEmail);
      if (blocked) {
        res.setHeader("Retry-After", String(retryAfter));
        return next(new ApiError(429, "RATE_LIMITED", "Too many failed attempts"));
      }

      try {
        const token = await login(db, normalizedEmail, password);
        clearAttempts(ip, normalizedEmail);
        res.status(200).json(token);
      } catch (err) {
        if (err instanceof ApiError && err.code === "INVALID_CREDENTIALS") {
          recordFailure(ip, normalizedEmail);
        }
        next(err);
      }
    }),
  );

  router.get(
    "/api/v1/auth/me",
    requireOperator(db),
    asyncHandler(async (req: Request, res: Response) => {
      const operator = await getMe(db, req.headers["x-operator-id"] as string);
      res.status(200).json(operator);
    }),
  );

  router.delete(
    "/api/v1/auth/session",
    requireOperator(db),
    asyncHandler(async (req: Request, res: Response) => {
      await revokeSession(db, req.headers["x-operator-id"] as string);
      res.status(204).send();
    }),
  );

  router.put(
    "/api/v1/auth/password",
    requireOperator(db),
    asyncHandler(async (req: Request, res: Response, next: NextFunction) => {
      const { currentPassword, newPassword } = req.body ?? {};
      if (!currentPassword || !newPassword) {
        return next(
          new ApiError(422, "VALIDATION_ERROR", "currentPassword and newPassword are required", [
            ...(!currentPassword ? [{ field: "currentPassword", issue: "required" }] : []),
            ...(!newPassword ? [{ field: "newPassword", issue: "required" }] : []),
          ]),
        );
      }
      if (newPassword.length < 12 || newPassword.length > 128) {
        return next(
          new ApiError(422, "VALIDATION_ERROR", "newPassword must be 12–128 characters", [
            { field: "newPassword", issue: "must be 12–128 characters" },
          ]),
        );
      }
      const token = await changePassword(
        db,
        req.headers["x-operator-id"] as string,
        currentPassword,
        newPassword,
      );
      res.status(200).json(token);
    }),
  );

  return router;
};
