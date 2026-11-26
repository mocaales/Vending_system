import { Request, Response, NextFunction } from "express";

export interface ApiErrorBody {
  code: string;
  message: string;
  traceId: string;
  details?: { field: string; issue: string }[];
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details?: { field: string; issue: string }[],
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const DB_DOWN = new Set([
  "ECONNREFUSED",
  "ENOTFOUND",
  "ETIMEDOUT",
  "57P01",
  "57P03",
  "08000",
  "08001",
  "08003",
  "08006",
]);

export const errorHandler = (
  err: unknown,
  req: Request,
  res: Response,
  _next: NextFunction,
): void => {
  const traceId = res.getHeader("X-Trace-Id") as string;

  const send = (
    status: number,
    code: string,
    message: string,
    details?: { field: string; issue: string }[],
  ) => {
    const body: ApiErrorBody = { code, message, traceId };
    if (details?.length) body.details = details;
    res.status(status).json(body);
  };

  if (err instanceof ApiError) {
    send(err.status, err.code, err.message, err.details);
    return;
  }

  const e = err as {
    type?: string;
    status?: number;
    errors?: { path?: string; message: string }[];
    code?: string;
  };

  if (e.type === "entity.parse.failed") {
    send(400, "BAD_REQUEST", "Malformed JSON body");
    return;
  }
  if (e.type === "entity.too.large") {
    send(400, "BAD_REQUEST", "Request body too large");
    return;
  }

  if (Array.isArray(e.errors) && typeof e.status === "number") {
    if (e.status === 404 || e.status === 405) {
      send(404, "NOT_FOUND", "Route not found");
      return;
    }
    if (e.status === 415) {
      send(400, "BAD_REQUEST", "Unsupported content type");
      return;
    }
    const inPath = e.errors.some((x) => /^[./]params[./]/.test(x.path ?? ""));
    if (inPath) {
      send(400, "BAD_REQUEST", "Malformed path parameter");
      return;
    }
    const details = e.errors.map((x) => ({
      field: (x.path ?? "").replace(/^[./](body|query)[./]/, "").replace(/[./]/g, "."),
      issue: x.message,
    }));
    send(422, "VALIDATION_ERROR", "Request validation failed", details);
    return;
  }

  if (e.code && DB_DOWN.has(e.code)) {
    console.error("[db]", e.code, (err as Error).message);
    send(503, "DEPENDENCY_UNAVAILABLE", "Gateway database unavailable");
    return;
  }

  console.error("[error]", err);
  send(500, "INTERNAL_ERROR", "Internal server error");
};
