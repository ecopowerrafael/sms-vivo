import { unzipSync } from 'fflate';
import type { ContactRecord } from './sms';

export type ContactInput = Omit<ContactRecord, 'id' | 'normalizedPhone'>;
export type ImportRowError = { row: number; reason: string };
export type ImportProgress = { stage: 'reading' | 'validating' | 'importing' | 'complete'; processed: number; total: number };

export function normalizeHeader(value: unknown): string { return String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, ''); }
export function normalizeSearch(value: unknown): string { return String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim(); }
function textValue(value: unknown): string { return value == null ? '' : String(value).trim(); }
function mapRow(row: Record<string, unknown>): ContactInput {
  const values = new Map(Object.entries(row).map(([key, value]) => [normalizeHeader(key), textValue(value)]));
  const firstPhone = values.get('telefone1') || values.get('telefone') || values.get('celular') || '';
  const secondPhone = values.get('telefone2') || '';
  const name = values.get('nome') || values.get('name') || values.get('nomefantasia') || values.get('razaosocial') || '';
  const company = values.get('empresa') || values.get('company') || values.get('nomefantasia') || values.get('razaosocial') || '';
  return {
    name,
    phone: firstPhone || secondPhone,
    company,
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
  inputs.forEach((contact, index) => { if (!contact.name && !contact.phone) errors.push({ row: index + 2, reason: 'Nome e telefone vazios.' }); });
  onProgress?.({ stage: 'validating', processed: inputs.length, total: inputs.length });
  return { inputs, errors };
}

export function findDuplicates(contacts: ContactInput[], normalize: (phone: string) => string): string[][] {
  const groups = new Map<string, string[]>(); contacts.forEach(c => { try { const key = normalize(c.phone); groups.set(key, [...(groups.get(key) ?? []), c.phone]); } catch { /* invalid numbers remain for validation */ } });
  return [...groups.values()].filter(group => group.length > 1);
}

export function csvCell(value: unknown): string { return `"${String(value ?? '').replaceAll('"', '""')}"`; }
