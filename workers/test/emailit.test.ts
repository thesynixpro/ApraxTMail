import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { parseEmailitDomain } from '../src/emailit.js';

describe('parseEmailitDomain', () => {
  it('parses a wrapped response with dns records', () => {
    const setup = parseEmailitDomain({
      data: {
        id: 'dom_abc',
        verification_status: 'pending',
        dns_records: [
          { required: true, type: 'MX', name: 'inbound', value: 'inbound.emailitmail.com', priority: 10 },
          { required: true, type: 'TXT', name: '@', value: 'v=spf1 include:_spf.emailit.com ~all' },
          { type: 'CNAME', name: 'go', value: 'go.emailitmail.com.' },
        ],
      },
    });
    assert.equal(setup.remoteId, 'dom_abc');
    assert.equal(setup.verificationStatus, 'pending');
    assert.equal(setup.records.length, 3);
    assert.equal(setup.records[0]?.type, 'MX');
    assert.equal(setup.records[0]?.priority, 10);
    assert.equal(setup.records[0]?.host, 'inbound');
    assert.equal(setup.records[2]?.type, 'CNAME');
  });

  it('parses an unwrapped response', () => {
    const setup = parseEmailitDomain({
      id: 'dom_flat',
      status: 'unverified',
      dns_records: [{ type: 'TXT', name: 'emailit._domainkey', value: 'v=DKIM1; p=xyz' }],
    });
    assert.equal(setup.remoteId, 'dom_flat');
    assert.equal(setup.verificationStatus, 'unverified');
    assert.equal(setup.records.length, 1);
  });

  it('defaults required to true', () => {
    const setup = parseEmailitDomain({
      dns_records: [{ type: 'TXT', name: '@', value: 'v=spf1 ~all' }],
    });
    assert.equal(setup.records[0]?.required, true);
  });

  it('skips records without type or value', () => {
    const setup = parseEmailitDomain({
      dns_records: [
        { type: 'TXT', name: '@' },
        { value: 'orphan' },
        null,
        'junk',
        { type: 'TXT', name: '@', value: 'v=spf1 ~all' },
      ],
    });
    assert.equal(setup.records.length, 1);
    assert.equal(setup.records[0]?.value, 'v=spf1 ~all');
  });

  it('throws when no usable records are returned', () => {
    assert.throws(
      () => parseEmailitDomain({ data: { id: 'dom_x', dns_records: [] } }),
      (e: unknown) => e instanceof Error && e.message.includes('DNS records'),
    );
  });
});
