const GSM_7_BASIC = /^[@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ !"#¤%&'()*+,\-./0-9:;<=>?¡A-ZÄÖÑÜ§¿a-zäöñüà]*$/;
export function normalizePhone(value: string, countryCode = '55'): string {
  const trimmed = value.trim(); const digits = trimmed.replace(/\D/g, '');
  if (!digits) throw new Error('Informe um telefone.');
  if (trimmed.startsWith('+')) return `+${digits}`;
  if (digits.startsWith(countryCode)) return `+${digits}`;
  if (digits.length === 10 || digits.length === 11) return `+${countryCode}${digits}`;
  return `+${digits}`;
}
export function smsEncoding(text: string): 'GSM-7' | 'UNICODE' { return GSM_7_BASIC.test(text) ? 'GSM-7' : 'UNICODE'; }
export function smsSegments(text: string): { encoding: 'GSM-7' | 'UNICODE'; segments: number; units: number; limit: number } {
  const encoding = smsEncoding(text); const units = [...text].length; const limit = encoding === 'GSM-7' ? (units <= 160 ? 160 : 153) : (units <= 70 ? 70 : 67);
  return { encoding, units, limit, segments: Math.max(1, Math.ceil(units / limit)) };
}
export function renderTemplate(template: string, fields: Record<string, string | undefined>): string { return template.replace(/\{([\w]+)\}/g, (_, key: string) => fields[key] ?? ''); }
