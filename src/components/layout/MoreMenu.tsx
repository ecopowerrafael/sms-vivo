import type { AppRoute } from '../../app/routes';

const moreItems: Array<{ route: AppRoute; label: string; description: string }> = [
  { route: 'send', label: 'Enviar SMS', description: 'Envio unitário pelo chip' },
  { route: 'lists', label: 'Listas de contatos', description: 'Organize seus destinatários' },
  { route: 'templates', label: 'Modelos de mensagens', description: 'Textos reutilizáveis' },
  { route: 'history', label: 'Histórico', description: 'Resultados persistidos' },
  { route: 'laboratory', label: 'Laboratório SMS', description: 'Testes controlados' },
  { route: 'settings', label: 'Configurações', description: 'Permissões e telefonia' },
];

export function MoreMenu({ open, route, onNavigate, onClose }: { open: boolean; route: AppRoute; onNavigate: (route: AppRoute) => void; onClose: () => void }) {
  if (!open) return null;
  return <div className="more-backdrop" role="presentation" onClick={onClose}><aside className="more-panel" role="dialog" aria-label="Mais funcionalidades" onClick={event => event.stopPropagation()}><div className="more-header"><div><span className="eyebrow">NAVEGAR</span><h2>Mais funcionalidades</h2></div><button className="icon-button" onClick={onClose} aria-label="Fechar">×</button></div>{moreItems.map(item => <button key={item.route} className={route === item.route ? 'more-item active' : 'more-item'} onClick={() => { onNavigate(item.route); onClose(); }}><span className="more-item-icon" aria-hidden="true">{item.route === 'settings' ? '•' : '›'}</span><span><strong>{item.label}</strong><small>{item.description}</small></span></button>)}</aside></div>;
}

