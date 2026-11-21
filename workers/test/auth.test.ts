import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { hashPassword, signToken, verifyPassword, verifyToken } from '../src/auth.js';
import type { Env } from '../src/env.js';

const testEnv = { JWT_SECRET: 'test-secret-that-is-long-enough-123456' } as unknown as Env;

describe('password hashing', () => {
  it('round-trips a password', async () => {
    const stored = await hashPassword('correct horse battery staple');
    assert.ok(stored.startsWith('pbkdf2$'));
    assert.equal(await verifyPassword('correct horse battery staple', stored), true);
    assert.equal(await verifyPassword('wrong password', stored), false);
  });

  it('produces unique salts', async () => {
    const a = await hashPassword('same-password-here');
    const b = await hashPassword('same-password-here');
    assert.notEqual(a, b);
  });

  it('rejects malformed stored hashes', async () => {
    assert.equal(await verifyPassword('x', 'not-a-hash'), false);
  });
});

describe('jwt tokens', () => {
  it('signs and verifies a token', async () => {
    const token = await signToken(testEnv, { id: 'user-1', email: 'a@b.com' });
    const user = await verifyToken(testEnv, token);
    assert.equal(user.id, 'user-1');
    assert.equal(user.email, 'a@b.com');
  });

  it('rejects a token signed with another secret', async () => {
    const other = { JWT_SECRET: 'another-secret-that-is-also-long-enough' } as unknown as Env;
    const token = await signToken(other, { id: 'user-1', email: 'a@b.com' });
    await assert.rejects(() => verifyToken(testEnv, token));
  });

  it('rejects garbage input', async () => {
    await assert.rejects(() => verifyToken(testEnv, 'not.a.jwt'));
  });
});
