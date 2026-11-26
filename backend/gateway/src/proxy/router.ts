import { Router, Request, Response, NextFunction } from "express";
import { Pool } from "pg";
import { requireOperator } from "../core/jwt";
import { ApiError } from "../core/errors";
import { config } from "../core/config";
import { asyncHandler } from "../core/asyncHandler";
import { isBlocked, matchRoute, serviceUrl } from "./allowlist";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

const proxyRequest = async (
  req: Request,
  res: Response,
  next: NextFunction,
  targetUrl: string,
): Promise<void> => {
  const headers: Record<string, string> = {
    "content-type": "application/json",
    "x-trace-id": res.getHeader("X-Trace-Id") as string,
    "x-service-token": config.gatewayServiceToken,
  };
  if (req.headers["x-operator-id"]) {
    headers["x-operator-id"] = req.headers["x-operator-id"] as string;
    // Forward original JWT so downstream services can verify it themselves
    if (req.headers["authorization"]) {
      headers["authorization"] = req.headers["authorization"] as string;
    }
  }

  const hasBody = !SAFE_METHODS.has(req.method) && req.body && Object.keys(req.body).length > 0;

  try {
    const timeoutMs = config.proxyTimeoutMs;
    const upstream = await fetch(targetUrl, {
      method: req.method,
      headers,
      body: hasBody ? JSON.stringify(req.body) : undefined,
      signal: AbortSignal.timeout(timeoutMs),
    });

    const traceId = upstream.headers.get("x-trace-id");
    if (traceId) res.setHeader("X-Trace-Id", traceId);

    const contentType = upstream.headers.get("content-type") ?? "";
    if (upstream.status === 204 || upstream.headers.get("content-length") === "0") {
      res.status(upstream.status).send();
      return;
    }

    if (contentType.includes("application/json")) {
      const body = await upstream.json();
      res.status(upstream.status).json(body);
    } else {
      const text = await upstream.text();
      res.status(upstream.status).send(text);
    }
  } catch (err) {
    const name = (err as Error).name;
    if (name === "TimeoutError" || name === "AbortError") {
      next(new ApiError(504, "UPSTREAM_TIMEOUT", "Upstream service timed out"));
    } else {
      next(new ApiError(503, "DEPENDENCY_UNAVAILABLE", "Upstream service unavailable"));
    }
  }
};

export const createProxyRouter = (db: Pool): Router => {
  const router = Router();

  router.use(
    "/api/v1",
    requireOperator(db),
    asyncHandler(async (req: Request, res: Response, next: NextFunction) => {
      const upstreamPath = `/v1${req.path}`;

      if (isBlocked(req.method, upstreamPath)) {
        return next(new ApiError(404, "NOT_FOUND", "Route not found"));
      }

      const matched = matchRoute(req.method, upstreamPath);
      if (!matched) {
        return next(new ApiError(404, "NOT_FOUND", "Route not found"));
      }

      const query = req.originalUrl.includes("?")
        ? req.originalUrl.slice(req.originalUrl.indexOf("?"))
        : "";

      await proxyRequest(req, res, next, `${serviceUrl(matched.service)}${upstreamPath}${query}`);
    }),
  );

  return router;
};
