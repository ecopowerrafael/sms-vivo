import { useMemo, useState } from 'react';
import { useSmsApp, useMessageMetrics } from '../contexts/SmsAppContext';
import { Card, EmptyState, Notice, SectionHeading } from '../components/ui/Primitives';
import { MessageEditor } from '../components/ui/Forms';

export function TemplatesPage() {
  const { templates, saveTemplate, deleteTemplate, notice, setNotice } = useSmsApp();
  const [editingId, setEditingId] = useState<number>(); const [name, setName] = useState(''); const [body, setBody] = useState(''); const [search, setSearch] = useState('');
  const filtered = useMemo(() => templates.filter(template => !search || `${template.name} ${template.body}`.toLowerCase().includes(search.toLowerCase())), [search, templates]); const metrics = useMessageMetrics(body);
  function edit(template: typeof templates[number]) { setEditingId(template.id); setName(template.name); setBody(template.body); }
  function reset() { setEditingId(undefined); setName(''); setBody(''); }
  async function submit() { try { await saveTemplate({ id: editingId, name, body }); setNotice({ text: editingId ? 'Modelo atualizado.' : 'Modelo salvo.', tone: 'success' }); reset(); } catch (error) { setNotice({ text: error instanceof Error ? error.message : String(error), tone: 'danger' }); } }
  return <div className="page-stack"><Notice {...notice} /><div className="two-column"><Card><SectionHeading title={editingId ? 'Editar modelo' : 'Novo modelo'} /><label>Nome<input value={name} onChange={event => setName(event.target.value)} placeholder="Ex.: Primeiro contato" /></label><MessageEditor value={body} onChange={setBody} /><div className="message-meta"><span>{metrics.units} caracteres · {metrics.encoding}</span><span>{metrics.segments} segmento(s)</span></div><div className="preview"><span>Prévia</span><p>{body || 'A mensagem aparecerá aqui.'}</p></div><div className="inline-actions"><button onClick={() => void submit()} disabled={!name.trim() || !body.trim()}>{editingId ? 'Salvar alterações' : 'Criar modelo'}</button>{editingId && <button className="secondary" onClick={reset}>Cancelar</button>}</div></Card><Card><SectionHeading title={`${filtered.length} modelo(s)`} /><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar modelos" />{filtered.length === 0 ? <EmptyState title="Nenhum modelo encontrado" /> : <div className="template-list">{filtered.map(template => <div className="template-card" key={template.id}><strong>{template.name}</strong><p>{template.body}</p><div className="inline-actions"><button className="secondary" onClick={() => edit(template)}>Editar</button><button className="danger" onClick={() => { if (window.confirm(`Excluir o modelo “${template.name}”?`)) void deleteTemplate(template.id); }}>Excluir</button></div></div>)}</div>}</Card></div></div>;
}

