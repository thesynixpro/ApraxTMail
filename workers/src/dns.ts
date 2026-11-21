import { serviceUnavailable } from './errors.js';

export interface DnsRecordSpec {
  type: 'MX' | 'TXT' | 'CNAME';
  /** '@' for the apex, an FQDN, or a label relative to the domain (e.g. "emailit"). */
  host: string;
  /** MX: mail exchanger host. TXT/CNAME: the full record value. */
  value: string;
  priority?: number;
  /** Required records must all resolve for the domain to verify. Defaults to true. */
  required?: boolean;
}

export interface RecordCheck {
  type: string;
  name: string;
  value: string;
  ok: boolean;
  found: string[];
}

export interface DnsCheckResult {
  allOk: boolean;
  mxOk: boolean;
  txtOk: boolean;
  checks: RecordCheck[];
  missing: string[];
}

interface DohJson {
  Status: number;
  Answer?: { name: string; type: number; data: string }[];
}

const TYPE_CODE = { MX: 15, TXT: 16, CNAME: 5 } as const;

export function fqdn(host: string, domain: string): string {
  const h = host.trim().toLowerCase().replace(/\.$/, '');
  const d = domain.trim().toLowerCase().replace(/\.$/, '');
  if (!h || h === '@' || h === d) return d;
  // Absolute iff it already ends with the domain; anything else (including
  // dotted relative labels like "emailit._domainkey") is relative to it.
  if (h.endsWith(`.${d}`)) return h;
  return `${h}.${d}`;
}

export function normalizeHost(value: string): string {
  return value.trim().toLowerCase().replace(/\.$/, '');
}

/** Extract the exchange host from DoH MX data like "10 mx.example.com.". */
export function parseMxHost(data: string): string {
  const parts = data.trim().split(/\s+/);
  return normalizeHost(parts[parts.length - 1] ?? '');
}

/** TXT data may arrive quoted and split into chunks: "part1" "part2". */
export function parseTxtValue(data: string): string {
  const chunks = data.match(/"([^"]*)"/g);
  if (chunks) return chunks.map((c) => c.slice(1, -1)).join('');
  return data.trim();
}

async function dohQuery(name: string, type: keyof typeof TYPE_CODE): Promise<string[]> {
  const url = `https://cloudflare-dns.com/dns-query?name=${encodeURIComponent(name)}&type=${type}`;
  let res: Response;
  try {
    res = await fetch(url, { headers: { accept: 'application/dns-json' } });
  } catch {
    throw serviceUnavailable('dns_lookup_failed', 'DNS lookup failed, try again');
  }
  if (!res.ok) throw serviceUnavailable('dns_lookup_failed', 'DNS lookup failed, try again');
  const body = (await res.json()) as DohJson;
  // 0 = NOERROR, 3 = NXDOMAIN (treated as "record not present")
  if (body.Status !== 0 && body.Status !== 3) {
    throw serviceUnavailable('dns_lookup_failed', `DNS lookup returned status ${body.Status}`);
  }
  return (body.Answer ?? []).filter((a) => a.type === TYPE_CODE[type]).map((a) => a.data);
}

/**
 * Checks every required record at its own host (records live on subdomains
 * like `inbound.example.com` and `emailit._domainkey.example.com`, not only
 * the apex). All required records must pass for allOk.
 */
export async function checkRecords(domain: string, specs: DnsRecordSpec[]): Promise<DnsCheckResult> {
  const checks = await Promise.all(
    specs.map(async (spec): Promise<RecordCheck> => {
      const name = fqdn(spec.host, domain);
      const found = await dohQuery(name, spec.type);
      let ok = false;
      if (spec.type === 'MX') {
        const target = normalizeHost(spec.value.replace(/^\d+\s+/, ''));
        ok = found.map(parseMxHost).includes(target);
      } else if (spec.type === 'TXT') {
        const want = spec.value.trim();
        ok = found.map(parseTxtValue).some((t) => t === want || t.includes(want));
      } else if (spec.type === 'CNAME') {
        const target = normalizeHost(spec.value);
        ok = found.map(normalizeHost).includes(target);
      }
      return { type: spec.type, name, value: spec.value, ok, found };
    }),
  );

  const required = specs.map((s, i) => ({ spec: s, check: checks[i] as RecordCheck }));
  const requiredChecks = required.filter((r) => r.spec.required !== false);
  const missing = requiredChecks.filter((r) => !r.check.ok).map((r) => `${r.check.type} ${r.check.name}`);
  const mxRequired = required.filter((r) => r.spec.required !== false && r.spec.type === 'MX');
  const txtRequired = required.filter((r) => r.spec.required !== false && r.spec.type === 'TXT');

  return {
    allOk: requiredChecks.length > 0 && requiredChecks.every((r) => r.check.ok),
    mxOk: mxRequired.length > 0 && mxRequired.every((r) => r.check.ok),
    txtOk: txtRequired.length > 0 && txtRequired.every((r) => r.check.ok),
    checks,
    missing,
  };
}

/** Host where inbound mail arrives (Emailit's inbound MX), falling back to the apex. */
export function receivingHost(records: DnsRecordSpec[], domainName: string): string {
  const inbound = records.find(
    (r) => r.type === 'MX' && normalizeHost(r.value.replace(/^\d+\s+/, '')) === 'inbound.emailitmail.com',
  );
  return inbound ? fqdn(inbound.host, domainName) : domainName.trim().toLowerCase().replace(/\.$/, '');
}
