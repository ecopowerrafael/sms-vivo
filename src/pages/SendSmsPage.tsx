import { useState } from 'react';
import { useSmsApp, useMessageMetrics } from '../contexts/SmsAppContext';
import { Card, Notice } from '../components/ui/Primitives';
import { MessageEditor, SubscriptionSelect } from '../components/ui/Forms';

export function SendSmsPage() {
  const { diagnostic, notice, sendSms } = useSmsApp();
  const [phone, setPhone] = useState('');
  const [body, setBody] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const metrics = useMessageMetrics(body);
  async function submit() {
    try { setBusy(true); setError(''); await sendSms({ phone, body }); }
    catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  }
  return <div className="page-stack narrow-page"><Notice {...notice} />{error && <Notice tone="danger">{error}</Notice>}<Card><div className="form-intro"><span className="eyebrow">ENVIO UNITÁRIO</span><h2>Teste controlado</h2><p className="muted">Use um número próprio ou um destinatário autorizado.</p></div><SubscriptionSelect /><label>Número do destinatário<input value={phone} onChange={event => setPhone(event.target.value)} placeholder="(11) 99999-0000" inputMode="tel" /></label><MessageEditor value={body} onChange={setBody} /><div className="message-meta"><span>{metrics.units} caracteres · {metrics.encoding}</span><span>{metrics.segments} segmento(s)</span></div><div className="preview"><span>Prévia</span><p>{body || 'A mensagem aparecerá aqui.'}</p></div><button onClick={() => void submit()} disabled={busy || !diagnostic?.sendSmsGranted || !phone.trim() || !body.trim()}>{busy ? 'Enviando…' : 'Enviar SMS real'}</button></Card></div>;
}
