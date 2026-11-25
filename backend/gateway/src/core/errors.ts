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

export const errorHandler = (
  err: unknown,
  req: Request,
  res: Response,
  _next: NextFunction,
): void => {
  const traceId = res.getHeader("X-Trace-Id") as string;

  if (err instanceof ApiError) {
    const body: ApiErrorBody = { code: err.code, message: err.message, traceId };
    if (err.details) body.details = err.details;
    res.status(err.status).json(body);
    return;
  }

  // express-openapi-validator errors
  const validationErr = err as { status?: number; errors?: { path: string; message: string }[] };
  if (validationErr.status === 404) {
    res.status(404).json({ code: "NOT_FOUND", message: "Route not found", traceId });
    return;
  }
  if (validationErr.status === 400 || validationErr.status === 422) {
    const details = (validationErr.errors ?? []).map((e) => ({
      field: e.path,
      issue: e.message,
    }));
    res.status(422).json({
      code: "VALIDATION_ERROR",
      message: "Request validation failed",
      traceId,
      details,
    } satisfies ApiErrorBody);
    return;
  }

  console.error("[error]", err);
  res.status(500).json({ code: "INTERNAL_ERROR", message: "Internal server error", traceId });
};
