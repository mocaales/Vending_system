import { Router, Request, Response as ExpressResponse, NextFunction } from "express";
import { config } from "../core/config";
import { ApiError } from "../core/errors";
import { checkPublicRateLimit } from "./rateLimit";

const TIMEOUT_MS = () => parseInt(process.env.PROXY_TIMEOUT_MS ?? "10000", 10);
const MACHINE_URL = () => process.env.MACHINE_URL ?? config.machineUrl;
const INVENTORY_URL = () => process.env.INVENTORY_URL ?? config.inventoryUrl;

const rateLimitMiddleware = (req: Request, res: ExpressResponse, next: NextFunction): void => {
  const ip = req.ip ?? "unknown";
  const { limited, retryAfter } = checkPublicRateLimit(ip);
  if (limited) {
    res.setHeader("Retry-After", String(retryAfter));
    next(new ApiError(429, "RATE_LIMITED", "Too many requests"));
    return;
  }
  next();
};

// Returns true if the slot's inventory is expired in Europe/Ljubljana timezone (rule B4).
const isExpired = (expiryDate: string | null): boolean => {
  if (!expiryDate) return false;
  const today = new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Ljubljana" }).format(
    new Date(),
  );
  return today > expiryDate;
};

const fetchUpstream = async (url: string, traceId: string): Promise<globalThis.Response | null> => {
  try {
    return await fetch(url, {
      headers: {
        "x-service-token": config.gatewayServiceToken,
        "x-trace-id": traceId,
      },
      signal: AbortSignal.timeout(TIMEOUT_MS()),
    });
  } catch {
    return null;
  }
};

export const createPublicRouter = (): Router => {
  const router = Router();

  router.get(
    "/api/v1/public/machines",
    rateLimitMiddleware,
    async (req: Request, res: ExpressResponse, next: NextFunction) => {
      const traceId = res.getHeader("X-Trace-Id") as string;
      const upstream = await fetchUpstream(`${MACHINE_URL()}/v1/machines`, traceId);

      if (!upstream) {
        return next(new ApiError(503, "DEPENDENCY_UNAVAILABLE", "Machine service unavailable"));
      }
      if (!upstream.ok) {
        return next(new ApiError(503, "DEPENDENCY_UNAVAILABLE", "Machine service error"));
      }

      const data = (await upstream.json()) as {
        items: {
          id: string;
          name: string;
          address: string;
          latitude: number;
          longitude: number;
          status: string;
        }[];
        total: number;
      };

      // Hide RETIRED; include ACTIVE and SERVICE (SERVICE shown as unavailable)
      const items = data.items
        .filter((m) => m.status !== "RETIRED")
        .map((m) => ({
          id: m.id,
          name: m.name,
          address: m.address,
          latitude: m.latitude,
          longitude: m.longitude,
          available: m.status === "ACTIVE",
        }));

      res.json({ items, total: items.length });
    },
  );

  router.get(
    "/api/v1/public/machines/:id/availability",
    rateLimitMiddleware,
    async (req: Request, res: ExpressResponse, next: NextFunction) => {
      const traceId = res.getHeader("X-Trace-Id") as string;
      const machineId = String(req.params.id);
      const asOf = new Date().toISOString();

      // Verify machine exists and is not RETIRED
      const machineRes = await fetchUpstream(`${MACHINE_URL()}/v1/machines/${machineId}`, traceId);
      if (!machineRes) {
        return next(new ApiError(503, "DEPENDENCY_UNAVAILABLE", "Machine service unavailable"));
      }
      if (machineRes.status === 404) {
        return next(new ApiError(404, "MACHINE_NOT_FOUND", "Machine not found"));
      }
      if (!machineRes.ok) {
        return next(new ApiError(503, "DEPENDENCY_UNAVAILABLE", "Machine service error"));
      }

      const machine = (await machineRes.json()) as { id: string; name: string; status: string };
      if (machine.status === "RETIRED") {
        return next(new ApiError(404, "MACHINE_NOT_FOUND", "Machine not found"));
      }

      // Fetch inventory; if unavailable return UNAVAILABLE status (never empty list as substitute)
      const invRes = await fetchUpstream(`${INVENTORY_URL()}/v1/inventory/${machineId}`, traceId);

      if (!invRes || !invRes.ok) {
        return res.json({
          machineId,
          status: "UNAVAILABLE",
          asOf,
          slots: null,
        });
      }

      const inv = (await invRes.json()) as {
        items: {
          slot: string;
          productId: string | null;
          expiryDate: string | null;
          quantity: number;
        }[];
      };

      // Filter: exclude empty slots and expired inventory
      const slots = inv.items
        .filter((s) => s.productId !== null && s.quantity > 0 && !isExpired(s.expiryDate))
        .map((s) => ({
          slotCode: s.slot,
          productId: s.productId,
        }));

      res.json({ machineId, status: "OK", asOf, slots });
    },
  );

  return router;
};
