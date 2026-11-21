import { createMiddleware } from 'hono/factory';
import { jwtVerify, SignJWT } from 'jose';
import { ApiError, unauthorized } from './errors.js';
import type { AuthUser, Env } from './env.js';

const PBKDF2_ITERATIONS = 100_000;

function toBase64(bytes: Uint8Array): string {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary);
}

function fromBase64(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function derive(password: string, salt: Uint8Array, iterations: number): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt: salt as unknown as BufferSource, iterations, hash: 'SHA-256' },
    key,
    256,
  );
  return new Uint8Array(bits);
}

/** Format: pbkdf2$<iterations>$<saltB64>$<hashB64> */
export async function hashPassword(password: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const hash = await derive(password, salt, PBKDF2_ITERATIONS);
  return `pbkdf2$${PBKDF2_ITERATIONS}$${toBase64(salt)}${'$'}${toBase64(hash)}`;
}

export async function verifyPassword(password: string, stored: string): Promise<boolean> {
  const parts = stored.split('$');
  if (parts.length !== 4 || parts[0] !== 'pbkdf2') return false;
  const iterations = Number(parts[1]);
  if (!Number.isInteger(iterations) || iterations < 10_000 || iterations > 1_000_000) return false;
  try {
    const salt = fromBase64(parts[2] as string);
    const expected = fromBase64(parts[3] as string);
    const actual = await derive(password, salt, iterations);
    if (actual.length !== expected.length) return false;
    let diff = 0;
    for (let i = 0; i < actual.length; i++) diff |= (actual[i] as number) ^ (expected[i] as number);
    return diff === 0;
  } catch {
    return false;
  }
}

function secretKey(env: Env): Uint8Array {
  if (!env.JWT_SECRET || env.JWT_SECRET.length < 16) {
    throw new ApiError(500, 'server_misconfigured', 'JWT_SECRET is not configured');
  }
  return new TextEncoder().encode(env.JWT_SECRET);
}

export async function signToken(env: Env, user: AuthUser): Promise<string> {
  return new SignJWT({ email: user.email })
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(user.id)
    .setIssuedAt()
    .setExpirationTime('30d')
    .sign(secretKey(env));
}

export async function verifyToken(env: Env, token: string): Promise<AuthUser> {
  try {
    const { payload } = await jwtVerify(token, secretKey(env), { algorithms: ['HS256'] });
    if (!payload.sub) throw new Error('missing subject');
    return { id: payload.sub, email: String(payload.email ?? '') };
  } catch {
    throw unauthorized('Invalid or expired token');
  }
}

export const requireAuth = createMiddleware<{ Bindings: Env }>(async (c, next) => {
  const header = c.req.header('authorization') ?? '';
  const match = /^Bearer\s+(.+)$/i.exec(header);
  if (!match?.[1]) throw unauthorized();
  c.set('user', await verifyToken(c.env, match[1]));
  await next();
});
