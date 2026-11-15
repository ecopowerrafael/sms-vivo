import type { SmsResult } from '../types/sms';

export interface HistoryFilters {
  state?: string;
  phone?: string;
  campaignId?: string;
  listId?: string;
  subscriptionId?: string;
  failure?: string;
  from?: string;
  to?: string;
}

export function filterHistory(results: readonly SmsResult[], filters: HistoryFilters): SmsResult[] {
  const phone = filters.phone?.trim().toLowerCase() ?? '';
  const failure = filters.failure?.trim().toLowerCase() ?? '';
  const from = filters.from ? Date.parse(`${filters.from}T00:00:00`) : undefined;
  const to = filters.to ? Date.parse(`${filters.to}T23:59:59.999`) : undefined;
  return results.filter(result => {
    const timestamp = Date.parse(result.sentAt);
    const searchableFailure = `${result.code ?? ''} ${result.message ?? ''}`.toLowerCase();
    return (!filters.state || filters.state === 'ALL' || result.state === filters.state)
      && (!phone || (result.destination ?? '').toLowerCase().includes(phone))
      && (!filters.campaignId || filters.campaignId === 'ALL' || String(result.campaignId ?? '') === filters.campaignId)
      && (!filters.listId || filters.listId === 'ALL' || String(result.listId ?? '') === filters.listId)
      && (!filters.subscriptionId || filters.subscriptionId === 'ALL' || String(result.subscriptionId ?? '') === filters.subscriptionId)
      && (!failure || searchableFailure.includes(failure))
      && (from === undefined || timestamp >= from)
      && (to === undefined || timestamp <= to);
  });
}
