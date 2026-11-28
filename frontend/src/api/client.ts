const apiUrl = () => import.meta.env.VITE_API_URL ?? "";

export interface ApiError {
  code: string;
  message: string;
  traceId: string;
  details?: { field: string; issue: string }[];
}

export class GatewayError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiError,
  ) {
    super(body.message);
    this.name = "GatewayError";
  }
}

const getToken = () => sessionStorage.getItem("accessToken");

const request = async <T>(
  method: string,
  path: string,
  body?: unknown,
  auth = true,
): Promise<T> => {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (auth) {
    const token = getToken();
    if (token) headers["authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${apiUrl()}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 204) return undefined as T;

  const data = await res.json().catch(() => ({ code: "INTERNAL_ERROR", message: res.statusText }));
  if (!res.ok) {
    // Auto-logout on expired/revoked token
    if (res.status === 401 && auth) {
      sessionStorage.removeItem("accessToken");
      window.location.href = "/login";
    }
    throw new GatewayError(res.status, data as ApiError);
  }
  return data as T;
};

// Auth
export interface TokenOut {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

export interface OperatorOut {
  id: string;
  email: string;
  name: string;
  status: string;
  createdAt: string;
  deactivatedAt?: string;
}

export const authApi = {
  login: (email: string, password: string) =>
    request<TokenOut>("POST", "/api/v1/auth/login", { email, password }, false),

  me: () => request<OperatorOut>("GET", "/api/v1/auth/me"),

  logout: () => request<void>("DELETE", "/api/v1/auth/session"),

  changePassword: (currentPassword: string, newPassword: string) =>
    request<TokenOut>("PUT", "/api/v1/auth/password", { currentPassword, newPassword }),
};

// Operators
export interface OperatorIn {
  email: string;
  name: string;
  password: string;
}

export interface PageOut<T> {
  items: T[];
  total: number;
  limit: number;
  offset: number;
}

export const operatorsApi = {
  list: (status?: string) =>
    request<PageOut<OperatorOut>>("GET", `/api/v1/operators${status ? `?status=${status}` : ""}`),
  get: (id: string) => request<OperatorOut>("GET", `/api/v1/operators/${id}`),
  create: (data: OperatorIn) => request<OperatorOut>("POST", "/api/v1/operators", data),
  update: (id: string, data: { email: string; name: string }) =>
    request<OperatorOut>("PUT", `/api/v1/operators/${id}`, data),
  deactivate: (id: string) => request<void>("DELETE", `/api/v1/operators/${id}`),
  resetPassword: (id: string, newPassword: string) =>
    request<void>("PUT", `/api/v1/operators/${id}/password`, { newPassword }),
};

// Public
export interface PublicMachine {
  id: string;
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  available: boolean;
}

export interface PublicSlot {
  slotCode: string;
  productId: string;
}

export interface PublicAvailability {
  machineId: string;
  status: "OK" | "UNAVAILABLE";
  asOf: string;
  slots: PublicSlot[] | null;
}

export const publicApi = {
  machines: () =>
    request<{ items: PublicMachine[]; total: number }>(
      "GET",
      "/api/v1/public/machines",
      undefined,
      false,
    ),
  availability: (id: string) =>
    request<PublicAvailability>(
      "GET",
      `/api/v1/public/machines/${id}/availability`,
      undefined,
      false,
    ),
};

// Operator views
export interface Section<T> {
  status: "OK" | "STALE" | "UNAVAILABLE";
  asOf: string;
  data: T | null;
}

export interface DashboardOut {
  machines: Section<{ items: unknown[]; total: number }>;
  alarms: Section<{ items: unknown[]; total: number }>;
  sales: Section<unknown>;
}

export interface MachineOverviewOut {
  machine: Section<unknown>;
  monitoring: Section<unknown>;
  thresholds: Section<unknown>;
  alarms: Section<{ items: unknown[] }>;
  inventory: Section<{ items: unknown[] }>;
}

export const operatorViewsApi = {
  dashboard: () => request<DashboardOut>("GET", "/api/v1/operator/dashboard"),
  machineOverview: (id: string) =>
    request<MachineOverviewOut>("GET", `/api/v1/operator/machines/${id}/overview`),
};

// Machines (proxy)
export const machinesApi = {
  list: () => request<PageOut<unknown>>("GET", "/api/v1/machines"),
  get: (id: string) => request<unknown>("GET", `/api/v1/machines/${id}`),
  create: (data: unknown) => request<unknown>("POST", "/api/v1/machines", data),
  update: (id: string, data: unknown) => request<unknown>("PUT", `/api/v1/machines/${id}`, data),
  retire: (id: string) => request<void>("DELETE", `/api/v1/machines/${id}`),
  setStatus: (id: string, status: string) =>
    request<unknown>("PUT", `/api/v1/machines/${id}/status`, { status }),
  listKeys: (id: string) => request<unknown>("GET", `/api/v1/machines/${id}/keys`),
  createKey: (id: string, label?: string) =>
    request<unknown>("POST", `/api/v1/machines/${id}/keys`, { label }),
  deleteKey: (machineId: string, keyId: string) =>
    request<void>("DELETE", `/api/v1/machines/${machineId}/keys/${keyId}`),
};

// Categories and products (Product, docs/03 §6)
export interface CategoryIn {
  name: string;
  description?: string;
}

export interface CategoryOut {
  id: string;
  name: string;
  description: string | null;
}

export interface ProductIn {
  name: string;
  categoryId: string;
  referencePrice: string;
  currency: string;
  weightGrams: number;
  allergens: string[];
  imageUrl: string;
}

export interface ProductOut extends ProductIn {
  id: string;
  categoryName: string;
  status: "ACTIVE" | "ARCHIVED";
}

export const categoriesApi = {
  list: () => request<PageOut<CategoryOut>>("GET", "/api/v1/categories?limit=200"),
  create: (data: CategoryIn) => request<CategoryOut>("POST", "/api/v1/categories", data),
  update: (id: string, data: CategoryIn) =>
    request<CategoryOut>("PUT", `/api/v1/categories/${id}`, data),
  remove: (id: string) => request<void>("DELETE", `/api/v1/categories/${id}`),
};

export const productsApi = {
  list: (status: string, q = "") => {
    const params = new URLSearchParams({ status, limit: "200" });
    if (q) params.set("q", q);
    return request<PageOut<ProductOut>>("GET", `/api/v1/products?${params}`);
  },
  create: (data: ProductIn) => request<ProductOut>("POST", "/api/v1/products", data),
  update: (id: string, data: ProductIn) =>
    request<ProductOut>("PUT", `/api/v1/products/${id}`, data),
  archive: (id: string) => request<void>("DELETE", `/api/v1/products/${id}`),
};

// Inventory (docs/03 §7)
// Avtomat s predali, kot ga rabita strani zaloge (machinesApi vrne unknown).
export interface MachineWithSlots {
  id: string;
  name: string;
  status: string;
  slots: { slotCode: string; label?: string | null }[];
}

export interface SlotOut {
  machineId: string;
  slot: string;
  productId: string | null;
  batchCode: string | null;
  expiryDate: string | null;
  quantity: number;
  lowStockThreshold: number;
}

export interface SlotSet {
  productId: string;
  batchCode: string;
  expiryDate: string;
  quantity: number;
  lowStockThreshold: number;
}

export interface RefillIn {
  refillId: string; // isti refillId ne podvoji polnitve
  machineId: string;
  slot: string;
  productId: string;
  batchCode: string;
  expiryDate: string;
  quantity: number;
}

export type MovementType = "SET" | "REFILL" | "REMOVAL" | "SALE";

export interface MovementOut {
  id: string;
  machineId: string;
  slot: string;
  type: MovementType;
  productId: string;
  batchCode: string | null;
  quantity: number;
  saleId: string | null;
  refillId: string | null;
  occurredAt: string;
}

export interface RemovedOut {
  slot: string;
  productId: string;
  batchCode: string | null;
  quantity: number;
}

export const inventoryApi = {
  productSlots: (productId: string) =>
    request<PageOut<SlotOut>>("GET", `/api/v1/inventory/product/${productId}`),
  machine: (machineId: string) =>
    request<PageOut<SlotOut>>("GET", `/api/v1/inventory/${machineId}?limit=200`),
  setSlot: (machineId: string, slot: string, data: SlotSet) =>
    request<SlotOut>("PUT", `/api/v1/inventory/${machineId}/${slot}`, data),
  setThreshold: (machineId: string, slot: string, lowStockThreshold: number) =>
    request<SlotOut>("PUT", `/api/v1/inventory/${machineId}/${slot}/threshold`, {
      lowStockThreshold,
    }),
  empty: (machineId: string, slot: string) =>
    request<void>("DELETE", `/api/v1/inventory/${machineId}/${slot}`),
  refill: (data: RefillIn) => request<SlotOut>("POST", "/api/v1/inventory/refill", data),
  removeExpired: (machineId: string) =>
    request<{ removed: RemovedOut[] }>(
      "DELETE",
      `/api/v1/inventory/expired?machineId=${machineId}`,
    ),
  movements: (filters: Record<string, string>) => {
    const params = new URLSearchParams({ ...filters, limit: "200" });
    return request<PageOut<MovementOut>>("GET", `/api/v1/inventory/movements?${params}`);
  },
};
