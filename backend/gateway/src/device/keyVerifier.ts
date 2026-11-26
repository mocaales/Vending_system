import { config } from "../core/config";
import { ApiError } from "../core/errors";
import { isUuid } from "../core/params";

interface KeyVerifyResult {
  machineId: string;
  status: string;
}

export const verifyMachineKey = async (
  machineKey: string,
  traceId: string,
): Promise<KeyVerifyResult> => {
  const machineUrl = process.env.MACHINE_URL ?? "http://machine-service:8000";

  let res: Response;
  try {
    res = await fetch(`${machineUrl}/v1/internal/machine-keys/verify`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-service-token": config.gatewayServiceToken,
        "x-trace-id": traceId,
      },
      body: JSON.stringify({ machineKey }),
      signal: AbortSignal.timeout(parseInt(process.env.PROXY_TIMEOUT_MS ?? "10000", 10)),
    });
  } catch {
    throw new ApiError(503, "DEPENDENCY_UNAVAILABLE", "Machine service unavailable");
  }

  if (res.status === 401) {
    throw new ApiError(401, "UNAUTHENTICATED", "Invalid or revoked machine key");
  }
  if (!res.ok) {
    throw new ApiError(503, "DEPENDENCY_UNAVAILABLE", "Machine service error");
  }

  const result = (await res.json()) as KeyVerifyResult;
  if (!isUuid(result.machineId)) {
    throw new ApiError(503, "DEPENDENCY_UNAVAILABLE", "Machine service returned invalid machineId");
  }

  return result;
};
