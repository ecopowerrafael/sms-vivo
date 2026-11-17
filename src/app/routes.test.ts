import { describe, expect, it } from 'vitest';
import { hashForRoute, routeFromHash } from './routes';

describe('app routes', () => {
  it('round-trips every supported screen through a hash', () => {
    for (const route of ['home', 'contacts', 'campaigns', 'queue', 'send', 'lists', 'templates', 'history', 'laboratory', 'settings'] as const) {
      expect(routeFromHash(hashForRoute(route))).toBe(route);
    }
  });

  it('falls back safely for an unknown or empty hash', () => {
    expect(routeFromHash('')).toBe('home');
    expect(routeFromHash('#/unknown')).toBe('home');
  });
});
