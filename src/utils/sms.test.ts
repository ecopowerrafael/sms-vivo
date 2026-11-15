import { describe, expect, it } from 'vitest';
import { normalizePhone, renderTemplate, smsSegments } from './sms';
describe('sms domain rules', () => {
  it('normalizes Brazilian and international numbers', () => { expect(normalizePhone('(11) 99999-0000')).toBe('+5511999990000'); expect(normalizePhone('+351 912 000 000')).toBe('+351912000000'); });
  it('calculates GSM-7 multipart segments', () => { expect(smsSegments('a'.repeat(161)).segments).toBe(2); });
  it('calculates Unicode multipart segments', () => { expect(smsSegments('🙂'.repeat(71)).segments).toBe(2); });
  it('renders missing variables as empty values', () => { expect(renderTemplate('Oi {nome} {empresa}', { nome: 'Ana' })).toBe('Oi Ana '); });
});
