import { Hono, type Context } from 'hono';
import { requireAuth } from '../auth.js';
import { checkRecords, type DnsRecordSpec } from '../dns.js';
import { emailitAddDomain, emailitDeleteDomain, emailitVerifyDomain } from '../emailit.js';
import { badRequest, conflict, notFound, rateLimitGuard, readJson } from '../helpers.js';
import type { Env } from '../env.js';

const DOMAIN_RE = /^(?!-)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/;

interface DomainRow {
  id: string;
  user_id: string;
  name: string;
  emailit_id: string | null;
  records_json: string;
  status: string;
  mx_ok: number;
  txt_ok: number;
  last_check_json: string | null;
  last_checked_at: number | null;
  created_at: number;
}

function parseRecords(json: string): DnsRecordSpec[] {
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? (parsed as DnsRecordSpec[]) : [];
  } catch {
    return [];
  }
}

function serialize(row: DomainRow) {
  const records = parseRecords(row.records_json);
  return {
    id: row.id,
    name: row.name,
    status: row.status,
    mxOk: row.mx_ok === 1,
    txtOk: row.txt_ok === 1,
    records,
    lastCheckedAt: row.last_checked_at,
    lastCheck: row.last_check_json ? (JSON.parse(row.last_check_json) as unknown) : null,
    createdAt: row.created_at,
  };
}

async function loadOwned(c: Context<{ Bindings: Env }>): Promise<DomainRow> {
  const id = c.req.param('id');
  const row = await c.env.DB.prepare('SELECT * FROM domains WHERE id = ? AND user_id = ?')
    .bind(id, c.get('user').id)
    .first<DomainRow>();
  if (!row) throw notFound('Domain not found');
  return row;
}

export const domainRoutes = new Hono<{ Bindings: Env }>();

domainRoutes.use('/domains*', requireAuth);

domainRoutes.get('/domains', async (c) => {
  const result = await c.env.DB.prepare('SELECT * FROM domains WHERE user_id = ? ORDER BY created_at DESC')
    .bind(c.get('user').id)
    .all<DomainRow>();
  return c.json({ domains: result.results.map(serialize) });
});

domainRoutes.post('/domains', async (c) => {
  const user = c.get('user');
  await rateLimitGuard(c, `domain_create:${user.id}`, 10, 3600, 'Too many domains created, try later');

  const body = await readJson(c);
  const name = String(body?.name ?? '').trim().toLowerCase().replace(/\.$/, '');
  if (!DOMAIN_RE.test(name) || name.length > 253) {
    throw badRequest('invalid_domain', 'Enter a valid domain name like example.com');
  }

  const existing = await c.env.DB.prepare('SELECT id FROM domains WHERE name = ?').bind(name).first();
  if (existing) throw conflict('domain_exists', 'This domain is already registered');

  const setup = await emailitAddDomain(c.env, name);
  const id = crypto.randomUUID();
  const status = setup.verificationStatus === 'verified' ? 'verified' : 'pending';
  await c.env.DB.prepare(
    `INSERT INTO domains (id, user_id, name, emailit_id, verification_token, records_json, status, mx_ok, txt_ok, created_at)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 0, 0, ?8)`,
  )
    .bind(id, user.id, name, setup.remoteId, crypto.randomUUID(), JSON.stringify(setup.records), status, Date.now())
    .run();

  const row = await c.env.DB.prepare('SELECT * FROM domains WHERE id = ?').bind(id).first<DomainRow>();
  return c.json({ domain: serialize(row as DomainRow) }, 201);
});

domainRoutes.get('/domains/:id', async (c) => c.json({ domain: serialize(await loadOwned(c)) }));

domainRoutes.post('/domains/:id/verify', async (c) => {
  const user = c.get('user');
  await rateLimitGuard(c, `domain_verify:${user.id}`, 30, 60, 'Too many verification checks, wait a moment');

  const row = await loadOwned(c);
  const records = parseRecords(row.records_json);
  if (records.length === 0) throw badRequest('no_records', 'This domain has no stored DNS records');

  const check = await checkRecords(row.name, records);
  const status = check.allOk ? 'verified' : 'pending';
  await c.env.DB.prepare(
    `UPDATE domains SET mx_ok = ?1, txt_ok = ?2, status = ?3, last_check_json = ?4, last_checked_at = ?5 WHERE id = ?6`,
  )
    .bind(check.mxOk ? 1 : 0, check.txtOk ? 1 : 0, status, JSON.stringify(check), Date.now(), row.id)
    .run();

  if (row.emailit_id) {
    const remoteStatus = await emailitVerifyDomain(c.env, row.emailit_id);
    if (remoteStatus === 'verified') {
      await c.env.DB.prepare('UPDATE domains SET status = ?1 WHERE id = ?2').bind('verified', row.id).run();
    }
  }

  const updated = await c.env.DB.prepare('SELECT * FROM domains WHERE id = ?').bind(row.id).first<DomainRow>();
  return c.json({
    domain: serialize(updated as DomainRow),
    check: { allOk: check.allOk, mxOk: check.mxOk, txtOk: check.txtOk, missing: check.missing, checks: check.checks },
  });
});

domainRoutes.delete('/domains/:id', async (c) => {
  const row = await loadOwned(c);
  await c.env.DB.prepare('DELETE FROM domains WHERE id = ?').bind(row.id).run();
  if (row.emailit_id) await emailitDeleteDomain(c.env, row.emailit_id);
  return c.json({ ok: true });
});
