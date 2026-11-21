import type { Context } from 'hono';
import type { ContentfulStatusCode } from 'hono/utils/http-status';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function badRequest(code: string, message: string, details?: unknown): ApiError {
  return new ApiError(400, code, message, details);
}

export function unauthorized(message = 'Authentication required'): ApiError {
  return new ApiError(401, 'unauthorized', message);
}

export function forbidden(message = 'Not allowed'): ApiError {
  return new ApiError(403, 'forbidden', message);
}

export function notFound(message = 'Not found'): ApiError {
  return new ApiError(404, 'not_found', message);
}

export function conflict(code: string, message: string): ApiError {
  return new ApiError(409, code, message);
}

export function tooMany(message = 'Too many requests', retryAfterSeconds?: number): ApiError {
  return new ApiError(429, 'rate_limited', message, retryAfterSeconds ? { retryAfter: retryAfterSeconds } : undefined);
}

export function serviceUnavailable(code: string, message: string): ApiError {
  return new ApiError(503, code, message);
}

export function errorResponse(c: Context, error: unknown): Response {
  if (error instanceof ApiError) {
    if (error.status === 429 && error.details && typeof error.details === 'object') {
      const retry = (error.details as { retryAfter?: number }).retryAfter;
      if (retry) c.header('Retry-After', String(retry));
    }
    return c.json(
      { error: error.code, message: error.message, ...(error.details !== undefined ? { details: error.details } : {}) },
      error.status as ContentfulStatusCode,
    );
  }
  console.error('unhandled_error', error instanceof Error ? { message: error.message, stack: error.stack } : { error: String(error) });
  return c.json({ error: 'internal_error', message: 'An unexpected error occurred' }, 500);
}
