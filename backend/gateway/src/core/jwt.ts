import jwt from "jsonwebtoken";
import { Request, Response, NextFunction, RequestHandler } from "express";
import { Pool } from "pg";
import { config } from "./config";
import { ApiError } from "./errors";
import { asyncHandler } from "./asyncHandler";

export interface OperatorPayload {
  sub: string;
  name: string;
  email: string;
  tokenVersion: number;
  iat?: number;
  exp?: number;
}

export const signToken = (payload: OperatorPayload): string =>
  jwt.sign(payload, config.jwtSecret, {
    algorithm: "HS256",
    expiresIn: config.jwtExpiresInSeconds,
  });

export const verifyToken = (token: string): OperatorPayload => {
  try {
    const decoded = jwt.verify(token, config.jwtSecret, {
      algorithms: ["HS256"],
    }) as OperatorPayload;
    if (
      !decoded.sub ||
      !decoded.name ||
      decoded.tokenVersion === undefined ||
      !decoded.iat ||
      !decoded.exp
    ) {
      throw new ApiError(401, "TOKEN_INVALID", "Invalid token.");
    }
    return decoded;
  } catch (err) {
    if (err instanceof ApiError) throw err;
    if (err instanceof jwt.TokenExpiredError)
      throw new ApiError(401, "TOKEN_EXPIRED", "Token expired.");
    throw new ApiError(401, "TOKEN_INVALID", "Invalid token.");
  }
};

export const sanitizeHeaders = (req: Request, _res: Response, next: NextFunction): void => {
  delete req.headers["x-operator-id"];
  delete req.headers["x-machine-id"];
  delete req.headers["x-service-token"];
  next();
};

export const requireOperator = (db: Pool): RequestHandler =>
  asyncHandler(async (req: Request, _res: Response, next: NextFunction) => {
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
      next(new ApiError(401, "TOKEN_INVALID", "Token revoked or account inactive"));
      return;
    }

    req.headers["x-operator-id"] = payload.sub;
    req.headers["x-service-token"] = config.gatewayServiceToken;
    next();
  });
