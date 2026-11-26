const WINDOW_MS = 60 * 1000;
const MAX_REQUESTS = 60;
const MAX_ENTRIES = 10_000;

interface Entry {
  count: number;
  resetAt: number;
}

const store = new Map<string, Entry>();

export const pruneExpired = (now = Date.now()): void => {
  for (const [k, e] of store) if (e.resetAt <= now) store.delete(k);
};

setInterval(() => pruneExpired(), 60_000).unref();

export const _size = (): number => store.size;

export const checkPublicRateLimit = (ip: string): { limited: boolean; retryAfter: number } => {
  const now = Date.now();
  const entry = store.get(ip);

  if (!entry || now > entry.resetAt) {
    if (store.size >= MAX_ENTRIES) {
      pruneExpired(now);
      if (store.size >= MAX_ENTRIES) store.delete(store.keys().next().value as string);
    }
    store.set(ip, { count: 1, resetAt: now + WINDOW_MS });
    return { limited: false, retryAfter: 0 };
  }

  entry.count += 1;
  if (entry.count > MAX_REQUESTS) {
    return { limited: true, retryAfter: Math.ceil((entry.resetAt - now) / 1000) };
  }
  return { limited: false, retryAfter: 0 };
};
