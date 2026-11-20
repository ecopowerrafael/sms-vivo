import { unzipSync } from 'fflate';
import type { ContactRecord, DeviceContactRecord } from './sms';
import { normalizePhone } from '../utils/sms';

export type ContactInput = Omit<ContactRecord, 'id' | 'normalizedPhone'> & { phoneCandidates?: Array<{ value: string; classification: PhoneClassification }> };
export type PhoneClassification = 'MOBILE_VALID' | 'MOBILE_LEGACY_CANDIDATE' | 'LANDLINE' | 'AMBIGUOUS' | 'INVALID' | 'DUPLICATE';
export type ImportPhoneCounts = Record<PhoneClassification | 'EMPTY', number>;
export type SmsEligibilityReason = 'LANDLINE' | 'MOBILE_LEGACY_CANDIDATE' | 'INVALID' | 'AMBIGUOUS' | 'UNAUTHORIZED' | 'UNSUBSCRIBED' | 'EMPTY';
export type ImportRowError = { row: number; reason: string };
export type ImportProgress = { stage: 'reading' | 'validating' | 'importing' | 'complete'; processed: number; total: number };
export type DeviceContactCandidate = DeviceContactRecord & {
  normalizedPhone: string;
  phoneClassification: PhoneClassification;
  duplicateCount: number;
  sourceIds: string[];
};
export type DeviceContactAnalysis = {
  recordsFound: number;
  completeMobileRecords: number;
  duplicateRecords: number;
  landlineExcluded: number;
  legacyCandidates: number;
  invalidOrAmbiguous: number;
  uniqueMobileAvailable: number;
  candidates: DeviceContactCandidate[];
};

export function normalizeHeader(value: unknown): string { return String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, ''); }
export function normalizeSearch(value: unknown): string { return String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim(); }
function textValue(value: unknown): string { return value == null ? '' : String(value).trim(); }
export function classifyPhone(value: string): PhoneClassification {
  const digits = textValue(value).replace(/\D/g, '').replace(/^55(?=\d{10,11}$)/, '');
  if (!/^\d{10,11}$/.test(digits) || !/^[1-9]\d$/.test(digits.slice(0, 2))) return 'INVALID';
  const subscriber = digits.slice(2);
  if (digits.length === 11) return subscriber.startsWith('9') ? 'MOBILE_VALID' : 'INVALID';
  if (/^[6-9]/.test(subscriber)) return 'MOBILE_LEGACY_CANDIDATE';
  if (/^[2-5]/.test(subscriber)) return 'LANDLINE';
  return 'AMBIGUOUS';
}

function contactNameScore(name: string): number {
  const normalized = name.trim().toLocaleLowerCase();
  if (!normalized || normalized === 'sem nome' || normalized === 'contato da agenda') return 0;
  return normalized.replace(/[^\p{L}\p{N}]/gu, '').length;
}

export function deduplicateDeviceContacts(records: DeviceContactRecord[]): DeviceContactAnalysis {
  const groups = new Map<string, DeviceContactCandidate>();
  let duplicateRecords = 0;
  let completeMobileRecords = 0;
  let landlineExcluded = 0;
  let legacyCandidates = 0;
  let invalidOrAmbiguous = 0;

  records.forEach(record => {
    const classification = classifyPhone(record.phone);
    if (classification === 'MOBILE_VALID') completeMobileRecords += 1;
    else if (classification === 'LANDLINE') landlineExcluded += 1;
    else if (classification === 'MOBILE_LEGACY_CANDIDATE') legacyCandidates += 1;
    else invalidOrAmbiguous += 1;

    let normalizedPhone: string;
    try { normalizedPhone = normalizePhone(record.phone); } catch { normalizedPhone = `invalid:${record.sourceId}`; }
    const existing = groups.get(normalizedPhone);
    if (!existing) {
      groups.set(normalizedPhone, { ...record, normalizedPhone, phoneClassification: classification, duplicateCount: 1, sourceIds: [record.sourceId] });
      return;
    }
    duplicateRecords += 1;
    const preferred = contactNameScore(record.name) > contactNameScore(existing.name) ? record : existing;
    groups.set(normalizedPhone, { ...existing, ...preferred, normalizedPhone, phoneClassification: existing.phoneClassification === 'MOBILE_VALID' || classification !== 'MOBILE_VALID' ? existing.phoneClassification : classification, duplicateCount: existing.duplicateCount + 1, sourceIds: [...existing.sourceIds, record.sourceId] });
  });

  const candidates = [...groups.values()];
  return { recordsFound: records.length, completeMobileRecords, duplicateRecords, landlineExcluded, legacyCandidates, invalidOrAmbiguous, uniqueMobileAvailable: candidates.filter(candidate => candidate.phoneClassification === 'MOBILE_VALID').length, candidates };
}

export function correctLegacyPhone(value: string): string {
  const digits = textValue(value).replace(/\D/g, '').replace(/^55(?=\d{10}$)/, '');
  if (classifyPhone(value) !== 'MOBILE_LEGACY_CANDIDATE') throw new Error('Telefone não é um candidato elegível para correção.');
  return `+55${digits.slice(0, 2)}9${digits.slice(2)}`;
}

export function phoneClassificationLabel(value: PhoneClassification): string {
  return ({ MOBILE_VALID: 'móvel válido', MOBILE_LEGACY_CANDIDATE: 'possível celular antigo', LANDLINE: 'fixo', AMBIGUOUS: 'ambíguo', INVALID: 'inválido', DUPLICATE: 'duplicado' } as Record<PhoneClassification, string>)[value];
}

function mapRow(row: Record<string, unknown>): ContactInput {
  const values = new Map(Object.entries(row).map(([key, value]) => [normalizeHeader(key), textValue(value)]));
  const phoneKeys = [...values.keys()].filter(key => /^(telefone\d*|celular\d*|fone\d*)$/.test(key));
  const phoneCandidates = phoneKeys.map(key => values.get(key) ?? '').filter(Boolean).map(value => ({ value, classification: classifyPhone(value) }));
  const primary = phoneCandidates.find(candidate => candidate.classification === 'MOBILE_VALID')
    ?? phoneCandidates.find(candidate => candidate.classification === 'MOBILE_LEGACY_CANDIDATE')
    ?? phoneCandidates[0];
  const phone = primary?.value ?? '';
  const name = values.get('nome') || values.get('name') || values.get('nomefantasia') || values.get('razaosocial') || '';
  const company = values.get('empresa') || values.get('company') || values.get('nomefantasia') || values.get('razaosocial') || '';
  return {
    name,
    phone,
    originalPhone: phone,
    phoneClassification: classifyPhone(phone),
    correctedAt: undefined,
    company,
    city: values.get('cidade') || values.get('city') || values.get('municipio') || '',
    state: values.get('estado') || values.get('state') || values.get('uf') || '',
    authorized: false,
    unsubscribed: false,
    phoneCandidates,
  };
}

export function isSmsEligibleContact(contact: Pick<ContactRecord, 'authorized' | 'unsubscribed' | 'phone'> & { phoneClassification?: PhoneClassification }): boolean {
  return contact.authorized && !contact.unsubscribed && classifyPhone(contact.phone) === 'MOBILE_VALID';
}

export function smsEligibilityReason(contact: Pick<ContactRecord, 'authorized' | 'unsubscribed' | 'phone'> & { phoneClassification?: PhoneClassification }): SmsEligibilityReason | null {
  if (isSmsEligibleContact(contact)) return null;
  if (contact.unsubscribed) return 'UNSUBSCRIBED';
  if (!contact.authorized) return 'UNAUTHORIZED';
  const classification = classifyPhone(contact.phone);
  return classification === 'LANDLINE' || classification === 'MOBILE_LEGACY_CANDIDATE' || classification === 'INVALID' || classification === 'AMBIGUOUS' ? classification : 'EMPTY';
}

export function summarizeImportPhones(inputs: ContactInput[]): ImportPhoneCounts {
  const counts: ImportPhoneCounts = { MOBILE_VALID: 0, MOBILE_LEGACY_CANDIDATE: 0, LANDLINE: 0, AMBIGUOUS: 0, INVALID: 0, DUPLICATE: 0, EMPTY: 0 };
  inputs.forEach(input => {
    const candidates = input.phoneCandidates ?? (input.phone ? [{ value: input.phone, classification: classifyPhone(input.phone) }] : []);
    if (!candidates.length) { counts.EMPTY += 1; return; }
    candidates.forEach(candidate => { counts[candidate.classification] += 1; });
  });
  return counts;
}

export function parseCsv(text: string): ContactInput[] {
  const rows: string[][] = []; let row: string[] = []; let cell = ''; let quoted = false;
  for (let i = 0; i < text.length; i += 1) { const ch = text[i]; if (ch === '"' && text[i + 1] === '"' && quoted) { cell += '"'; i += 1; } else if (ch === '"') quoted = !quoted; else if (ch === ',' && !quoted) { row.push(cell); cell = ''; } else if ((ch === '\n' || ch === '\r') && !quoted) { if (ch === '\r' && text[i + 1] === '\n') i += 1; row.push(cell); if (row.some(Boolean)) rows.push(row); row = []; cell = ''; } else cell += ch; }
  row.push(cell); if (row.some(Boolean)) rows.push(row); const [header, ...data] = rows; if (!header) return [];
  return mapContactRows(header, data);
}

export function mapContactRows(header: unknown[], data: unknown[][]): ContactInput[] {
  return data.map(values => mapRow(Object.fromEntries(header.map((key, index) => [textValue(key), values[index] ?? ''])))).filter(c => c.name || c.phone);
}

function elementText(element: Element | undefined): string {
  return element ? Array.from(element.querySelectorAll('t')).map(node => node.textContent ?? '').join('') : '';
}

function columnNumber(reference: string): number {
  const letters = reference.match(/^[A-Z]+/i)?.[0].toUpperCase() ?? '';
  return [...letters].reduce((value, letter) => value * 26 + letter.charCodeAt(0) - 64, 0);
}

function parseXlsxRows(file: ArrayBuffer | Uint8Array): { rows: unknown[][]; sheetName: string } {
  const files = unzipSync(file instanceof Uint8Array ? file : new Uint8Array(file));
  const readXml = (path: string) => {
    const bytes = files[path] ?? files[`${path}/`];
    if (!bytes) throw new Error(`Arquivo XLSX sem ${path}.`);
    return new DOMParser().parseFromString(new TextDecoder().decode(bytes), 'application/xml');
  };
  const workbook = readXml('xl/workbook.xml');
  const relationships = readXml('xl/_rels/workbook.xml.rels');
  const relationshipById = new Map(Array.from(relationships.getElementsByTagName('Relationship')).map(item => [item.getAttribute('Id') ?? '', item.getAttribute('Target') ?? '']));
  const sheets = Array.from(workbook.getElementsByTagName('sheet'));
  const selected = sheets.find(sheet => normalizeHeader(sheet.getAttribute('name')) === 'empresas') ?? sheets[0];
  if (!selected) throw new Error('A pasta de trabalho não possui planilhas.');
  const relation = relationshipById.get(selected.getAttribute('r:id') ?? '');
  if (!relation) throw new Error('Não foi possível localizar a planilha principal.');
  const sheetPath = relation.startsWith('/') ? relation.slice(1) : relation.startsWith('xl/') ? relation : `xl/${relation.replace(/^\.\//, '')}`;
  const sheet = readXml(sheetPath);
  const shared = files['xl/sharedStrings.xml'] ? readXml('xl/sharedStrings.xml') : undefined;
  const sharedValues = shared ? Array.from(shared.getElementsByTagName('si')).map(item => elementText(item)) : [];
  const rows = new Map<number, Map<number, string>>();
  let maxRow = 0; let maxColumn = 0;
  for (const rowElement of Array.from(sheet.getElementsByTagName('row'))) {
    const rowNumber = Number(rowElement.getAttribute('r')) || maxRow + 1;
    let nextColumn = 1;
    const values = new Map<number, string>();
    for (const cell of Array.from(rowElement.getElementsByTagName('c'))) {
      const reference = cell.getAttribute('r');
      const column = reference ? columnNumber(reference) : nextColumn;
      nextColumn = column + 1;
      const type = cell.getAttribute('t');
      const raw = type === 'inlineStr' ? elementText(cell.querySelector('is') ?? undefined) : cell.querySelector('v')?.textContent ?? '';
      const value = type === 's' ? (sharedValues[Number(raw)] ?? '') : raw;
      values.set(column, value);
      maxColumn = Math.max(maxColumn, column);
    }
    if (values.size) { rows.set(rowNumber, values); maxRow = Math.max(maxRow, rowNumber); }
  }
  return { sheetName: selected.getAttribute('name') ?? '', rows: Array.from({ length: maxRow }, (_, rowIndex) => Array.from({ length: maxColumn }, (_, columnIndex) => rows.get(rowIndex + 1)?.get(columnIndex + 1) ?? '')) };
}

export async function parseXlsx(file: ArrayBuffer | Uint8Array): Promise<ContactInput[]> {
  const { rows } = parseXlsxRows(file); const [header, ...data] = rows; if (!header?.length) return [];
  return mapContactRows(header, data);
}

export async function parseContactsFile(file: File, onProgress?: (progress: ImportProgress) => void): Promise<{ inputs: ContactInput[]; errors: ImportRowError[]; sheetName?: string }> {
  onProgress?.({ stage: 'reading', processed: 0, total: 0 });
  const isXlsx = file.name.toLowerCase().endsWith('.xlsx');
  if (!isXlsx && !file.name.toLowerCase().endsWith('.csv')) throw new Error('Formato não suportado. Selecione um arquivo CSV ou XLSX.');
  const inputs = isXlsx ? await parseXlsx(await file.arrayBuffer()) : parseCsv(await file.text());
  const errors: ImportRowError[] = [];
  inputs.forEach((contact, index) => { if (!contact.name && !contact.phone) errors.push({ row: index + 2, reason: 'Nome e telefone vazios.' }); else if (!contact.phone) errors.push({ row: index + 2, reason: 'Telefone vazio.' }); });
  onProgress?.({ stage: 'validating', processed: inputs.length, total: inputs.length });
  return { inputs, errors };
}

export function findDuplicates(contacts: ContactInput[], normalize: (phone: string) => string): string[][] {
  const groups = new Map<string, string[]>(); contacts.forEach(c => { try { const key = normalize(c.phone); groups.set(key, [...(groups.get(key) ?? []), c.phone]); } catch { /* invalid numbers remain for validation */ } });
  return [...groups.values()].filter(group => group.length > 1);
}

export function csvCell(value: unknown): string { return `"${String(value ?? '').replaceAll('"', '""')}"`; }
