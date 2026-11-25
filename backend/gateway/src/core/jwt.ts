import jwt from "jsonwebtoken";
import { Request, Response, NextFunction } from "express";
import { Pool } from "pg";
import { config } from "./config";
import { ApiError } from "./errors";

export interface OperatorPayload {
  sub: string;
  email: string;
  tokenVersion: number;
}

export const signToken = (payload: OperatorPayload): string =>
  jwt.sign(payload, config.jwtSecret, {
    algorithm: "HS256",
    expiresIn: config.jwtExpiresInSeconds,
  });

export const verifyToken = (token: string): OperatorPayload => {
  try {
    return jwt.verify(token, config.jwtSecret, { algorithms: ["HS256"] }) as OperatorPayload;
  } catch {
    throw new ApiError(401, "UNAUTHENTICATED", "Invalid or expired token");
  }
};

// Strips client-supplied trusted headers and adds gateway's own.
// Must run before requireOperator so headers are clean regardless of auth outcome.
export const sanitizeHeaders = (req: Request, _res: Response, next: NextFunction): void => {
  delete req.headers["x-operator-id"];
  delete req.headers["x-machine-id"];
  delete req.headers["x-service-token"];
  next();
};

export const requireOperator = (db: Pool) => {
  return async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    const authHeader = req.headers["authorization"];
    if (!authHeader?.startsWith("Bearer ")) {
      next(new ApiError(401, "UNAUTHENTICATED", "Missing authorization header"));
      return;
    }

    const token = authHeader.slice(7);
    let payload: OperatorPayload;
    try {
      payload = verifyToken(token);
    } catch (err) {
      next(err);
      return;
    }

    const result = await db.query("SELECT id, status, token_version FROM operators WHERE id = $1", [
      payload.sub,
    ]);

    const operator = result.rows[0];
    if (
      !operator ||
      operator.status !== "ACTIVE" ||
      operator.token_version !== payload.tokenVersion
    ) {
      next(new ApiError(401, "UNAUTHENTICATED", "Token revoked or account inactive"));
      return;
    }

    req.headers["x-operator-id"] = payload.sub;
    req.headers["x-service-token"] = config.gatewayServiceToken;
    next();
  };
};
