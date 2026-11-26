import { Router, Request, Response as ExpressResponse, NextFunction } from "express";
import { Pool } from "pg";
import { requireOperator } from "../core/jwt";
import { ApiError } from "../core/errors";
import { asyncHandler } from "../core/asyncHandler";
import { uuidParam, isObject } from "../core/params";
import { config } from "../core/config";

interface Section<T> {
  status: "OK" | "STALE" | "UNAVAILABLE";
  asOf: string;
  data: T | null;
}

const timeoutMs = () => parseInt(process.env.PROXY_TIMEOUT_MS ?? "10000", 10);
const svcUrl = (name: string, fallback: string) => process.env[name] ?? fallback;

const fetchSection = async <T>(url: string, traceId: string): Promise<Section<T>> => {
  const asOf = new Date().toISOString();
  try {
    const res = await fetch(url, {
      headers: {
        "x-service-token": config.gatewayServiceToken,
        "x-trace-id": traceId,
      },
      signal: AbortSignal.timeout(timeoutMs()),
    });
    if (!res.ok) return { status: "UNAVAILABLE", asOf, data: null };
    let body: unknown;
    try {
      body = await res.json();
    } catch {
      return { status: "UNAVAILABLE", asOf, data: null };
    }
    if (!isObject(body)) return { status: "UNAVAILABLE", asOf, data: null };
    return { status: "OK", asOf, data: body as T };
  } catch {
    return { status: "UNAVAILABLE", asOf, data: null };
  }
};

const todayUtcRange = () => {
  const now = new Date();
  const from = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const to = new Date(from.getTime() + 86400000);
  return { from: from.toISOString(), to: to.toISOString() };
};

export const createOperatorRouter = (db: Pool): Router => {
  const router = Router();
  const auth = requireOperator(db);

  router.param("id", uuidParam);

  router.get(
    "/api/v1/operator/dashboard",
    auth,
    asyncHandler(async (req: Request, res: ExpressResponse) => {
      const traceId = res.getHeader("X-Trace-Id") as string;
      const machineUrl = svcUrl("MACHINE_URL", config.machineUrl);
      const monitoringUrl = svcUrl("MONITORING_URL", config.monitoringUrl);
      const salesUrl = svcUrl("SALES_URL", config.salesUrl);
      const { from, to } = todayUtcRange();

      const [machines, alarms, sales] = await Promise.all([
        fetchSection(`${machineUrl}/v1/machines`, traceId),
        fetchSection(`${monitoringUrl}/v1/alerts?status=ACTIVE`, traceId),
        fetchSection(`${salesUrl}/v1/sales/stats?from=${from}&to=${to}`, traceId),
      ]);

      res.json({ machines, alarms, sales });
    }),
  );

  router.get(
    "/api/v1/operator/machines/:id/overview",
    auth,
    asyncHandler(async (req: Request, res: ExpressResponse, next: NextFunction) => {
      const traceId = res.getHeader("X-Trace-Id") as string;
      const machineId = req.params.id;
      const machineUrl = svcUrl("MACHINE_URL", config.machineUrl);
      const monitoringUrl = svcUrl("MONITORING_URL", config.monitoringUrl);
      const inventoryUrl = svcUrl("INVENTORY_URL", config.inventoryUrl);

      const machineRes = await fetch(`${machineUrl}/v1/machines/${encodeURIComponent(machineId)}`, {
        headers: {
          "x-service-token": config.gatewayServiceToken,
          "x-trace-id": traceId,
        },
        signal: AbortSignal.timeout(timeoutMs()),
      });

      if (machineRes.status === 404) {
        return next(new ApiError(404, "MACHINE_NOT_FOUND", "Machine not found"));
      }

      let machineData: unknown = null;
      if (machineRes.ok) {
        try {
          machineData = await machineRes.json();
        } catch {
          machineData = null;
        }
        if (!isObject(machineData)) machineData = null;
      }

      const machineSection: Section<unknown> =
        machineRes.ok && machineData !== null
          ? { status: "OK", asOf: new Date().toISOString(), data: machineData }
          : { status: "UNAVAILABLE", asOf: new Date().toISOString(), data: null };

      const [monitoring, thresholds, alarms, inventory] = await Promise.all([
        fetchSection(
          `${monitoringUrl}/v1/monitoring/${encodeURIComponent(machineId)}/status`,
          traceId,
        ),
        fetchSection(`${monitoringUrl}/v1/thresholds/${encodeURIComponent(machineId)}`, traceId),
        fetchSection(
          `${monitoringUrl}/v1/alerts?machineId=${encodeURIComponent(machineId)}`,
          traceId,
        ),
        fetchSection(`${inventoryUrl}/v1/inventory/${encodeURIComponent(machineId)}`, traceId),
      ]);

      res.json({ machine: machineSection, monitoring, thresholds, alarms, inventory });
    }),
  );

  return router;
};
