export type QueueState = 'PENDING' | 'PROCESSING' | 'SENT' | 'DELIVERED' | 'FAILED' | 'UNKNOWN' | 'CANCELLED' | 'SKIPPED';
export interface QueueItem { requestId: string; state: QueueState; attemptCount: number; }

export function transition(item: QueueItem, next: QueueState): QueueItem {
  if (item.state === 'DELIVERED' || item.state === 'CANCELLED' || item.state === 'SKIPPED') throw new Error(`Estado terminal: ${item.state}`);
  if (item.state === 'SENT' && next === 'PROCESSING') throw new Error('Reenvio automático bloqueado após aceitação');
  if (item.state === 'UNKNOWN' && next === 'PROCESSING') throw new Error('Resultado desconhecido exige decisão manual');
  return { ...item, state: next, attemptCount: next === 'PROCESSING' ? item.attemptCount + 1 : item.attemptCount };
}

export function recoverAfterProcessStop(items: QueueItem[]): QueueItem[] { return items.map(item => item.state === 'PROCESSING' ? { ...item, state: 'UNKNOWN' } : item); }
export function cancelPending(items: QueueItem[]): QueueItem[] { return items.map(item => item.state === 'PENDING' ? { ...item, state: 'CANCELLED' } : item); }
