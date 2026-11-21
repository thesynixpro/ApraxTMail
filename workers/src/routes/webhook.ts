import { Hono } from 'hono';
import type { ContentfulStatusCode } from 'hono/utils/http-status';
import { badRequest, serviceUnavailable } from '../helpers.js';
import type { Env } from '../env.js';
import { extractOtp, sanitizeHtml } from '../otp.js';
import {
  extractAddresses,
  fetchInboundEmail,
  MAX_EVENTS_PER_DELIVERY,
  resolveRecipient,
  verifyWebhookSignature,
  WebhookError,
  type InboundEmail,
} from '../webhook.js';

interface EventEnvelope {
  event_id?: string;
  type?: string;
  object?: Record<string, unknown>;
  data?: { object?: Record<string, unknown> };
}

interface ResolvedRecipient {
  addressId: string;
  userId: string;
  domainId: string;
  domainName: string;
  recordsJson: string;
}

export const webhookRoutes = new Hono<{ Bindings: Env }>();

webhookRoutes.post('/', async (c) => {
  const secret = c.env.WEBHOOK_SECRET;
  if (!secret) throw serviceUnavailable('webhook_not_configured', 'WEBHOOK_SECRET is not set on the server');

  const raw = await c.req.text();
  try {
    await verifyWebhookSignature(secret, raw, c.req.header('x-emailit-signature'), c.req.header('x-emailit-timestamp'));
  } catch (e) {
    if (e instanceof WebhookError) return c.json({ error: e.code, message: e.message }, e.status as ContentfulStatusCode);
    throw e;
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw badRequest('invalid_json', 'Body must be JSON');
  }
  const events = Array.isArray(parsed) ? parsed : [parsed];

  let received = 0;
  let dropped = 0;
  let failed = 0;
  const errors: string[] = [];
  for (const ev of events.slice(0, MAX_EVENTS_PER_DELIVERY)) {
    try {
      const outcome = await handleEvent(c.env, ev as EventEnvelope);
      if (outcome === 'received') received++;
      else dropped++;
    } catch (e) {
      failed++;
      const message = e instanceof Error ? e.message : String(e);
      errors.push(message);
      console.error('webhook_event_failed', { message });
    }
  }

  // Non-2xx makes Emailit retry the delivery for up to ~3 days.
  if (failed > 0) return c.json({ received, dropped, failed, errors: errors.slice(0, 5) }, 502);
  return c.json({ received, dropped });
});

async function handleEvent(env: Env, ev: EventEnvelope): Promise<'received' | 'dropped'> {
  if (ev.type && ev.type !== 'email.received') return 'dropped';
  const obj = ev.data?.object ?? ev.object ?? {};
  const emailId = typeof obj.id === 'string' ? obj.id : null;
  if (!emailId) return 'dropped';

  const email = await fetchInboundEmail(env, emailId);
  const candidates = email.to.length > 0 ? email.to : extractAddresses(obj.to);
  for (const candidate of candidates) {
    const resolved = await resolveRecipient(env, candidate);
    if (resolved) {
      await storeMessage(env, email, resolved);
      return 'received';
    }
  }
  console.log('webhook_dropped_unmatched', { emailId, candidates });
  return 'dropped';
}

async function storeMessage(env: Env, email: InboundEmail, recipient: ResolvedRecipient): Promise<void> {
  const now = Date.now();
  const ttlDays = Number(env.MESSAGE_TTL_DAYS ?? '30') || 30;
  const receivedAt = Number.isFinite(email.createdAt) ? email.createdAt : now;
  const messageId = crypto.randomUUID();
  const otp = extractOtp(email.html) ?? extractOtp(email.text);
  const html = sanitizeHtml(email.html);
  const day = new Date(receivedAt).toISOString().slice(0, 10);
  const rawKey = `raw/${day}/${messageId}.json`;

  const writtenKeys: string[] = [];
  try {
    await env.MAIL_BUCKET.put(rawKey, JSON.stringify(email.raw), {
      httpMetadata: { contentType: 'application/json' },
    });
    writtenKeys.push(rawKey);

    let totalSize = email.text.length + html.length;
    const attachmentRows: [string, string, string, string, number, string][] = [];
    for (let i = 0; i < email.attachments.length; i++) {
      const a = email.attachments[i] as InboundEmail['attachments'][number];
      const safeName = a.filename.replace(/[^\w.\- ]+/g, '_').slice(-80) || 'file';
      const key = `att/${messageId}/${i}-${safeName}`;
      const bytes = base64ToBytes(a.contentBase64);
      await env.MAIL_BUCKET.put(key, bytes, { httpMetadata: { contentType: a.contentType } });
      writtenKeys.push(key);
      attachmentRows.push([crypto.randomUUID(), messageId, a.filename, a.contentType, a.size, key]);
      totalSize += a.size;
    }

    await env.DB.prepare(
      `INSERT INTO messages
        (id, user_id, address_id, remote_id, from_addr, from_name, to_addr, subject,
         text_body, html_body, otp_code, otp_source, r2_key, size, is_read, received_at, expires_at)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, 0, ?15, ?16)`,
    )
      .bind(
        messageId,
        recipient.userId,
        recipient.addressId,
        email.id,
        email.from,
        email.fromName,
        email.to.join(', '),
        email.subject,
        email.text,
        html,
        otp?.code ?? null,
        otp ? otp.source : null,
        rawKey,
        totalSize,
        receivedAt,
        receivedAt + ttlDays * 86_400_000,
      )
      .run();

    const attStmt = env.DB.prepare(
      `INSERT INTO attachments (id, message_id, filename, content_type, size, r2_key)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6)`,
    );
    for (const row of attachmentRows) {
      await attStmt.bind(...row).run();
    }
  } catch (e) {
    // Best-effort cleanup so failed writes do not leak objects.
    await Promise.allSettled(writtenKeys.map((k) => env.MAIL_BUCKET.delete(k)));
    throw e;
  }
}

function base64ToBytes(b64: string): Uint8Array {
  const clean = b64.includes(',') ? b64.slice(b64.indexOf(',') + 1) : b64;
  const bin = atob(clean);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}
