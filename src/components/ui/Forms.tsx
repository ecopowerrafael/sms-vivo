import type { ChangeEvent } from 'react';
import { useSmsApp } from '../../contexts/SmsAppContext';

export function SubscriptionSelect({ value, onChange }: { value?: number | ''; onChange?: (value: number | '') => void }) {
  const { diagnostic, selectedSubscriptionId, setSelectedSubscriptionId } = useSmsApp();
  const selected = value === undefined ? selectedSubscriptionId : value;
  const change = (event: ChangeEvent<HTMLSelectElement>) => { const next = event.target.value ? Number(event.target.value) : ''; (onChange ?? setSelectedSubscriptionId)(next); };
  return <label>Chip selecionado<select value={selected} onChange={change}><option value="">Selecione o chip</option>{diagnostic?.subscriptions.map(subscription => <option key={subscription.id} value={subscription.id}>{subscription.displayName}{subscription.carrierName ? ` · ${subscription.carrierName}` : ''}</option>)}</select></label>;
}

export function MessageEditor({ value, onChange, placeholder = 'Digite a mensagem. Variáveis: {nome}, {telefone}, {empresa}, {cidade}, {estado}.' }: { value: string; onChange: (value: string) => void; placeholder?: string }) {
  return <label>Mensagem<textarea value={value} onChange={event => onChange(event.target.value)} placeholder={placeholder} rows={6} /></label>;
}

