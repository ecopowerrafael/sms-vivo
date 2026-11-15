import { describe, expect, it } from 'vitest';
import { cancelPending, recoverAfterProcessStop, transition } from './queue';

describe('queue state machine', () => {
  it('does not auto-retry accepted or unknown requests', () => { expect(() => transition({ requestId: '1', state: 'SENT', attemptCount: 1 }, 'PROCESSING')).toThrow(); expect(() => transition({ requestId: '2', state: 'UNKNOWN', attemptCount: 1 }, 'PROCESSING')).toThrow(); });
  it('recovers processing items as unknown', () => { expect(recoverAfterProcessStop([{ requestId: '1', state: 'PROCESSING', attemptCount: 1 }, { requestId: '2', state: 'PENDING', attemptCount: 0 }])).toMatchObject([{ state: 'UNKNOWN' }, { state: 'PENDING' }]); });
  it('cancels only pending items', () => { expect(cancelPending([{ requestId: '1', state: 'PENDING', attemptCount: 0 }, { requestId: '2', state: 'SENT', attemptCount: 1 }])).toMatchObject([{ state: 'CANCELLED' }, { state: 'SENT' }]); });
});
