import { useMemo } from 'react';
import type { AppRoute } from '../app/routes';
import { useSmsApp } from '../contexts/SmsAppContext';
import { Card, EmptyState, Notice, SectionHeading, StatCard, StatusBadge } from '../components/ui/Primitives';

export function DashboardPage({ onNavigate }: { onNavigate: (route: AppRoute) => void }) {
  const { diagnostic, contacts, campaigns, results, queueStats, notice, selectedSubscriptionId } = useSmsApp();
  const authorized = useMemo(() => contacts.filter(contact => contact.authorized && !contact.unsubscribed).length, [contacts]);
  const latest = results.slice(0, 5);
  const runningCampaign = campaigns.find(campaign => campaign.state === 'PROCESSING' || campaign.state === 'RUNNING');
  return <div className="page-stack">
    <Notice {...notice} />
    <Card className="hero-card"><div><span className="eyebrow">CONTROLE OPERACIONAL</span><h2>Envie pelo seu chip, com rastreabilidade.</h2><p className="muted">Dados locais, fila persistente e resultados reais do Android.</p></div><div className={diagnostic?.canSendSms && diagnostic.sendSmsGranted ? 'hero-status positive' : 'hero-status'}><span className="status-dot" />{diagnostic?.canSendSms && diagnostic.sendSmsGranted ? 'Pronto para enviar' : 'Revisar permissões'}</div></Card>
    <div className="stats-grid"><StatCard label="Contatos" value={queueStats.contacts ?? contacts.length} /><StatCard label="Autorizados" value={authorized} tone="success" /><StatCard label="Pendentes" value={queueStats.pending ?? 0} tone="warning" /><StatCard label="Entregues" value={queueStats.delivered ?? 0} tone="success" /><StatCard label="Falhas" value={queueStats.failed ?? 0} tone="danger" /><StatCard label="Processando" value={queueStats.processing ?? 0} /></div>
    <div className="dashboard-grid"><Card><SectionHeading title="Status da telefonia" /><div className="status-list"><StatusLine label="SMS" value={diagnostic?.sendSmsGranted ? 'Concedido' : 'Não concedido'} ok={Boolean(diagnostic?.sendSmsGranted)} /><StatusLine label="Telefonia" value={diagnostic?.canSendSms ? 'Disponível' : 'Indisponível'} ok={Boolean(diagnostic?.canSendSms)} /><StatusLine label="Chip ativo" value={diagnostic?.subscriptions.find(subscription => subscription.id === selectedSubscriptionId)?.displayName ?? 'Não selecionado'} ok={Boolean(diagnostic?.subscriptions.length)} /></div><button className="secondary" onClick={() => onNavigate('settings')}>Abrir configurações</button></Card><Card><SectionHeading title="Acesso rápido" /><div className="quick-actions"><button onClick={() => onNavigate('campaigns')}>Nova campanha</button><button className="secondary" onClick={() => onNavigate('send')}>Enviar teste</button><button className="secondary" onClick={() => onNavigate('history')}>Ver histórico</button></div>{runningCampaign && <p className="notice notice-info">Campanha em andamento: {runningCampaign.name}</p>}</Card></div>
    <Card><SectionHeading title="Últimos resultados" action={<button className="text-button" onClick={() => onNavigate('history')}>Ver todos</button>} />{latest.length === 0 ? <EmptyState title="Nenhum envio registrado" description="Os resultados do Android aparecerão aqui." /> : <div className="result-list">{latest.map(result => <div className="result-row" key={result.requestId}><StatusBadge state={result.state} /><span>{result.destination ?? 'Destino não informado'}</span><small>{new Date(result.sentAt).toLocaleString('pt-BR')}</small></div>)}</div>}</Card>
  </div>;
}

function StatusLine({ label, value, ok }: { label: string; value: string; ok: boolean }) { return <div className="status-line"><span><i className={ok ? 'status-dot ok' : 'status-dot'} />{label}</span><strong>{value}</strong></div>; }

