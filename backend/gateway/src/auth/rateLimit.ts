const WINDOW_MS = 15 * 60 * 1000;
const MAX_ATTEMPTS = 5;
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

export const recordFailure = (ip: string, normalizedEmail: string): void => {
  const key = `${ip}|${normalizedEmail}`;
  const now = Date.now();
  const entry = store.get(key);

  if (!entry || now > entry.resetAt) {
    if (store.size >= MAX_ENTRIES) {
      pruneExpired(now);
      if (store.size >= MAX_ENTRIES) store.delete(store.keys().next().value as string);
    }
    store.set(key, { count: 1, resetAt: now + WINDOW_MS });
  } else {
    entry.count += 1;
  }
};

export const isBlocked = (
  ip: string,
  normalizedEmail: string,
): { blocked: boolean; retryAfter: number } => {
  const key = `${ip}|${normalizedEmail}`;
  const now = Date.now();
  const entry = store.get(key);

  if (!entry || now > entry.resetAt) return { blocked: false, retryAfter: 0 };
  if (entry.count >= MAX_ATTEMPTS) {
    return { blocked: true, retryAfter: Math.ceil((entry.resetAt - now) / 1000) };
  }
  return { blocked: false, retryAfter: 0 };
};

export const clearAttempts = (ip: string, normalizedEmail: string): void => {
  store.delete(`${ip}|${normalizedEmail}`);
};
