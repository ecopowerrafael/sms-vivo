import readXlsxFile from 'read-excel-file';
import type { ContactRecord } from './sms';

export type ContactInput = Omit<ContactRecord, 'id' | 'normalizedPhone'>;

function normalizeHeader(value: unknown): string { return String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, ''); }
function mapRow(row: Record<string, unknown>): ContactInput {
  const values = new Map(Object.entries(row).map(([key, value]) => [normalizeHeader(key), String(value ?? '').trim()]));
  return {
    name: values.get('nome') || values.get('name') || values.get('nomefantasia') || values.get('razaosocial') || '',
    phone: values.get('telefone') || values.get('phone') || values.get('celular') || values.get('telefone1') || values.get('telefone2') || '',
    company: values.get('empresa') || values.get('company') || values.get('nomefantasia') || values.get('razaosocial') || '',
    city: values.get('cidade') || values.get('city') || values.get('municipio') || '',
    state: values.get('estado') || values.get('state') || values.get('uf') || '',
    authorized: false,
    unsubscribed: false,
  };
}

export function parseCsv(text: string): ContactInput[] {
  const rows: string[][] = []; let row: string[] = []; let cell = ''; let quoted = false;
  for (let i = 0; i < text.length; i += 1) { const ch = text[i]; if (ch === '"' && text[i + 1] === '"' && quoted) { cell += '"'; i += 1; } else if (ch === '"') quoted = !quoted; else if (ch === ',' && !quoted) { row.push(cell); cell = ''; } else if ((ch === '\n' || ch === '\r') && !quoted) { if (ch === '\r' && text[i + 1] === '\n') i += 1; row.push(cell); if (row.some(Boolean)) rows.push(row); row = []; cell = ''; } else cell += ch; }
  row.push(cell); if (row.some(Boolean)) rows.push(row); const [header, ...data] = rows; if (!header) return [];
  return data.map(values => mapRow(Object.fromEntries(header.map((key, index) => [key, values[index] ?? ''])))).filter(c => c.name || c.phone);
}

export async function parseXlsx(file: ArrayBuffer): Promise<ContactInput[]> {
  const rows = await readXlsxFile(file); const [header, ...data] = rows; if (!header) return [];
  return data.map(values => mapRow(Object.fromEntries(header.map((key, index) => [String(key ?? ''), values[index] ?? ''])))).filter(c => c.name || c.phone);
}

export function findDuplicates(contacts: ContactInput[], normalize: (phone: string) => string): string[][] {
  const groups = new Map<string, string[]>(); contacts.forEach(c => { try { const key = normalize(c.phone); groups.set(key, [...(groups.get(key) ?? []), c.phone]); } catch { /* invalid numbers remain for validation */ } });
  return [...groups.values()].filter(group => group.length > 1);
}

export function csvCell(value: unknown): string { return `"${String(value ?? '').replaceAll('"', '""')}"`; }
