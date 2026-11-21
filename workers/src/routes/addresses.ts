import { Hono, type Context } from 'hono';
import { requireAuth } from '../auth.js';
import { receivingHost, type DnsRecordSpec } from '../dns.js';
import { badRequest, conflict, notFound, rateLimitGuard, readJson } from '../helpers.js';
import type { Env } from '../env.js';

const LOCAL_RE = /^[a-z0-9](?:[a-z0-9._-]{0,61}[a-z0-9])?$/;
const MAX_PER_DOMAIN = 50;
const MAX_PER_USER = 200;

interface AddressRow {
  id: string;
  user_id: string;
  domain_id: string;
  local_part: string;
  disabled: number;
  created_at: number;
  domain_name: string;
  records_json: string;
}

function parseRecords(json: string | null): DnsRecordSpec[] {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? (parsed as DnsRecordSpec[]) : [];
  } catch {
    return [];
  }
}

function serialize(row: AddressRow) {
  const host = receivingHost(parseRecords(row.records_json), row.domain_name);
  return {
    id: row.id,
    domainId: row.domain_id,
    domain: row.domain_name,
    localPart: row.local_part,
    address: `${row.local_part}@${host}`,
    disabled: row.disabled === 1,
    createdAt: row.created_at,
  };
}

const SELECT_ADDRESS = `
  SELECT a.*, d.name AS domain_name, d.records_json AS records_json
  FROM addresses a JOIN domains d ON d.id = a.domain_id`;

async function loadOwnedAddress(c: Context<{ Bindings: Env }>): Promise<AddressRow> {
  const row = await c.env.DB.prepare(`${SELECT_ADDRESS} WHERE a.id = ? AND a.user_id = ?`)
    .bind(c.req.param('addressId'), c.get('user').id)
    .first<AddressRow>();
  if (!row) throw notFound('Address not found');
  return row;
}

export const addressRoutes = new Hono<{ Bindings: Env }>();

addressRoutes.use('/domains/:domainId/addresses*', requireAuth);
addressRoutes.use('/addresses/:addressId*', requireAuth);

addressRoutes.get('/domains/:domainId/addresses', async (c) => {
  const domain = await c.env.DB.prepare('SELECT id FROM domains WHERE id = ? AND user_id = ?')
    .bind(c.req.param('domainId'), c.get('user').id)
    .first();
  if (!domain) throw notFound('Domain not found');

  const result = await c.env.DB.prepare(
    `${SELECT_ADDRESS} WHERE a.domain_id = ? ORDER BY a.created_at ASC`,
  )
    .bind(c.req.param('domainId'))
    .all<AddressRow>();
  return c.json({ addresses: result.results.map(serialize) });
});

addressRoutes.post('/domains/:domainId/addresses', async (c) => {
  const user = c.get('user');
  await rateLimitGuard(c, `address_create:${user.id}`, 60, 3600, 'Too many addresses created, try later');

  const domain = await c.env.DB.prepare(
    'SELECT id, name, records_json FROM domains WHERE id = ? AND user_id = ?',
  )
    .bind(c.req.param('domainId'), user.id)
    .first<{ id: string; name: string; records_json: string }>();
  if (!domain) throw notFound('Domain not found');

  const body = await readJson(c);
  const localPart = String(body?.localPart ?? body?.local ?? '').trim().toLowerCase();
  if (!LOCAL_RE.test(localPart) || localPart.length > 64) {
    throw badRequest('invalid_local_part', 'Use letters, digits, dots, dashes or underscores (max 64 chars)');
  }

  const domainCount = await c.env.DB.prepare('SELECT COUNT(1) AS n FROM addresses WHERE domain_id = ?')
    .bind(domain.id)
    .first<{ n: number }>();
  if ((domainCount?.n ?? 0) >= MAX_PER_DOMAIN) {
    throw conflict('domain_address_limit', `Maximum ${MAX_PER_DOMAIN} addresses per domain`);
  }
  const userCount = await c.env.DB.prepare('SELECT COUNT(1) AS n FROM addresses WHERE user_id = ?')
    .bind(user.id)
    .first<{ n: number }>();
  if ((userCount?.n ?? 0) >= MAX_PER_USER) {
    throw conflict('user_address_limit', `Maximum ${MAX_PER_USER} addresses total`);
  }

  const id = crypto.randomUUID();
  try {
    await c.env.DB.prepare(
      'INSERT INTO addresses (id, user_id, domain_id, local_part, disabled, created_at) VALUES (?, ?, ?, ?, 0, ?)',
    )
      .bind(id, user.id, domain.id, localPart, Date.now())
      .run();
  } catch (e) {
    if (e instanceof Error && /UNIQUE/i.test(e.message)) {
      throw conflict('address_exists', 'That address already exists on this domain');
    }
    throw e;
  }

  const row = await c.env.DB.prepare(`${SELECT_ADDRESS} WHERE a.id = ?`).bind(id).first<AddressRow>();
  return c.json({ address: serialize(row as AddressRow) }, 201);
});

addressRoutes.delete('/addresses/:addressId', async (c) => {
  const row = await loadOwnedAddress(c);
  await c.env.DB.prepare('DELETE FROM addresses WHERE id = ?').bind(row.id).run();
  return c.json({ ok: true });
});
