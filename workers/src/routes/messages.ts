import { Hono, type Context } from 'hono';
import { requireAuth } from '../auth.js';
import { notFound } from '../helpers.js';
import type { Env } from '../env.js';

interface MessageRow {
  id: string;
  user_id: string;
  address_id: string | null;
  remote_id: string | null;
  from_addr: string | null;
  from_name: string | null;
  to_addr: string | null;
  subject: string | null;
  text_body: string | null;
  html_body: string | null;
  otp_code: string | null;
  otp_source: string | null;
  r2_key: string | null;
  size: number;
  is_read: number;
  received_at: number;
  expires_at: number;
  attachment_count?: number;
}

interface AttachmentRow {
  id: string;
  message_id: string;
  filename: string;
  content_type: string | null;
  size: number;
  r2_key: string;
}

function summary(row: MessageRow) {
  return {
    id: row.id,
    addressId: row.address_id,
    from: row.from_addr,
    fromName: row.from_name,
    to: row.to_addr,
    subject: row.subject ?? '(no subject)',
    otpCode: row.otp_code,
    otpSource: row.otp_source,
    read: row.is_read === 1,
    hasAttachments: (row.attachment_count ?? 0) > 0,
    attachmentCount: row.attachment_count ?? 0,
    size: row.size,
    receivedAt: row.received_at,
    expiresAt: row.expires_at,
  };
}

function detail(row: MessageRow, attachments: { id: string; filename: string; contentType: string | null; size: number }[]) {
  return {
    ...summary(row),
    text: row.text_body ?? '',
    html: row.html_body ?? '',
    attachments,
  };
}

async function loadOwnedMessage(c: Context<{ Bindings: Env }>): Promise<MessageRow> {
  const row = await c.env.DB.prepare('SELECT * FROM messages WHERE id = ? AND user_id = ?')
    .bind(c.req.param('messageId'), c.get('user').id)
    .first<MessageRow>();
  if (!row) throw notFound('Message not found');
  return row;
}

export const messageRoutes = new Hono<{ Bindings: Env }>();

messageRoutes.use('/messages*', requireAuth);

messageRoutes.get('/messages', async (c) => {
  const user = c.get('user');
  const addressId = c.req.query('addressId') ?? undefined;
  const domainId = c.req.query('domainId') ?? undefined;
  const unread = c.req.query('unread') === 'true';
  const limit = Math.min(Math.max(Number(c.req.query('limit') ?? 50) || 50, 1), 100);
  const offset = Math.max(Number(c.req.query('offset') ?? 0) || 0, 0);

  const where: string[] = ['m.user_id = ?1'];
  const binds: (string | number)[] = [user.id];
  if (addressId) {
    binds.push(addressId);
    where.push(`m.address_id = ?${binds.length}`);
  }
  if (domainId) {
    binds.push(domainId);
    where.push(`m.address_id IN (SELECT id FROM addresses WHERE domain_id = ?${binds.length})`);
  }
  if (unread) where.push('m.is_read = 0');
  const whereSql = where.join(' AND ');

  const countRow = await c.env.DB.prepare(`SELECT COUNT(1) AS total FROM messages m WHERE ${whereSql}`)
    .bind(...binds)
    .first<{ total: number }>();

  binds.push(limit, offset);
  const result = await c.env.DB.prepare(
    `SELECT m.*, (SELECT COUNT(1) FROM attachments a WHERE a.message_id = m.id) AS attachment_count
     FROM messages m WHERE ${whereSql}
     ORDER BY m.received_at DESC LIMIT ?${binds.length - 1} OFFSET ?${binds.length}`,
  )
    .bind(...binds)
    .all<MessageRow>();

  return c.json({ messages: result.results.map(summary), total: countRow?.total ?? 0 });
});

messageRoutes.get('/messages/:messageId', async (c) => {
  const row = await loadOwnedMessage(c);
  const atts = await c.env.DB.prepare('SELECT * FROM attachments WHERE message_id = ? ORDER BY filename')
    .bind(row.id)
    .all<AttachmentRow>();
  return c.json({
    message: detail(
      row,
      atts.results.map((a) => ({ id: a.id, filename: a.filename, contentType: a.content_type, size: a.size })),
    ),
  });
});

messageRoutes.post('/messages/:messageId/read', async (c) => {
  const row = await loadOwnedMessage(c);
  const body = await c.req.json().catch(() => null);
  const read = body && typeof body.read === 'boolean' ? body.read : true;
  await c.env.DB.prepare('UPDATE messages SET is_read = ?1 WHERE id = ?2').bind(read ? 1 : 0, row.id).run();
  return c.json({ ok: true, read });
});

messageRoutes.delete('/messages/:messageId', async (c) => {
  const row = await loadOwnedMessage(c);
  const atts = await c.env.DB.prepare('SELECT r2_key FROM attachments WHERE message_id = ?')
    .bind(row.id)
    .all<{ r2_key: string }>();
  const deletions: Promise<unknown>[] = [];
  for (const a of atts.results) deletions.push(c.env.MAIL_BUCKET.delete(a.r2_key));
  if (row.r2_key) deletions.push(c.env.MAIL_BUCKET.delete(row.r2_key));
  await Promise.allSettled(deletions);
  await c.env.DB.prepare('DELETE FROM messages WHERE id = ?').bind(row.id).run();
  return c.json({ ok: true });
});

messageRoutes.get('/messages/:messageId/attachments/:attachmentId', async (c) => {
  const row = await loadOwnedMessage(c);
  const att = await c.env.DB.prepare('SELECT * FROM attachments WHERE id = ? AND message_id = ?')
    .bind(c.req.param('attachmentId'), row.id)
    .first<AttachmentRow>();
  if (!att) throw notFound('Attachment not found');

  const object = await c.env.MAIL_BUCKET.get(att.r2_key);
  if (!object) throw notFound('Attachment data no longer available');

  const asciiName = att.filename.replace(/[^\x20-\x7e]/g, '_').replace(/["\\]/g, '_');
  const headers = new Headers({
    'content-type': att.content_type ?? object.httpMetadata?.contentType ?? 'application/octet-stream',
    'content-length': String(object.size),
    'content-disposition': `attachment; filename="${asciiName}"; filename*=UTF-8''${encodeURIComponent(att.filename)}`,
    'cache-control': 'private, max-age=3600',
    'x-content-type-options': 'nosniff',
  });
  return new Response(object.body, { headers });
});
