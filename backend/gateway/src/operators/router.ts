import { Router, Request, Response, NextFunction } from "express";
import { Pool } from "pg";
import { requireOperator } from "../core/jwt";
import { ApiError } from "../core/errors";
import {
  listOperators,
  getOperator,
  createOperator,
  updateOperator,
  deactivateOperator,
  resetOperatorPassword,
} from "./service";

const parsePagination = (query: Record<string, unknown>) => {
  const limit = Math.min(parseInt(String(query.limit ?? "50"), 10) || 50, 200);
  const offset = parseInt(String(query.offset ?? "0"), 10) || 0;
  return { limit, offset };
};

const validatePassword = (password: unknown, field = "password"): string => {
  if (typeof password !== "string" || password.length < 12 || password.length > 128) {
    throw new ApiError(422, "VALIDATION_ERROR", `${field} must be 12–128 characters`, [
      { field, issue: "must be 12–128 characters" },
    ]);
  }
  return password;
};

export const createOperatorsRouter = (db: Pool): Router => {
  const router = Router();
  const auth = requireOperator(db);

  router.get("/api/v1/operators", auth, async (req: Request, res: Response, next: NextFunction) => {
    try {
      const { limit, offset } = parsePagination(req.query as Record<string, unknown>);
      const status = Array.isArray(req.query.status)
        ? undefined
        : (req.query.status as string | undefined);
      res.json(await listOperators(db, status, limit, offset));
    } catch (err) {
      next(err);
    }
  });

  router.get(
    "/api/v1/operators/:id",
    auth,
    async (req: Request, res: Response, next: NextFunction) => {
      try {
        res.json(await getOperator(db, String(req.params.id)));
      } catch (err) {
        next(err);
      }
    },
  );

  router.post(
    "/api/v1/operators",
    auth,
    async (req: Request, res: Response, next: NextFunction) => {
      const { email, name, password } = req.body ?? {};
      if (!email || !name || !password) {
        return next(
          new ApiError(422, "VALIDATION_ERROR", "email, name and password are required", [
            ...(!email ? [{ field: "email", issue: "required" }] : []),
            ...(!name ? [{ field: "name", issue: "required" }] : []),
            ...(!password ? [{ field: "password", issue: "required" }] : []),
          ]),
        );
      }
      try {
        validatePassword(password);
        const operator = await createOperator(db, String(email), String(name), String(password));
        res.status(201).json(operator);
      } catch (err) {
        next(err);
      }
    },
  );

  router.put(
    "/api/v1/operators/:id",
    auth,
    async (req: Request, res: Response, next: NextFunction) => {
      const { email, name } = req.body ?? {};
      if (!email || !name) {
        return next(
          new ApiError(422, "VALIDATION_ERROR", "email and name are required", [
            ...(!email ? [{ field: "email", issue: "required" }] : []),
            ...(!name ? [{ field: "name", issue: "required" }] : []),
          ]),
        );
      }
      try {
        res.json(await updateOperator(db, String(req.params.id), String(email), String(name)));
      } catch (err) {
        next(err);
      }
    },
  );

  router.delete(
    "/api/v1/operators/:id",
    auth,
    async (req: Request, res: Response, next: NextFunction) => {
      try {
        await deactivateOperator(db, String(req.params.id), req.headers["x-operator-id"] as string);
        res.status(204).send();
      } catch (err) {
        next(err);
      }
    },
  );

  router.put(
    "/api/v1/operators/:id/password",
    auth,
    async (req: Request, res: Response, next: NextFunction) => {
      const { newPassword } = req.body ?? {};
      if (!newPassword) {
        return next(
          new ApiError(422, "VALIDATION_ERROR", "newPassword is required", [
            { field: "newPassword", issue: "required" },
          ]),
        );
      }
      try {
        validatePassword(newPassword, "newPassword");
        await resetOperatorPassword(
          db,
          String(req.params.id),
          req.headers["x-operator-id"] as string,
          String(newPassword),
        );
        res.status(204).send();
      } catch (err) {
        next(err);
      }
    },
  );

  return router;
};
