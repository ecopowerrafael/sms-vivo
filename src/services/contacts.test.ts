import { describe, expect, it } from 'vitest';
import { csvCell, findDuplicates, parseCsv } from './contacts';
import { normalizePhone } from '../utils/sms';
import { parseXlsx } from './contacts';
import { vi } from 'vitest';
vi.mock('read-excel-file', () => ({ default: async () => [['Nome', 'Telefone'], ['Carlos', '11988887777']] }));

describe('contact import', () => {
  it('parses quoted CSV fields', () => { const rows = parseCsv('nome,telefone,empresa\n"Ana, Silva",11999990000,"ACME, Ltda"'); expect(rows[0]).toMatchObject({ name: 'Ana, Silva', phone: '11999990000', company: 'ACME, Ltda' }); });
  it('finds duplicates after phone normalization', () => { const rows = parseCsv('nome,telefone\nAna,(11) 99999-0000\nBia,+55 11 99999-0000'); expect(findDuplicates(rows, normalizePhone)).toEqual([['(11) 99999-0000', '+55 11 99999-0000']]); });
  it('maps the first XLSX worksheet and preserves the import contract', async () => { await expect(parseXlsx(new ArrayBuffer(0))).resolves.toMatchObject([{ name: 'Carlos', phone: '11988887777' }]); });
  it('escapes cells when exporting CSV', () => { expect(csvCell('A "quoted", value')).toBe('"A ""quoted"", value"'); });
});
