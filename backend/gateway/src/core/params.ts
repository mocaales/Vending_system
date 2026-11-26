import { Request, Response, NextFunction } from "express";
import { ApiError } from "./errors";

export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const isUuid = (v: unknown): v is string => typeof v === "string" && UUID_RE.test(v);

export const isObject = (x: unknown): x is Record<string, unknown> =>
  typeof x === "object" && x !== null && !Array.isArray(x);

export const isPage = <T = unknown>(x: unknown): x is { items: T[]; total: number } =>
  isObject(x) &&
  Array.isArray((x as Record<string, unknown>).items) &&
  typeof (x as Record<string, unknown>).total === "number";

export const uuidParam = (
  req: Request,
  _res: Response,
  next: NextFunction,
  value: string,
  name: string,
): void => {
  if (!isUuid(value)) {
    next(new ApiError(400, "BAD_REQUEST", `${name} must be a UUID`));
    return;
  }
  next();
};
