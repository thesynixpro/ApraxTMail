import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { handleScheduled } from './cron.js';
import type { Env } from './env.js';
import { errorResponse } from './errors.js';
import { addressRoutes } from './routes/addresses.js';
import { authRoutes } from './routes/auth.js';
import { domainRoutes } from './routes/domains.js';
import { messageRoutes } from './routes/messages.js';
import { webhookRoutes } from './routes/webhook.js';

const app = new Hono<{ Bindings: Env }>();

app.use('*', cors());
app.onError((err, c) => errorResponse(c, err));
app.notFound((c) => c.json({ error: 'not_found', message: 'Not found' }, 404));

app.get('/health', (c) => c.json({ status: 'ok', service: 'aprax-tmail', version: '2.0.0' }));

app.route('/api/auth', authRoutes);
app.route('/api', domainRoutes);
app.route('/api', addressRoutes);
app.route('/api', messageRoutes);
app.route('/webhooks/emailit', webhookRoutes);

export default {
  fetch: app.fetch,
  scheduled(_event: ScheduledController, env: Env, ctx: ExecutionContext): void {
    ctx.waitUntil(handleScheduled(env));
  },
};
