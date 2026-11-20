import { describe, expect, it } from 'vitest';
import { classifyPhone, correctLegacyPhone, csvCell, deduplicateDeviceContacts, findDuplicates, isSmsEligibleContact, mapContactRows, parseCsv, summarizeImportPhones } from './contacts';
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
  it('deduplicates Brazilian mobile formats by normalized phone and preserves the most readable name', () => {
    const result = deduplicateDeviceContacts([
      { sourceId: '1:1', contactId: 1, name: 'A', phone: '(15) 99781-3981' },
      { sourceId: '1:2', contactId: 1, name: 'Ana Maria Oficina', phone: '15997813981' },
      { sourceId: '2:1', contactId: 2, name: 'Ana Maria Oficina', phone: '+55 15 99781-3981' },
      { sourceId: '3:1', contactId: 3, name: 'Outro', phone: '5515997813981' },
    ]);
    expect(result.recordsFound).toBe(4);
    expect(result.duplicateRecords).toBe(3);
    expect(result.uniqueMobileAvailable).toBe(1);
    expect(result.candidates[0]).toMatchObject({ normalizedPhone: '+5515997813981', name: 'Ana Maria Oficina', duplicateCount: 4 });
  });
  it('keeps different numbers from the same source contact and excludes fixed/legacy numbers from direct selection', () => {
    const result = deduplicateDeviceContacts([
      { sourceId: '4:1', contactId: 4, name: 'Empresa', phone: '1133334444' },
      { sourceId: '4:2', contactId: 4, name: 'Empresa', phone: '1188887777' },
      { sourceId: '5:1', contactId: 5, name: 'Móvel', phone: '11999990000' },
    ]);
    expect(result.candidates).toHaveLength(3);
    expect(result.landlineExcluded).toBe(1);
    expect(result.legacyCandidates).toBe(1);
    expect(result.uniqueMobileAvailable).toBe(1);
  });
  it('handles more than ten thousand records without duplicating selection keys', () => {
    const records = Array.from({ length: 11000 }, (_, index) => ({ sourceId: `bulk:${index}`, contactId: index, name: `Contato ${index}`, phone: `119${String(index).padStart(8, '0')}` }));
    const result = deduplicateDeviceContacts(records);
    expect(result.recordsFound).toBe(11000);
    expect(new Set(result.candidates.filter(item => item.phoneClassification === 'MOBILE_VALID').map(item => item.normalizedPhone)).size).toBe(result.uniqueMobileAvailable);
  });
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
