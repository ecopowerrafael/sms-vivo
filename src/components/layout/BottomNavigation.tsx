import type { AppRoute } from '../../app/routes';

type NavItem = { route: AppRoute; label: string; icon: string };
const items: NavItem[] = [
  { route: 'home', label: 'Início', icon: '⌂' },
  { route: 'contacts', label: 'Contatos', icon: '◎' },
  { route: 'campaigns', label: 'Campanhas', icon: '◈' },
  { route: 'queue', label: 'Fila', icon: '⇄' },
];

export function BottomNavigation({ route, onNavigate, onMore }: { route: AppRoute; onNavigate: (route: AppRoute) => void; onMore: () => void }) {
  return <nav className="primary-navigation" aria-label="Navegação principal">
    {items.map(item => <button key={item.route} className={route === item.route ? 'nav-item active' : 'nav-item'} onClick={() => onNavigate(item.route)}><span aria-hidden="true">{item.icon}</span><small>{item.label}</small></button>)}
    <button className="nav-item" onClick={onMore}><span aria-hidden="true">•••</span><small>Mais</small></button>
  </nav>;
}

