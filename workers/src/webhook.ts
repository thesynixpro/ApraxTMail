import { emailitFetch } from './emailit.js';
import { receivingHost, type DnsRecordSpec } from './dns.js';
import type { Env } from './env.js';

export const MAX_EVENTS_PER_DELIVERY = 20;
const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024;
const TIMESTAMP_SKEW_SECONDS = 300;

/** Constant-time hex comparison. */
export function timingSafeEqualHex(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

/**
 * Verifies X-Emailit-Signature: hex HMAC-SHA256 over `${timestamp}.${rawBody}`
 * with the webhook secret, rejecting timestamps skewed more than 5 minutes.
 */
export async function verifyWebhookSignature(
  secret: string,
  rawBody: string,
  signatureHeader: string | undefined,
  timestampHeader: string | undefined,
  nowSeconds = Math.floor(Date.now() / 1000),
): Promise<void> {
  if (!signatureHeader || !timestampHeader) {
    throw new WebhookError(401, 'missing_signature', 'Missing webhook signature headers');
  }
  const ts = Number(timestampHeader);
  if (!Number.isFinite(ts) || Math.abs(nowSeconds - ts) > TIMESTAMP_SKEW_SECONDS) {
    throw new WebhookError(401, 'stale_timestamp', 'Webhook timestamp outside allowed window');
  }
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const mac = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(`${ts}.${rawBody}`));
  const expected = [...new Uint8Array(mac)].map((b) => b.toString(16).padStart(2, '0')).join('');
  if (!timingSafeEqualHex(expected, signatureHeader.trim().toLowerCase())) {
    throw new WebhookError(401, 'bad_signature', 'Webhook signature verification failed');
  }
}

export class WebhookError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export interface InboundEmail {
  id: string;
  from: string | null;
  fromName: string | null;
  to: string[];
  subject: string;
  text: string;
  html: string;
  createdAt: number;
  attachments: { filename: string; contentType: string; size: number; contentBase64: string }[];
  raw: unknown;
}

/** Extracts address strings from the many shapes Emailit may use. */
export function extractAddresses(value: unknown): string[] {
  if (!value) return [];
  if (typeof value === 'string') {
    return value
      .split(/[,;]/)
      .map((s) => parseAddress(s))
      .filter((s): s is string => !!s);
  }
  if (Array.isArray(value)) return value.flatMap((v) => extractAddresses(v));
  if (typeof value === 'object') {
    const o = value as Record<string, unknown>;
    if (typeof o.address === 'string') return [o.address.trim().toLowerCase()];
    if (typeof o.email === 'string') return [o.email.trim().toLowerCase()];
    return extractAddresses(o.value ?? o.email_address);
  }
  return [];
}

export function parseAddress(value: string): string | null {
  const angle = /<([^>]+)>/.exec(value);
  const raw = (angle?.[1] ?? value).trim().toLowerCase();
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(raw) ? raw : null;
}

export function splitAddress(full: string): { local: string; host: string } | null {
  const idx = full.lastIndexOf('@');
  if (idx <= 0 || idx === full.length - 1) return null;
  return { local: full.slice(0, idx), host: full.slice(idx + 1) };
}

/** Normalizes an Emailit GET /emails/{id} payload (tolerant to field shapes). */
export function parseInboundEmail(payload: Record<string, unknown>, fallbackId: string): InboundEmail {
  const root = (payload.data ?? payload) as Record<string, unknown>;
  const body = (root.body ?? {}) as Record<string, unknown>;
  const id = str(root.id) ?? fallbackId;
  const fromRaw = root.from ?? root.sender;
  const fromFull = typeof fromRaw === 'string' ? parseAddress(fromRaw) : extractAddresses(fromRaw)[0] ?? null;
  let fromName: string | null = null;
  if (typeof fromRaw === 'string') {
    const m = /^(.*?)<[^>]+>/.exec(fromRaw);
    fromName = m && m[1] ? m[1].trim() : null;
  } else if (fromRaw && typeof fromRaw === 'object') {
    fromName = str((fromRaw as Record<string, unknown>).name);
  }

  const html = str(body.html) ?? str(root.html) ?? str(root.body_html) ?? '';
  const text = str(body.text) ?? str(root.text) ?? str(root.body_text) ?? stripTagsIfNoText(html);

  const createdAtMs = Date.parse(str(root.created_at) ?? str(root.received_at) ?? '');
  const attachmentsRaw = (root.attachments ?? []) as unknown[];
  const attachments: InboundEmail['attachments'] = [];
  if (Array.isArray(attachmentsRaw)) {
    for (const item of attachmentsRaw) {
      if (!item || typeof item !== 'object') continue;
      const a = item as Record<string, unknown>;
      const content = str(a.content);
      const size = typeof a.size === 'number' ? a.size : content ? Math.floor((content.length * 3) / 4) : 0;
      if (!content || size > MAX_ATTACHMENT_BYTES) continue;
      attachments.push({
        filename: str(a.filename) ?? str(a.name) ?? 'attachment',
        contentType: str(a.content_type) ?? str(a.contentType) ?? 'application/octet-stream',
        size,
        contentBase64: content,
      });
    }
  }

  return {
    id,
    from: fromFull,
    fromName,
    to: extractAddresses(root.to),
    subject: str(root.subject) ?? '(no subject)',
    text,
    html,
    createdAt: Number.isFinite(createdAtMs) ? createdAtMs : Date.now(),
    attachments,
    raw: payload,
  };
}

function stripTagsIfNoText(html: string): string {
  if (!html) return '';
  return html
    .replace(/<(script|style)[^>]*>[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function str(v: unknown): string | null {
  if (typeof v === 'string') return v;
  if (typeof v === 'number') return String(v);
  return null;
}

/** Fetches the full email body + attachments from Emailit (webhook payload is envelope-only). */
export async function fetchInboundEmail(env: Env, emailId: string): Promise<InboundEmail> {
  const res = await emailitFetch(env, `/emails/${encodeURIComponent(emailId)}`);
  if (!res.ok) {
    throw new WebhookError(502, 'emailit_fetch_failed', `Could not fetch email ${emailId} (${res.status})`);
  }
  const data = (await res.json()) as Record<string, unknown>;
  return parseInboundEmail(data, emailId);
}

interface ResolvedRecipient {
  addressId: string;
  userId: string;
  domainId: string;
  domainName: string;
  recordsJson: string;
}

/** Matches a recipient to a stored address on `<local>@<domain>` or `<local>@inbound.<domain>`. */
export async function resolveRecipient(env: Env, fullAddress: string): Promise<ResolvedRecipient | null> {
  const parts = splitAddress(fullAddress);
  if (!parts) return null;
  const row = await env.DB.prepare(
    `SELECT a.id AS addressId, a.user_id AS userId, a.domain_id AS domainId,
            d.name AS domainName, d.records_json AS recordsJson
     FROM addresses a JOIN domains d ON d.id = a.domain_id
     WHERE lower(a.local_part) = ?1
       AND (lower(d.name) = ?2 OR lower('inbound.' || d.name) = ?2)
       AND a.disabled = 0
     LIMIT 1`,
  )
    .bind(parts.local, parts.host)
    .first<ResolvedRecipient>();
  return row ?? null;
}

export function addressForDisplay(local: string, domainName: string, recordsJson: string): string {
  let records: DnsRecordSpec[] = [];
  try {
    const parsed = JSON.parse(recordsJson);
    if (Array.isArray(parsed)) records = parsed as DnsRecordSpec[];
  } catch {
    // ignore
  }
  return `${local}@${receivingHost(records, domainName)}`;
}
