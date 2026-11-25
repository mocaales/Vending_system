import { Router, Request, Response, NextFunction } from "express";
import { config } from "../core/config";
import { ApiError } from "../core/errors";
import { verifyMachineKey } from "./keyVerifier";

const forwardToService = async (
  req: Request,
  res: Response,
  next: NextFunction,
  targetUrl: string,
  machineId: string,
): Promise<void> => {
  const traceId = res.getHeader("X-Trace-Id") as string;

  // machineId in body must match the key
  if (req.body?.machineId && req.body.machineId !== machineId) {
    return next(new ApiError(403, "MACHINE_KEY_MISMATCH", "Machine key does not match machineId"));
  }

  try {
    const upstream = await fetch(targetUrl, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-service-token": config.gatewayServiceToken,
        "x-machine-id": machineId,
        "x-trace-id": traceId,
      },
      body: JSON.stringify(req.body),
      signal: AbortSignal.timeout(parseInt(process.env.PROXY_TIMEOUT_MS ?? "10000", 10)),
    });

    const contentType = upstream.headers.get("content-type") ?? "";
    if (upstream.status === 204 || upstream.headers.get("content-length") === "0") {
      res.status(upstream.status).send();
      return;
    }
    if (contentType.includes("application/json")) {
      res.status(upstream.status).json(await upstream.json());
    } else {
      res.status(upstream.status).send(await upstream.text());
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

export const createDeviceRouter = (): Router => {
  const router = Router();

  router.post("/api/v1/device/sales", async (req: Request, res: Response, next: NextFunction) => {
    const machineKey = req.headers["x-machine-key"] as string | undefined;
    if (!machineKey) {
      return next(new ApiError(401, "UNAUTHENTICATED", "Missing X-Machine-Key header"));
    }

    let machineId: string;
    try {
      const result = await verifyMachineKey(machineKey, res.getHeader("X-Trace-Id") as string);
      machineId = result.machineId;
    } catch (err) {
      return next(err);
    }

    const salesUrl = process.env.SALES_URL ?? "http://sales-service:8000";
    await forwardToService(req, res, next, `${salesUrl}/v1/sales`, machineId);
  });

  router.post(
    "/api/v1/device/measurements",
    async (req: Request, res: Response, next: NextFunction) => {
      const machineKey = req.headers["x-machine-key"] as string | undefined;
      if (!machineKey) {
        return next(new ApiError(401, "UNAUTHENTICATED", "Missing X-Machine-Key header"));
      }

      let machineId: string;
      try {
        const result = await verifyMachineKey(machineKey, res.getHeader("X-Trace-Id") as string);
        machineId = result.machineId;
      } catch (err) {
        return next(err);
      }

      const monitoringUrl = process.env.MONITORING_URL ?? "http://monitoring-service:8000";
      await forwardToService(req, res, next, `${monitoringUrl}/v1/measurements`, machineId);
    },
  );

  return router;
};
