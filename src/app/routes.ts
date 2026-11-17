export type AppRoute =
  | 'home'
  | 'contacts'
  | 'campaigns'
  | 'queue'
  | 'send'
  | 'lists'
  | 'templates'
  | 'history'
  | 'laboratory'
  | 'settings';

export const routePaths: Record<AppRoute, string> = {
  home: '/home',
  contacts: '/contacts',
  campaigns: '/campaigns',
  queue: '/queue',
  send: '/send',
  lists: '/lists',
  templates: '/templates',
  history: '/history',
  laboratory: '/laboratory',
  settings: '/settings',
};

const pathRoutes = new Map(Object.entries(routePaths).map(([route, path]) => [path, route as AppRoute]));

export function routeFromHash(hash: string): AppRoute {
  return pathRoutes.get(hash.replace(/^#/, '').split('?')[0]) ?? 'home';
}

export function hashForRoute(route: AppRoute): string {
  return `#${routePaths[route]}`;
}

