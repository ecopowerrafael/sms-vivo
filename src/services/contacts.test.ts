import { describe, expect, it } from 'vitest';
import { classifyPhone, correctLegacyPhone, csvCell, findDuplicates, isSmsEligibleContact, mapContactRows, parseCsv, summarizeImportPhones } from './contacts';
import { normalizePhone } from '../utils/sms';

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
  it('prefers a valid mobile in the second column over a landline in the first', () => {
    const [contact] = mapContactRows(['Nome', 'Telefone 1', 'Telefone 2'], [['Oficina', '1133334444', '11999990000']]);
    expect(contact).toMatchObject({ phone: '11999990000', phoneClassification: 'MOBILE_VALID' });
    expect(contact.phoneCandidates).toEqual([
      { value: '1133334444', classification: 'LANDLINE' },
      { value: '11999990000', classification: 'MOBILE_VALID' },
    ]);
  });
  it('keeps two mobile candidates identifiable without duplicating the contact', () => {
    const [contact] = mapContactRows(['Nome', 'Telefone 1', 'Telefone 2'], [['Eleva', '11999990000', '11988887777']]);
    expect(contact.phone).toBe('11999990000');
    expect(contact.phoneCandidates?.filter(candidate => candidate.classification === 'MOBILE_VALID')).toHaveLength(2);
  });
  it('escapes cells when exporting CSV', () => { expect(csvCell('A "quoted", value')).toBe('"A ""quoted"", value"'); });
  it('classifies Brazilian mobile, legacy, landline and ambiguous numbers', () => {
    expect(classifyPhone('11988887777')).toBe('MOBILE_VALID');
    expect(classifyPhone('1188887777')).toBe('MOBILE_LEGACY_CANDIDATE');
    expect(classifyPhone('1133334444')).toBe('LANDLINE');
    expect(classifyPhone('1100000000')).toBe('AMBIGUOUS');
    expect(classifyPhone('000')).toBe('INVALID');
  });
  it('corrects only eligible legacy mobile numbers and preserves the original separately', () => { expect(correctLegacyPhone('1187654321')).toBe('+5511987654321'); expect(() => correctLegacyPhone('1133334444')).toThrow(); });
  it('does not add a second ninth digit or alter a complete mobile', () => { expect(classifyPhone('11987654321')).toBe('MOBILE_VALID'); expect(() => correctLegacyPhone('11987654321')).toThrow(); });
  it('requires a complete valid mobile for SMS eligibility', () => {
    expect(isSmsEligibleContact({ authorized: true, unsubscribed: false, phone: '1133334444' })).toBe(false);
    expect(isSmsEligibleContact({ authorized: true, unsubscribed: false, phone: '11999990000' })).toBe(true);
    expect(isSmsEligibleContact({ authorized: false, unsubscribed: false, phone: '11999990000' })).toBe(false);
  });
  it('counts every imported phone candidate and empty rows by exclusion reason', () => {
    expect(summarizeImportPhones(mapContactRows(['Nome', 'Telefone 1', 'Telefone 2'], [
      ['A', '1133334444', '11999990000'],
      ['B', '000', ''],
      ['C', '', ''],
    ]))).toMatchObject({ LANDLINE: 1, MOBILE_VALID: 1, INVALID: 1, EMPTY: 1 });
  });
});
