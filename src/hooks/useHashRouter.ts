import { useCallback, useEffect, useState } from 'react';
import type { AppRoute } from '../app/routes';
import { hashForRoute, routeFromHash } from '../app/routes';

export function useHashRouter() {
  const [route, setRoute] = useState<AppRoute>(() => routeFromHash(window.location.hash));

  useEffect(() => {
    const onChange = () => setRoute(routeFromHash(window.location.hash));
    window.addEventListener('hashchange', onChange);
    window.addEventListener('popstate', onChange);
    if (!window.location.hash) window.history.replaceState(null, '', hashForRoute('home'));
    return () => {
      window.removeEventListener('hashchange', onChange);
      window.removeEventListener('popstate', onChange);
    };
  }, []);

  const navigate = useCallback((next: AppRoute) => {
    const nextHash = hashForRoute(next);
    if (window.location.hash === nextHash) return;
    window.location.hash = nextHash;
  }, []);

  return { route, navigate };
}

