import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { fqdn, normalizeHost, parseMxHost, parseTxtValue, receivingHost } from '../src/dns.js';

describe('fqdn', () => {
  it('maps @ to the apex', () => {
    assert.equal(fqdn('@', 'example.com'), 'example.com');
  });
  it('appends relative labels', () => {
    assert.equal(fqdn('inbound', 'example.com'), 'inbound.example.com');
    assert.equal(fqdn('emailit._domainkey', 'example.com'), 'emailit._domainkey.example.com');
  });
  it('keeps hostnames already suffixed with the domain', () => {
    assert.equal(fqdn('mail.example.com', 'example.com'), 'mail.example.com');
    assert.equal(fqdn('inbound.example.com.', 'example.com'), 'inbound.example.com');
  });
  it('normalizes case and trailing dots', () => {
    assert.equal(fqdn('Inbound.', 'Example.COM.'), 'inbound.example.com');
  });
});

describe('parseMxHost', () => {
  it('extracts the exchange host', () => {
    assert.equal(parseMxHost('10 inbound.emailitmail.com.'), 'inbound.emailitmail.com');
  });
  it('handles bare hosts', () => {
    assert.equal(parseMxHost('mx.example.com'), 'mx.example.com');
  });
});

describe('parseTxtValue', () => {
  it('joins quoted chunks', () => {
    assert.equal(parseTxtValue('"part-one" "part-two"'), 'part-onepart-two');
  });
  it('returns plain values untouched', () => {
    assert.equal(parseTxtValue('v=spf1 include:_spf.emailit.com ~all'), 'v=spf1 include:_spf.emailit.com ~all');
  });
});

describe('normalizeHost', () => {
  it('lowercases and strips trailing dot', () => {
    assert.equal(normalizeHost(' Inbound.Emailitmail.com. '), 'inbound.emailitmail.com');
  });
});

describe('receivingHost', () => {
  const inboundMx = {
    type: 'MX' as const,
    host: 'inbound',
    value: '10 inbound.emailitmail.com',
    priority: 10,
  };

  it('uses the inbound MX host when present', () => {
    assert.equal(receivingHost([inboundMx], 'example.com'), 'inbound.example.com');
  });

  it('falls back to the apex when no inbound MX', () => {
    const otherMx = { type: 'MX' as const, host: '@', value: '10 mail.example.com' };
    assert.equal(receivingHost([otherMx], 'example.com'), 'example.com');
  });

  it('falls back with an empty record list', () => {
    assert.equal(receivingHost([], 'example.com'), 'example.com');
  });
});
