const UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const SLOT = "[A-Za-z0-9_-]{1,8}";

export type Service = "machine" | "monitoring" | "product" | "inventory" | "sales";
export type Method = "GET" | "POST" | "PUT" | "DELETE";

export interface AllowedRoute {
  method: Method;
  template: string;
  pattern: RegExp;
  service: Service;
}

const route = (method: Method, template: string, service: Service): AllowedRoute => ({
  method,
  template,
  service,
  pattern: new RegExp(`^${template.replace(/\{uuid\}/g, UUID).replace(/\{slot\}/g, SLOT)}$`),
});

export const ALLOWED_ROUTES: AllowedRoute[] = [
  // machine
  route("GET", "/v1/machines", "machine"),
  route("POST", "/v1/machines", "machine"),
  route("GET", "/v1/machines/{uuid}", "machine"),
  route("PUT", "/v1/machines/{uuid}", "machine"),
  route("DELETE", "/v1/machines/{uuid}", "machine"),
  route("PUT", "/v1/machines/{uuid}/status", "machine"),
  route("GET", "/v1/machines/{uuid}/keys", "machine"),
  route("POST", "/v1/machines/{uuid}/keys", "machine"),
  route("DELETE", "/v1/machines/{uuid}/keys/{uuid}", "machine"),
  // monitoring
  route("GET", "/v1/measurements/{uuid}", "monitoring"),
  route("DELETE", "/v1/measurements/{uuid}", "monitoring"),
  route("GET", "/v1/monitoring/{uuid}/status", "monitoring"),
  route("GET", "/v1/alerts", "monitoring"),
  route("PUT", "/v1/alerts/{uuid}/acknowledgement", "monitoring"),
  route("PUT", "/v1/alerts/{uuid}/resolution", "monitoring"),
  route("GET", "/v1/thresholds/{uuid}", "monitoring"),
  route("PUT", "/v1/thresholds/{uuid}", "monitoring"),
  route("DELETE", "/v1/thresholds/{uuid}", "monitoring"),
  // product
  route("GET", "/v1/products", "product"),
  route("POST", "/v1/products", "product"),
  route("GET", "/v1/products/{uuid}", "product"),
  route("PUT", "/v1/products/{uuid}", "product"),
  route("DELETE", "/v1/products/{uuid}", "product"),
  route("GET", "/v1/categories", "product"),
  route("POST", "/v1/categories", "product"),
  route("GET", "/v1/categories/{uuid}", "product"),
  route("PUT", "/v1/categories/{uuid}", "product"),
  route("DELETE", "/v1/categories/{uuid}", "product"),
  // inventory
  route("GET", "/v1/inventory/movements", "inventory"),
  route("POST", "/v1/inventory/refill", "inventory"),
  route("DELETE", "/v1/inventory/expired", "inventory"),
  route("GET", "/v1/inventory/product/{uuid}", "inventory"),
  route("GET", "/v1/inventory/{uuid}", "inventory"),
  route("GET", "/v1/inventory/{uuid}/{slot}", "inventory"),
  route("PUT", "/v1/inventory/{uuid}/{slot}", "inventory"),
  route("DELETE", "/v1/inventory/{uuid}/{slot}", "inventory"),
  route("PUT", "/v1/inventory/{uuid}/{slot}/threshold", "inventory"),
  // sales
  route("GET", "/v1/sales", "sales"),
  route("GET", "/v1/sales/stats", "sales"),
  route("GET", "/v1/sales/stats/{uuid}", "sales"),
  route("GET", "/v1/sales/closings", "sales"),
  route("POST", "/v1/sales/closings", "sales"),
  route("DELETE", "/v1/sales/closings", "sales"),
  route("GET", "/v1/sales/closings/{uuid}", "sales"),
  route("PUT", "/v1/sales/closings/{uuid}", "sales"),
  route("DELETE", "/v1/sales/closings/{uuid}", "sales"),
  route("GET", "/v1/sales/{uuid}", "sales"),
  route("PUT", "/v1/sales/{uuid}/reconciliation", "sales"),
];

export const matchRoute = (method: string, upstreamPath: string): AllowedRoute | null =>
  ALLOWED_ROUTES.find((r) => r.method === method && r.pattern.test(upstreamPath)) ?? null;

// Internal paths that must never be proxied — kept as additional safeguard
const BLOCKED: { method: string; pattern: RegExp }[] = [
  { method: "POST", pattern: /^\/v1\/alerts$/ },
  { method: "POST", pattern: /^\/v1\/inventory\/[^/]+\/[^/]+\/decrease$/ },
  { method: "GET", pattern: /^\/v1\/machines\/internal\// },
  { method: "*", pattern: /^\/v1\/device\// },
  { method: "POST", pattern: /^\/v1\/sales$/ },
  { method: "POST", pattern: /^\/v1\/measurements$/ },
];

export const isBlocked = (method: string, upstreamPath: string): boolean =>
  BLOCKED.some((b) => (b.method === "*" || b.method === method) && b.pattern.test(upstreamPath));

const URLS: Record<Service, [string, string]> = {
  machine: ["MACHINE_URL", "http://machine-service:8000"],
  monitoring: ["MONITORING_URL", "http://monitoring-service:8000"],
  product: ["PRODUCT_URL", "http://product-service:8000"],
  inventory: ["INVENTORY_URL", "http://inventory-service:8000"],
  sales: ["SALES_URL", "http://sales-service:8000"],
};

export const serviceUrl = (s: Service): string => process.env[URLS[s][0]] ?? URLS[s][1];
