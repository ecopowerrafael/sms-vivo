import { describe, expect, it } from 'vitest';
import { csvCell, findDuplicates, parseCsv } from './contacts';
import { normalizePhone } from '../utils/sms';
import { mapContactRows } from './contacts';

describe('contact import', () => {
  it('parses quoted CSV fields', () => { const rows = parseCsv('nome,telefone,empresa\n"Ana, Silva",11999990000,"ACME, Ltda"'); expect(rows[0]).toMatchObject({ name: 'Ana, Silva', phone: '11999990000', company: 'ACME, Ltda' }); });
  it('finds duplicates after phone normalization', () => { const rows = parseCsv('nome,telefone\nAna,(11) 99999-0000\nBia,+55 11 99999-0000'); expect(findDuplicates(rows, normalizePhone)).toEqual([['(11) 99999-0000', '+55 11 99999-0000']]); });
  it('maps XLSX rows and preserves the import contract', () => { expect(mapContactRows(['Nome', 'Telefone'], [['Carlos', '11988887777']])).toMatchObject([{ name: 'Carlos', phone: '11988887777' }]); });
  it('accepts company-list headers and falls back to the second phone', () => {
    expect(mapContactRows(['CNPJ', 'Razão social', 'Nome fantasia', 'Município', 'UF', 'Telefone 1', 'Telefone 2'], [
      ['00.000.000/0001-00', 'Empresa Exemplo LTDA', 'Oficina Exemplo', 'Barueri', 'SP', '', '(11) 99999-0000'],
    ])).toMatchObject([{
      name: 'Oficina Exemplo',
      phone: '(11) 99999-0000',
      company: 'Oficina Exemplo',
      city: 'Barueri',
      state: 'SP',
    }]);
  });
  it('escapes cells when exporting CSV', () => { expect(csvCell('A "quoted", value')).toBe('"A ""quoted"", value"'); });
});
