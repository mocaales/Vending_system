import { Request, Response, NextFunction } from "express";
import { ApiError } from "./errors";

const FORBIDDEN = /%2e|%2f|%5c|\\|\/\//i;

export const isSuspiciousPath = (rawPath: string): boolean =>
  FORBIDDEN.test(rawPath) || rawPath.split("/").some((s) => s === "." || s === "..");

export const rejectSuspiciousPath = (req: Request, _res: Response, next: NextFunction): void => {
  const rawPath = req.originalUrl.split("?")[0];
  if (isSuspiciousPath(rawPath)) {
    next(new ApiError(400, "BAD_REQUEST", "Malformed path"));
    return;
  }
  next();
};
