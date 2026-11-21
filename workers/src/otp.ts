const CODE_PATTERNS: RegExp[] = [
  /(?:code|otp|pin|password|verification(?:\s+code)?|one[-\s]?time(?:\s+(?:password|code))?|2fa|auth(?:entication)?\s+code)\D{0,20}(\d{4,8})\b/gi,
  /\b(\d{4,8})\s*(?:is\s+your|(?:is\s+)?your\s+(?:verification\s+)?code)\b/gi,
  /\b(?:code|otp|pin)\s*[:#]?\s*(\d{4,8})\b/gi,
  /\b(\d{6})\b(?=[^\d]*(?:verify|confirm|sign\s*in|log\s*in|authenticate|one[-\s]?time))/gi,
];

const LINK_PATTERNS: RegExp[] = [
  /https?:\/\/[^\s"'<>()]+(?:verify|confirm|activate|auth|otp|token|magic)[^\s"'<>()]*/gi,
  /https?:\/\/[^\s"'<>()]*\?(?:[^&\s]*&)*(?:token|code|otp|verify|hash)=[^\s"'<>&]+/gi,
];

const EXCLUDED_CONTEXT =
  /(order|invoice|amount|total|price|balance|transaction|reference|invoice\s*number|phone|tel|mobile|date|birthday|amount\s*due)/i;

function stripHtml(raw: string): string {
  if (!raw.includes('<')) return raw;
  return raw
    .replace(/<(script|style)[^>]*>[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/\s+/g, ' ')
    .trim();
}

function isPlausibleOtp(candidate: string, text: string, start: number, end: number): boolean {
  if (candidate.length < 4 || candidate.length > 8) return false;
  if (/^0+$/.test(candidate) || /^1+$/.test(candidate)) return false;
  const contextStart = Math.max(0, start - 60);
  const contextEnd = Math.min(text.length, end + 60);
  const context = text.slice(contextStart, contextEnd);
  if (EXCLUDED_CONTEXT.test(context) && !/code/i.test(context)) {
    const near = text.slice(Math.max(0, start), Math.min(text.length, end + 30));
    if (!/code|otp|pin|verify/i.test(near)) return false;
  }
  return true;
}

/** Returns a plausible OTP found in raw text/HTML, or null. Never fabricates codes. */
export function extractOtp(raw: string): { code: string; source: string } | null {
  const all = extractAllOtps(raw);
  return all[0] ?? null;
}

export function extractAllOtps(raw: string): { code: string; source: string }[] {
  if (!raw.trim()) return [];
  const text = stripHtml(raw);
  const found: { code: string; source: string }[] = [];
  const seen = new Set<string>();
  for (const pattern of CODE_PATTERNS) {
    pattern.lastIndex = 0;
    let m: RegExpExecArray | null;
    while ((m = pattern.exec(text)) !== null) {
      const candidate = m[1];
      if (!candidate) continue;
      if (seen.has(candidate)) continue;
      if (isPlausibleOtp(candidate, text, m.index, m.index + m[0].length)) {
        seen.add(candidate);
        found.push({ code: candidate, source: 'message body' });
      }
      if (m.index === pattern.lastIndex) pattern.lastIndex++;
    }
  }
  return found;
}

export function findVerificationLinks(raw: string): string[] {
  if (!raw.trim()) return [];
  const text = stripHtml(raw);
  const links: string[] = [];
  const seen = new Set<string>();
  for (const pattern of LINK_PATTERNS) {
    pattern.lastIndex = 0;
    let m: RegExpExecArray | null;
    while ((m = pattern.exec(text)) !== null) {
      const url = m[0].replace(/[.,)\]]+$/, '');
      if (url.startsWith('http') && !seen.has(url)) {
        seen.add(url);
        links.push(url);
      }
      if (m.index === pattern.lastIndex) pattern.lastIndex++;
    }
    if (links.length >= 10) break;
  }
  return links.slice(0, 10);
}

/** Sanitize untrusted email HTML for safe rendering in a WebView. */
export function sanitizeHtml(raw: string): string {
  if (!raw.trim()) return '';
  let html = raw
    .replace(/<(script|style|iframe|object|embed|form|meta|link)[^>]*>[\s\S]*?<\/\1>/gi, '')
    .replace(/<(script|style|iframe|object|embed|form|meta|link)[^>]*\/?>/gi, '')
    .replace(/\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/javascript\s*:/gi, '')
    .replace(/href\s*=\s*("|')\s*javascript:[^"']*("|')/gi, 'href="#"')
    .replace(/src\s*=\s*("|')\s*javascript:[^"']*("|')/gi, 'src=""');
  html = html.replace(/(src|href)\s*=\s*("|')\s*data:text\/html[^"']*\2/gi, '$1="#"');
  return html;
}
