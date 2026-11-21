import { Hono } from 'hono';
import { hashPassword, requireAuth, signToken, verifyPassword } from '../auth.js';
import { badRequest, conflict, rateLimitGuard, unauthorized } from '../helpers.js';
import type { Env } from '../env.js';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const authRoutes = new Hono<{ Bindings: Env }>();

authRoutes.post('/register', async (c) => {
  await rateLimitGuard(c, `register:${clientIp(c)}`, 5, 3600, 'Too many signup attempts');
  const body = await readJson(c);
  const email = String(body?.email ?? '').trim().toLowerCase();
  const password = String(body?.password ?? '');
  if (!EMAIL_RE.test(email) || email.length > 254) throw badRequest('invalid_email', 'Enter a valid email address');
  if (password.length < 10) throw badRequest('weak_password', 'Password must be at least 10 characters');

  const existing = await c.env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(email).first();
  if (existing) throw conflict('email_taken', 'An account with this email already exists');

  const id = crypto.randomUUID();
  const passwordHash = await hashPassword(password);
  await c.env.DB.prepare('INSERT INTO users (id, email, password_hash, created_at) VALUES (?, ?, ?, ?)')
    .bind(id, email, passwordHash, Date.now())
    .run();

  const token = await signToken(c.env, { id, email });
  return c.json({ token, user: { id, email } }, 201);
});

authRoutes.post('/login', async (c) => {
  await rateLimitGuard(c, `login:${clientIp(c)}`, 10, 3600, 'Too many login attempts');
  const body = await readJson(c);
  const email = String(body?.email ?? '').trim().toLowerCase();
  const password = String(body?.password ?? '');
  if (!email || !password) throw badRequest('missing_credentials', 'Email and password are required');

  const row = await c.env.DB.prepare('SELECT id, password_hash FROM users WHERE email = ?').bind(email).first<{
    id: string;
    password_hash: string;
  }>();
  const ok = row ? await verifyPassword(password, row.password_hash) : false;
  if (!row || !ok) throw unauthorized('Invalid email or password');

  const token = await signToken(c.env, { id: row.id, email });
  return c.json({ token, user: { id: row.id, email } });
});

authRoutes.get('/me', requireAuth, (c) => c.json({ user: c.get('user') }));

function clientIp(c: { req: { header: (n: string) => string | undefined } }): string {
  return c.req.header('cf-connecting-ip') ?? c.req.header('x-forwarded-for')?.split(',')[0]?.trim() ?? 'unknown';
}

async function readJson(c: { req: { json: () => Promise<unknown> } }): Promise<Record<string, unknown> | null> {
  try {
    const body = await c.req.json();
    return body && typeof body === 'object' ? (body as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}
