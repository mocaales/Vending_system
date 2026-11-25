// In-memory rate limiter: max 5 failed login attempts per IP+email in 15 minutes.
const WINDOW_MS = 15 * 60 * 1000;
const MAX_ATTEMPTS = 5;

interface Entry {
  count: number;
  resetAt: number;
}

const store = new Map<string, Entry>();

export const recordFailure = (ip: string, email: string): void => {
  const key = `${ip}:${email}`;
  const now = Date.now();
  const entry = store.get(key);

  if (!entry || now > entry.resetAt) {
    store.set(key, { count: 1, resetAt: now + WINDOW_MS });
  } else {
    entry.count += 1;
  }
};

export const isBlocked = (ip: string, email: string): { blocked: boolean; retryAfter: number } => {
  const key = `${ip}:${email}`;
  const now = Date.now();
  const entry = store.get(key);

  if (!entry || now > entry.resetAt) return { blocked: false, retryAfter: 0 };
  if (entry.count >= MAX_ATTEMPTS) {
    return { blocked: true, retryAfter: Math.ceil((entry.resetAt - now) / 1000) };
  }
  return { blocked: false, retryAfter: 0 };
};

export const clearAttempts = (ip: string, email: string): void => {
  store.delete(`${ip}:${email}`);
};
