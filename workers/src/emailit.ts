import type { DnsRecordSpec } from './dns.js';
import { serviceUnavailable } from './errors.js';
import type { Env } from './env.js';

export interface EmailitDomainSetup {
  remoteId: string | null;
  verificationStatus: string | null;
  records: DnsRecordSpec[];
}

interface EmailitDnsRecord {
  required?: boolean;
  type?: string;
  name?: string;
  host?: string;
  value?: string;
  content?: string;
  priority?: number | string;
  status?: string;
  error?: string;
}

function apiBase(env: Env): string {
  return (env.EMAILIT_API_URL || 'https://api.emailit.com/v2').replace(/\/$/, '');
}

export async function emailitFetch(env: Env, path: string, init?: RequestInit): Promise<Response> {
  if (!env.EMAILIT_API_KEY) {
    throw serviceUnavailable('emailit_not_configured', 'Emailit API key is not configured on the server');
  }
  try {
    return await fetch(`${apiBase(env)}${path}`, {
      ...init,
      headers: {
        authorization: `Bearer ${env.EMAILIT_API_KEY}`,
        accept: 'application/json',
        ...(init?.body ? { 'content-type': 'application/json' } : {}),
        ...init?.headers,
      },
    });
  } catch {
    throw serviceUnavailable('emailit_unreachable', 'Could not reach Emailit, try again later');
  }
}

/**
 * Registers the domain with Emailit and returns the REAL DNS records their
 * API reports as required. Never invents record values.
 */
export async function emailitAddDomain(env: Env, domain: string): Promise<EmailitDomainSetup> {
  const res = await emailitFetch(env, '/domains', {
    method: 'POST',
    body: JSON.stringify({ name: domain, inbound_key: 'inbound' }),
  });
  if (res.status === 401 || res.status === 403) {
    throw serviceUnavailable('emailit_auth_failed', 'Emailit rejected the server credentials');
  }
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    console.error('emailit_add_domain_failed', { status: res.status, detail: detail.slice(0, 500) });
    if (res.status === 409) {
      throw serviceUnavailable('emailit_domain_exists', 'That domain is already registered with Emailit');
    }
    throw serviceUnavailable('emailit_error', `Emailit could not add the domain (${res.status})`);
  }
  const data = (await res.json()) as Record<string, unknown>;
  return parseEmailitDomain(data);
}

/** Asks Emailit to re-check DNS on their side. Best-effort — local DoH is authoritative for UX. */
export async function emailitVerifyDomain(env: Env, remoteIdOrName: string): Promise<string | null> {
  try {
    const res = await emailitFetch(env, `/domains/${encodeURIComponent(remoteIdOrName)}/verify`, { method: 'POST' });
    if (!res.ok) return null;
    const data = (await res.json()) as Record<string, unknown>;
    const root = (data.data ?? data) as Record<string, unknown>;
    return str(root.verification_status) ?? str(root.status);
  } catch {
    return null;
  }
}

export async function emailitDeleteDomain(env: Env, remoteIdOrName: string): Promise<void> {
  try {
    await emailitFetch(env, `/domains/${encodeURIComponent(remoteIdOrName)}`, { method: 'DELETE' });
  } catch {
    // best-effort cleanup
  }
}

/** Tolerant parser for Emailit's domain object (POST/GET /v2/domains response). */
export function parseEmailitDomain(payload: Record<string, unknown>): EmailitDomainSetup {
  const root = (payload.data ?? payload) as Record<string, unknown>;
  const remoteId = str(root.id ?? payload.id);
  const verificationStatus = str(root.verification_status ?? root.status);

  const records: DnsRecordSpec[] = [];
  const rawRecords = root.dns_records ?? root.dnsRecords ?? payload.dns_records;
  if (Array.isArray(rawRecords)) {
    for (const item of rawRecords) {
      if (!item || typeof item !== 'object') continue;
      const r = item as EmailitDnsRecord;
      const type = str(r.type)?.toUpperCase();
      const host = str(r.name ?? r.host) ?? '@';
      const value = str(r.value ?? r.content);
      if (!type || !value) continue;
      if (type !== 'MX' && type !== 'TXT' && type !== 'CNAME') continue;
      const priority = typeof r.priority === 'number' ? r.priority : Number(r.priority);
      records.push({
        type,
        host,
        value,
        ...(Number.isFinite(priority) ? { priority } : {}),
        required: r.required !== false,
      });
    }
  }

  if (records.length === 0) {
    console.error('emailit_no_records', { keys: Object.keys(root) });
    throw serviceUnavailable('emailit_no_records', 'Emailit did not return the required DNS records');
  }
  return { remoteId, verificationStatus, records };
}

function str(v: unknown): string | null {
  if (typeof v === 'string' && v.trim()) return v.trim();
  if (typeof v === 'number') return String(v);
  return null;
}
