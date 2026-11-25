import { Router, Request, Response, NextFunction } from "express";
import { Pool } from "pg";
import { requireOperator } from "../core/jwt";
import { ApiError } from "../core/errors";
import { config } from "../core/config";
import { isBlocked, resolveTarget } from "./allowlist";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

const proxyRequest = async (
  req: Request,
  res: Response,
  next: NextFunction,
  target: string,
): Promise<void> => {
  // req.path is stripped of /api/v1 by Express router.use; prepend /v1 for upstream
  // req.url includes query string; req.path does not
  const upstreamUrl = `${target}/v1${req.url}`;

  const headers: Record<string, string> = {
    "content-type": "application/json",
    "x-trace-id": res.getHeader("X-Trace-Id") as string,
    "x-service-token": config.gatewayServiceToken,
  };
  if (req.headers["x-operator-id"]) {
    headers["x-operator-id"] = req.headers["x-operator-id"] as string;
  }

  const hasBody = !SAFE_METHODS.has(req.method) && req.body && Object.keys(req.body).length > 0;

  try {
    const timeoutMs = parseInt(process.env.PROXY_TIMEOUT_MS ?? "10000", 10);
    const upstream = await fetch(upstreamUrl, {
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
    async (req: Request, res: Response, next: NextFunction) => {
      // Strip /api to get the upstream path
      const upstreamPath = req.path === "/" ? "/v1" : `/v1${req.path}`;

      if (isBlocked(req.method, upstreamPath)) {
        return next(new ApiError(404, "NOT_FOUND", "Route not found"));
      }

      const target = resolveTarget(upstreamPath);
      if (!target) {
        return next(new ApiError(404, "NOT_FOUND", "Route not found"));
      }

      await proxyRequest(req, res, next, target);
    },
  );

  return router;
};
