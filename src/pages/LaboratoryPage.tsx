import { useState } from 'react';
import { useSmsApp } from '../contexts/SmsAppContext';
import { Card, Notice, SectionHeading, StatusBadge } from '../components/ui/Primitives';
import { MessageEditor, SubscriptionSelect } from '../components/ui/Forms';

export function LaboratoryPage() {
  const { diagnostic, labRows, labRunning, labNotice, runLaboratory, stopLaboratory, exportLabCsv } = useSmsApp();
  const [numbers, setNumbers] = useState(''); const [message, setMessage] = useState(''); const [interval, setInterval] = useState('1');
  async function run() { try { await runLaboratory({ numbers, message, intervalSeconds: Number(interval) || 0 }); } catch (error) { /* context owns persistent lab state; this branch is only a safety net */ } }
  return <div className="page-stack narrow-page"><Notice {...labNotice} /><Card><SectionHeading title="Teste controlado" description="Use apenas números próprios ou destinatários autorizados." action={<button className="secondary export" disabled={!labRows.length} onClick={exportLabCsv}>Exportar CSV</button>} /><SubscriptionSelect /><label>Números de teste<textarea value={numbers} onChange={event => setNumbers(event.target.value)} placeholder="Um por linha, vírgula ou ponto e vírgula" rows={4} /></label><MessageEditor value={message} onChange={setMessage} placeholder="Mensagem de laboratório" /><label>Intervalo entre testes (segundos)<input type="number" min="0" value={interval} onChange={event => setInterval(event.target.value)} /></label><div className="queue-actions"><button onClick={() => void run()} disabled={labRunning || !diagnostic?.sendSmsGranted}>{labRunning ? 'Executando…' : 'Iniciar teste'}</button><button className="danger" onClick={stopLaboratory} disabled={!labRunning}>Interromper</button></div></Card>{labRows.length > 0 && <Card><SectionHeading title="Resultados da execução" />{labRows.map(row => <div className="result-row" key={row.requestId}><StatusBadge state={row.state} /><span>{row.destination}</span><small>{row.code ? `Código ${row.code}` : row.message ?? (row.responseMs ? `${Math.round(row.responseMs)}ms` : 'aguardando')}</small></div>)}</Card>}</div>;
}

