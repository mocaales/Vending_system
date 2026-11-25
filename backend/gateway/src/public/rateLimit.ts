// Per-IP rate limiter for public routes: 60 requests per minute.
const WINDOW_MS = 60 * 1000;
const MAX_REQUESTS = 60;

interface Entry {
  count: number;
  resetAt: number;
}

const store = new Map<string, Entry>();

export const checkPublicRateLimit = (ip: string): { limited: boolean; retryAfter: number } => {
  const now = Date.now();
  const entry = store.get(ip);

  if (!entry || now > entry.resetAt) {
    store.set(ip, { count: 1, resetAt: now + WINDOW_MS });
    return { limited: false, retryAfter: 0 };
  }

  entry.count += 1;
  if (entry.count > MAX_REQUESTS) {
    return { limited: true, retryAfter: Math.ceil((entry.resetAt - now) / 1000) };
  }
  return { limited: false, retryAfter: 0 };
};
