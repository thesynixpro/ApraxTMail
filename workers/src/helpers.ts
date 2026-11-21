import type { Context } from 'hono';
import { tooMany } from './errors.js';
import type { Env } from './env.js';
import { rateLimit } from './ratelimit.js';

export { ApiError, badRequest, conflict, forbidden, notFound, serviceUnavailable, tooMany, unauthorized } from './errors.js';

/** Throws 429 when the fixed-window limit for [bucket] is exceeded. */
export async function rateLimitGuard(
  c: Context<{ Bindings: Env }>,
  bucket: string,
  limit: number,
  windowSeconds: number,
  message: string,
): Promise<void> {
  const result = await rateLimit(c.env.DB, bucket, limit, windowSeconds);
  if (!result.allowed) throw tooMany(message, result.retryAfter);
}

export function clientIp(c: Context<{ Bindings: Env }>): string {
  return (
    c.req.header('cf-connecting-ip') ??
    c.req.header('x-forwarded-for')?.split(',')[0]?.trim() ??
    'unknown'
  );
}

export async function readJson(c: Context<{ Bindings: Env }>): Promise<Record<string, unknown> | null> {
  try {
    const body = await c.req.json();
    return body && typeof body === 'object' && !Array.isArray(body) ? (body as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}
