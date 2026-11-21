import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { extractAllOtps, extractOtp, sanitizeHtml } from '../src/otp.js';

describe('extractOtp', () => {
  it('finds a 6-digit code after "code"', () => {
    const r = extractOtp('Your verification code is 482913');
    assert.ok(r);
    assert.equal(r.code, '482913');
  });

  it('finds code in HTML body', () => {
    const r = extractOtp('<p>Use <b>135790</b> to sign in</p>');
    assert.ok(r);
    assert.equal(r.code, '135790');
  });

  it('returns null for empty input', () => {
    assert.equal(extractOtp('   '), null);
  });

  it('does not treat plain order numbers as OTPs', () => {
    assert.equal(extractOtp('Your order 55512345 was placed, total $10'), null);
  });

  it('collects unique codes only', () => {
    const all = extractAllOtps('code 111222 and again code 111222');
    assert.equal(all.length, 1);
    assert.equal(all[0]?.code, '111222');
  });
});

describe('sanitizeHtml', () => {
  it('removes script tags with content', () => {
    const out = sanitizeHtml('<p>hi</p><script>alert(1)</script>');
    assert.ok(!out.includes('script'));
    assert.ok(out.includes('hi'));
  });

  it('strips inline event handlers', () => {
    const out = sanitizeHtml('<img src="x.png" onerror="steal()">');
    assert.ok(!out.includes('onerror'));
  });

  it('neutralizes javascript: URIs', () => {
    const out = sanitizeHtml('<a href="javascript:alert(1)">x</a>');
    assert.ok(!out.includes('javascript:'));
  });

  it('keeps benign markup', () => {
    const out = sanitizeHtml('<div class="a"><b>bold</b></div>');
    assert.ok(out.includes('<b>bold</b>'));
  });
});
