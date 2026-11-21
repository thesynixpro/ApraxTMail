import { purgeRateLimits } from './ratelimit.js';
import type { Env } from './env.js';

const EXPIRED_BATCH = 100;

/** Daily cron: delete expired messages (D1 rows + R2 objects) and stale rate-limit rows. */
export async function handleScheduled(env: Env): Promise<void> {
  const now = Date.now();
  const expired = await env.DB.prepare(
    'SELECT id, r2_key FROM messages WHERE expires_at < ? LIMIT ?',
  )
    .bind(now, EXPIRED_BATCH)
    .all<{ id: string; r2_key: string | null }>();

  let purged = 0;
  for (const message of expired.results) {
    const attachments = await env.DB.prepare('SELECT r2_key FROM attachments WHERE message_id = ?')
      .bind(message.id)
      .all<{ r2_key: string }>();
    const keys = attachments.results.map((a) => a.r2_key);
    if (message.r2_key) keys.push(message.r2_key);
    await Promise.allSettled(keys.map((k) => env.MAIL_BUCKET.delete(k)));
    await env.DB.prepare('DELETE FROM messages WHERE id = ?').bind(message.id).run();
    purged++;
  }

  await purgeRateLimits(env.DB);
  console.log('cron_completed', { purged, at: new Date(now).toISOString() });
}
