import { useEffect, useState, type ReactNode } from 'react';
import type { AppRoute } from '../../app/routes';
import { BottomNavigation } from './BottomNavigation';
import { MoreMenu } from './MoreMenu';
import { PageHeader } from './PageHeader';

const labels: Record<AppRoute, { title: string; description: string }> = {
  home: { title: 'Visão geral', description: 'Acompanhe o envio direto pelo chip Vivo.' },
  contacts: { title: 'Contatos', description: 'Importe, organize e autorize destinatários.' },
  campaigns: { title: 'Campanhas', description: 'Monte uma campanha em etapas claras.' },
  queue: { title: 'Fila de envios', description: 'Controle o processamento persistente.' },
  send: { title: 'Enviar SMS', description: 'Faça um envio unitário controlado.' },
  lists: { title: 'Listas de contatos', description: 'Crie grupos para reutilizar em campanhas.' },
  templates: { title: 'Modelos de mensagens', description: 'Salve textos e variáveis reutilizáveis.' },
  history: { title: 'Histórico', description: 'Consulte os resultados registrados pelo Android.' },
  laboratory: { title: 'Laboratório SMS', description: 'Teste números próprios com rastreabilidade.' },
  settings: { title: 'Configurações', description: 'Permissões, chip, telefonia e diagnósticos.' },
};

export function AppShell({ route, children, onNavigate }: { route: AppRoute; children: ReactNode; onNavigate: (route: AppRoute) => void }) {
  const [moreOpen, setMoreOpen] = useState(false);
  useEffect(() => { const closeDrawer = () => setMoreOpen(false); window.addEventListener('popstate', closeDrawer); return () => window.removeEventListener('popstate', closeDrawer); }, []);
  return <div className="app-shell"><aside className="desktop-rail"><div className="brand"><span className="brand-mark">SM</span><span><strong>SMS Massa</strong><small>offline-first</small></span></div><BottomNavigation route={route} onNavigate={onNavigate} onMore={() => setMoreOpen(true)} /></aside><main className="page-content"><PageHeader title={labels[route].title} description={labels[route].description} /><div className="page-body">{children}</div></main><div className="mobile-navigation"><BottomNavigation route={route} onNavigate={onNavigate} onMore={() => setMoreOpen(true)} /></div><MoreMenu open={moreOpen} route={route} onNavigate={onNavigate} onClose={() => setMoreOpen(false)} /></div>;
}

