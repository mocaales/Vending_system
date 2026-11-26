import { Request, Response, NextFunction } from "express";
import { v4 as uuidv4 } from "uuid";

const SAFE_TRACE_ID = /^[A-Za-z0-9._-]{1,128}$/;

export const traceIdMiddleware = (req: Request, res: Response, next: NextFunction): void => {
  const incoming = req.headers["x-trace-id"] as string | undefined;
  const traceId = incoming && SAFE_TRACE_ID.test(incoming) ? incoming : uuidv4();
  res.setHeader("X-Trace-Id", traceId);
  next();
};
