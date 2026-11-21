import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  extractAddresses,
  parseAddress,
  parseInboundEmail,
  splitAddress,
  timingSafeEqualHex,
  verifyWebhookSignature,
  WebhookError,
} from '../src/webhook.js';

async function sign(secret: string, ts: number, body: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const mac = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(`${ts}.${body}`));
  return [...new Uint8Array(mac)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

describe('timingSafeEqualHex', () => {
  it('matches equal strings', () => {
    assert.equal(timingSafeEqualHex('abc123', 'abc123'), true);
  });
  it('rejects different strings of same length', () => {
    assert.equal(timingSafeEqualHex('abc123', 'abc124'), false);
  });
  it('rejects different lengths', () => {
    assert.equal(timingSafeEqualHex('abc', 'abcd'), false);
  });
});

describe('verifyWebhookSignature', () => {
  const secret = 'whsec_test_secret';
  const body = '[{"event_id":"evt_1","type":"email.received"}]';
  const now = 1_760_000_000;

  it('accepts a valid signature', async () => {
    const sig = await sign(secret, now, body);
    await verifyWebhookSignature(secret, body, sig, String(now), now);
  });

  it('rejects a missing signature', async () => {
    await assert.rejects(
      () => verifyWebhookSignature(secret, body, undefined, String(now), now),
      (e: unknown) => e instanceof WebhookError && e.status === 401 && e.code === 'missing_signature',
    );
  });

  it('rejects a stale timestamp', async () => {
    const sig = await sign(secret, now - 4000, body);
    await assert.rejects(
      () => verifyWebhookSignature(secret, body, sig, String(now - 4000), now),
      (e: unknown) => e instanceof WebhookError && e.code === 'stale_timestamp',
    );
  });

  it('rejects a bad signature', async () => {
    const sig = await sign(secret, now, 'tampered');
    await assert.rejects(
      () => verifyWebhookSignature(secret, body, sig, String(now), now),
      (e: unknown) => e instanceof WebhookError && e.code === 'bad_signature',
    );
  });

  it('rejects wrong secret', async () => {
    const sig = await sign('whsec_other', now, body);
    await assert.rejects(
      () => verifyWebhookSignature(secret, body, sig, String(now), now),
      (e: unknown) => e instanceof WebhookError && e.code === 'bad_signature',
    );
  });
});

describe('parseAddress / splitAddress', () => {
  it('parses angle-bracket addresses', () => {
    assert.equal(parseAddress('Jane Doe <jane@example.com>'), 'jane@example.com');
  });
  it('parses bare addresses', () => {
    assert.equal(parseAddress('  USER@Example.COM '), 'user@example.com');
  });
  it('rejects invalid addresses', () => {
    assert.equal(parseAddress('not-an-email'), null);
  });
  it('splits local and host', () => {
    assert.deepEqual(splitAddress('sales@inbound.example.com'), {
      local: 'sales',
      host: 'inbound.example.com',
    });
  });
  it('rejects malformed splits', () => {
    assert.equal(splitAddress('@example.com'), null);
    assert.equal(splitAddress('user@'), null);
  });
});

describe('extractAddresses', () => {
  it('parses a comma-separated string', () => {
    assert.deepEqual(extractAddresses('a@b.com, c@d.com'), ['a@b.com', 'c@d.com']);
  });
  it('parses arrays of objects', () => {
    assert.deepEqual(extractAddresses([{ address: 'x@y.com' }, { email: 'p@q.com' }]), ['x@y.com', 'p@q.com']);
  });
  it('returns empty for garbage', () => {
    assert.deepEqual(extractAddresses(42), []);
  });
});

describe('parseInboundEmail', () => {
  it('normalizes a v2-style payload', () => {
    const payload = {
      data: {
        id: 'em_123',
        from: 'Jane Doe <jane@example.com>',
        to: [{ address: 'inbox@inbound.example.com' }],
        subject: 'Hello',
        created_at: '2026-01-02T03:04:05Z',
        body: { text: 'plain body', html: '<p>html body</p>' },
        attachments: [
          { filename: 'a.txt', content_type: 'text/plain', size: 5, content: 'aGk=' },
          { filename: 'huge.bin', content_type: 'application/octet-stream', size: 30 * 1024 * 1024, content: 'AA==' },
        ],
      },
    };
    const email = parseInboundEmail(payload, 'em_fallback');
    assert.equal(email.id, 'em_123');
    assert.equal(email.from, 'jane@example.com');
    assert.equal(email.fromName, 'Jane Doe');
    assert.deepEqual(email.to, ['inbox@inbound.example.com']);
    assert.equal(email.subject, 'Hello');
    assert.equal(email.text, 'plain body');
    assert.equal(email.html, '<p>html body</p>');
    assert.equal(email.createdAt, Date.parse('2026-01-02T03:04:05Z'));
    assert.equal(email.attachments.length, 1);
    assert.equal(email.attachments[0]?.filename, 'a.txt');
  });

  it('falls back to root fields and strips HTML when no text', () => {
    const payload = {
      id: 'em_2',
      from: 'a@b.com',
      to: 'c@d.com',
      subject: 'Hi',
      html: '<p>only <b>html</b></p>',
    };
    const email = parseInboundEmail(payload, 'em_fallback');
    assert.equal(email.id, 'em_2');
    assert.equal(email.text, 'only html');
    assert.equal(email.html, '<p>only <b>html</b></p>');
    assert.ok(email.createdAt > 0);
  });

  it('uses the fallback id when missing', () => {
    const email = parseInboundEmail({}, 'em_fallback');
    assert.equal(email.id, 'em_fallback');
    assert.equal(email.subject, '(no subject)');
  });
});
