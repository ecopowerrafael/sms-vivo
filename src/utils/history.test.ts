import { describe, expect, it } from 'vitest';
import { filterHistory } from './history';

const results = [
  { requestId: '1', state: 'FAILED', sentAt: '2026-11-10T10:00:00Z', destination: '+5511999990000', campaignId: 2, listId: 7, subscriptionId: 11, code: 5, message: 'sem sinal' },
  { requestId: '2', state: 'DELIVERED', sentAt: '2026-11-12T10:00:00Z', destination: '+5511888880000', campaignId: 3, listId: 8, subscriptionId: 12 },
] as const;

describe('history filters', () => {
  it('filters by date, list, chip and failure code', () => {
    expect(filterHistory(results, { from: '2026-11-10', to: '2026-11-10', listId: '7', subscriptionId: '11', failure: '5' }).map(item => item.requestId)).toEqual(['1']);
  });

  it('does not match an unrelated campaign or number', () => {
    expect(filterHistory(results, { campaignId: '2', phone: '8888' })).toEqual([]);
  });
});
