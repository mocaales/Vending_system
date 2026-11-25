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
  if (!res.ok) throw new GatewayError(res.status, data as ApiError);
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
