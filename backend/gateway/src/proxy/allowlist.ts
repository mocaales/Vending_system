const env = (name: string, fallback: string) => process.env[name] ?? fallback;

// Read URLs at call time so tests can override process.env after module load.
const routeMap = () => [
  { prefix: "/v1/machines", target: env("MACHINE_URL", "http://machine-service:8000") },
  { prefix: "/v1/products", target: env("PRODUCT_URL", "http://product-service:8000") },
  { prefix: "/v1/categories", target: env("PRODUCT_URL", "http://product-service:8000") },
  { prefix: "/v1/inventory", target: env("INVENTORY_URL", "http://inventory-service:8000") },
  { prefix: "/v1/measurements", target: env("MONITORING_URL", "http://monitoring-service:8000") },
  { prefix: "/v1/monitoring", target: env("MONITORING_URL", "http://monitoring-service:8000") },
  { prefix: "/v1/alerts", target: env("MONITORING_URL", "http://monitoring-service:8000") },
  { prefix: "/v1/thresholds", target: env("MONITORING_URL", "http://monitoring-service:8000") },
  { prefix: "/v1/sales", target: env("SALES_URL", "http://sales-service:8000") },
];

// Internal paths that must never be proxied — service-to-service only.
// Matched against the upstream path (after stripping /api).
const BLOCKED: { method: string; pattern: RegExp }[] = [
  { method: "POST", pattern: /^\/v1\/alerts$/ },
  { method: "POST", pattern: /^\/v1\/inventory\/[^/]+\/[^/]+\/decrease$/ },
  { method: "GET", pattern: /^\/v1\/machines\/internal\// },
  { method: "*", pattern: /^\/v1\/device\// },
  // Device-only write paths — operators may only read these
  { method: "POST", pattern: /^\/v1\/sales$/ },
  { method: "POST", pattern: /^\/v1\/measurements$/ },
];

export const isBlocked = (method: string, upstreamPath: string): boolean =>
  BLOCKED.some((b) => (b.method === "*" || b.method === method) && b.pattern.test(upstreamPath));

export const resolveTarget = (upstreamPath: string): string | null => {
  const match = routeMap().find((r) => upstreamPath.startsWith(r.prefix));
  return match ? match.target : null;
};
