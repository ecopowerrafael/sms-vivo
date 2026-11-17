import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Sms } from '../services/sms';
import type { CampaignRecord, ContactListRecord, ContactRecord, DiagnosticRecord, MessageTemplateRecord } from '../services/sms';
import { csvCell, findDuplicates, parseCsv, parseXlsx } from '../services/contacts';
import type { SmsDiagnostic, SmsResult } from '../types/sms';
import { filterHistory, type HistoryFilters } from '../utils/history';
import { normalizePhone, renderTemplate, smsSegments } from '../utils/sms';

export type LabRow = { requestId: string; destination: string; state: SmsResult['state'] | 'PENDING'; interRequestMs: number; startedAt?: number; responseMs?: number; code?: number; message?: string };
export type AppNotice = { text: string; tone?: 'info' | 'success' | 'danger' };

type AppContextValue = {
  diagnostic: SmsDiagnostic | null;
  contacts: ContactRecord[];
  lists: ContactListRecord[];
  templates: MessageTemplateRecord[];
  campaigns: CampaignRecord[];
  results: SmsResult[];
  diagnosticEvents: DiagnosticRecord[];
  queueStats: Record<string, number>;
  selectedSubscriptionId: number | '';
  setSelectedSubscriptionId: (id: number | '') => void;
  refresh: () => Promise<void>;
  notice: AppNotice | null;
  setNotice: (notice: AppNotice | null) => void;
  sendSms: (options: { phone: string; body: string }) => Promise<void>;
  requestSendPermission: () => Promise<void>;
  requestPhoneStatePermission: () => Promise<void>;
  requestNotificationPermission: () => Promise<void>;
  importContacts: (file: File) => Promise<{ saved: number; skipped: number; duplicates: number }>;
  saveContact: (contact: { id?: number; name: string; phone: string; authorized: boolean; unsubscribed?: boolean; company?: string; city?: string; state?: string }) => Promise<void>;
  deleteContacts: (ids: number[]) => Promise<void>;
  toggleUnsubscribe: (ids: number[]) => Promise<void>;
  createList: (options: { name: string; description?: string; contactIds: number[] }) => Promise<void>;
  updateList: (options: { id: number; name: string; description?: string; contactIds: number[] }) => Promise<void>;
  deleteList: (id: number) => Promise<void>;
  saveTemplate: (options: { id?: number; name: string; body: string }) => Promise<void>;
  deleteTemplate: (id: number) => Promise<void>;
  prepareCampaign: (contactIds: number[]) => void;
  campaignRecipientIds: number[];
  createCampaignAndQueue: (options: { name: string; body: string; subscriptionId: number; listId?: number; intervalSeconds: number; batchSize: number; contactIds?: number[] }) => Promise<number>;
  startQueue: (options: { intervalSeconds: number; batchSize: number }) => Promise<void>;
  pauseQueue: () => Promise<void>;
  stopQueue: () => Promise<void>;
  labRows: LabRow[];
  labRunning: boolean;
  labNotice: AppNotice | null;
  runLaboratory: (options: { numbers: string; message: string; intervalSeconds: number }) => Promise<void>;
  stopLaboratory: () => void;
  exportContactsCsv: () => void;
  exportHistoryCsv: (filters: HistoryFilters) => void;
  exportLabCsv: () => void;
};

const SmsAppContext = createContext<AppContextValue | undefined>(undefined);

export function SmsAppProvider({ children }: { children: ReactNode }) {
  const [diagnostic, setDiagnostic] = useState<SmsDiagnostic | null>(null);
  const [contacts, setContacts] = useState<ContactRecord[]>([]);
  const [lists, setLists] = useState<ContactListRecord[]>([]);
  const [templates, setTemplates] = useState<MessageTemplateRecord[]>([]);
  const [campaigns, setCampaigns] = useState<CampaignRecord[]>([]);
  const [results, setResults] = useState<SmsResult[]>([]);
  const [diagnosticEvents, setDiagnosticEvents] = useState<DiagnosticRecord[]>([]);
  const [queueStats, setQueueStats] = useState<Record<string, number>>({});
  const [selectedSubscriptionId, setSelectedSubscriptionIdState] = useState<number | ''>('');
  const [notice, setNotice] = useState<AppNotice | null>(null);
  const [campaignRecipientIds, setCampaignRecipientIds] = useState<number[]>([]);
  const [labRows, setLabRows] = useState<LabRow[]>([]);
  const [labRunning, setLabRunning] = useState(false);
  const [labNotice, setLabNotice] = useState<AppNotice | null>(null);
  const labStop = useRef(false);
  const labWaiters = useRef(new Map<string, (result?: SmsResult) => void>());
  const labRequests = useRef(new Set<string>());

  const refresh = useCallback(async () => {
    const [nextDiagnostic, contactsResponse, stats, history, diagnostics, listsResponse, templatesResponse, campaignsResponse] = await Promise.all([
      Sms.diagnose(), Sms.listContacts(), Sms.queueStats(), Sms.listHistory(), Sms.listDiagnostics(), Sms.listContactLists(), Sms.listTemplates(), Sms.listCampaigns(),
    ]);
    setDiagnostic(nextDiagnostic);
    setContacts(contactsResponse.contacts);
    setQueueStats(stats);
    setResults(history.results);
    setDiagnosticEvents(diagnostics.events);
    setLists(listsResponse.lists);
    setTemplates(templatesResponse.templates);
    setCampaigns(campaignsResponse.campaigns);
    setSelectedSubscriptionIdState(current => current === '' && nextDiagnostic.subscriptions[0] ? nextDiagnostic.subscriptions[0].id : current);
  }, []);

  useEffect(() => { refresh().catch(error => setNotice({ text: error instanceof Error ? error.message : String(error), tone: 'danger' })); }, [refresh]);

  useEffect(() => {
    let active = true;
    const listener = Sms.addListener('smsResult', result => {
      if (!active) return;
      if (labRequests.current.has(result.requestId)) {
        setLabRows(rows => rows.map(row => row.requestId === result.requestId ? { ...row, state: result.state, responseMs: row.startedAt ? performance.now() - row.startedAt : undefined, code: result.code, message: result.message } : row));
        labWaiters.current.get(result.requestId)?.(result);
        labWaiters.current.delete(result.requestId);
        if (result.state === 'FAILED' || result.state === 'UNKNOWN') labStop.current = true;
      }
      refresh().catch(() => undefined);
    });
    return () => { active = false; listener.then(handle => handle.remove()).catch(() => undefined); };
  }, [refresh]);

  const setSelectedSubscriptionId = useCallback((id: number | '') => setSelectedSubscriptionIdState(id), []);

  const sendSms = useCallback(async ({ phone, body }: { phone: string; body: string }) => {
    if (selectedSubscriptionId === '') throw new Error('Selecione um chip.');
    const requestId = crypto.randomUUID();
    await Sms.send({ subscriptionId: selectedSubscriptionId, destination: normalizePhone(phone), body, requestId });
    setNotice({ text: 'Solicitação aceita pelo Android; aguardando resultado.', tone: 'success' });
    await refresh();
  }, [refresh, selectedSubscriptionId]);

  const requestSendPermission = useCallback(async () => { const response = await Sms.requestSendPermission(); setNotice({ text: response.granted ? 'Permissão SEND_SMS concedida.' : 'Permissão SEND_SMS não concedida.', tone: response.granted ? 'success' : 'danger' }); await refresh(); }, [refresh]);
  const requestPhoneStatePermission = useCallback(async () => { const response = await Sms.requestPhoneStatePermission(); setNotice({ text: response.granted ? 'Permissão de estado telefônico concedida.' : 'READ_PHONE_STATE não concedida.', tone: response.granted ? 'success' : 'danger' }); await refresh(); }, [refresh]);
  const requestNotificationPermission = useCallback(async () => { const response = await Sms.requestNotificationPermission(); setNotice({ text: response.granted ? 'Permissão de notificações concedida.' : 'Permissão de notificações não concedida.', tone: response.granted ? 'success' : 'danger' }); await refresh(); }, [refresh]);

  const importContacts = useCallback(async (file: File) => {
    let inputs;
    try { inputs = file.name.toLowerCase().endsWith('.xlsx') ? await parseXlsx(await file.arrayBuffer()) : parseCsv(await file.text()); }
    catch (error) { throw new Error(`Não foi possível ler “${file.name}”. Use um CSV ou XLSX compatível. ${error instanceof Error ? error.message : String(error)}`); }
    const duplicates = findDuplicates(inputs, normalizePhone).length;
    let saved = 0; let skipped = 0;
    for (const contact of inputs) {
      if (!contact.phone) { skipped += 1; continue; }
      try { await Sms.upsertContact({ ...contact, normalizedPhone: normalizePhone(contact.phone) }); saved += 1; } catch { skipped += 1; }
    }
    await refresh();
    return { saved, skipped, duplicates };
  }, [refresh]);

  const saveContact = useCallback(async (contact: { id?: number; name: string; phone: string; authorized: boolean; unsubscribed?: boolean; company?: string; city?: string; state?: string }) => {
    const normalizedPhone = normalizePhone(contact.phone);
    await Sms.upsertContact({ ...contact, phone: contact.phone.trim(), name: contact.name.trim(), normalizedPhone, unsubscribed: contact.unsubscribed ?? false });
    await refresh();
  }, [refresh]);

  const deleteContacts = useCallback(async (ids: number[]) => { if (ids.length === 0) return; await Sms.deleteContacts({ ids }); await refresh(); }, [refresh]);
  const toggleUnsubscribe = useCallback(async (ids: number[]) => { const selected = contacts.filter(contact => ids.includes(contact.id)); const unsubscribed = selected.some(contact => !contact.unsubscribed); for (const contact of selected) await saveContact({ ...contact, unsubscribed }); await refresh(); }, [contacts, refresh, saveContact]);
  const createList = useCallback(async (options: { name: string; description?: string; contactIds: number[] }) => { await Sms.createContactList(options); await refresh(); }, [refresh]);
  const updateList = useCallback(async (options: { id: number; name: string; description?: string; contactIds: number[] }) => { await Sms.updateContactList(options); await refresh(); }, [refresh]);
  const deleteList = useCallback(async (id: number) => { await Sms.deleteContactList({ id }); await refresh(); }, [refresh]);
  const saveTemplate = useCallback(async (options: { id?: number; name: string; body: string }) => { await Sms.saveTemplate(options); await refresh(); }, [refresh]);
  const deleteTemplate = useCallback(async (id: number) => { await Sms.deleteTemplate({ id }); await refresh(); }, [refresh]);
  const prepareCampaign = useCallback((ids: number[]) => setCampaignRecipientIds(ids), []);

  const createCampaignAndQueue = useCallback(async (options: { name: string; body: string; subscriptionId: number; listId?: number; intervalSeconds: number; batchSize: number; contactIds?: number[] }) => {
    const source = options.contactIds ?? (options.listId ? lists.find(list => list.id === options.listId)?.contactIds ?? [] : campaignRecipientIds);
    const eligible = source.map(id => contacts.find(contact => contact.id === id)).filter((contact): contact is ContactRecord => Boolean(contact?.authorized && !contact.unsubscribed));
    if (eligible.length === 0) throw new Error('Nenhum contato autorizado e não descadastrado foi selecionado.');
    const campaign = await Sms.createCampaign({ name: options.name.trim() || 'Campanha sem nome', subscriptionId: options.subscriptionId, intervalMs: Math.max(0, options.intervalSeconds) * 1000, batchSize: Math.max(0, Math.floor(options.batchSize)), listId: options.listId });
    for (const contact of eligible) await Sms.enqueue({ requestId: crypto.randomUUID(), destination: contact.normalizedPhone, body: renderTemplate(options.body, { nome: contact.name, telefone: contact.phone, empresa: contact.company, cidade: contact.city, estado: contact.state }), subscriptionId: options.subscriptionId, contactId: contact.id, campaignId: campaign.id });
    await refresh();
    return eligible.length;
  }, [campaignRecipientIds, contacts, lists, refresh]);

  const startQueue = useCallback(async ({ intervalSeconds, batchSize }: { intervalSeconds: number; batchSize: number }) => { await Sms.startQueue({ intervalMs: Math.max(0, intervalSeconds) * 1000, batchSize: Math.max(0, Math.floor(batchSize)) }); await refresh(); }, [refresh]);
  const pauseQueue = useCallback(async () => { await Sms.pauseQueue(); await refresh(); }, [refresh]);
  const stopQueue = useCallback(async () => { await Sms.stopQueue(); await refresh(); }, [refresh]);

  const runLaboratory = useCallback(async ({ numbers, message, intervalSeconds }: { numbers: string; message: string; intervalSeconds: number }) => {
    if (selectedSubscriptionId === '') throw new Error('Selecione um chip.');
    const destinations = numbers.split(/[\n,;]+/).map(value => value.trim()).filter(Boolean);
    if (destinations.length === 0 || !message.trim()) throw new Error('Informe números e uma mensagem.');
    labStop.current = false; labRequests.current.clear(); setLabRows([]); setLabRunning(true); setLabNotice({ text: 'Teste em execução…' });
    let previous = performance.now();
    try {
      for (const raw of destinations) {
        if (labStop.current) break;
        const destination = normalizePhone(raw); const requestId = crypto.randomUUID(); const requestedAt = performance.now(); const interRequestMs = requestedAt - previous; previous = requestedAt;
        labRequests.current.add(requestId); setLabRows(rows => [...rows, { requestId, destination, state: 'PENDING', interRequestMs, startedAt: requestedAt }]);
        let result: SmsResult | undefined;
        try {
          const wait = new Promise<SmsResult | undefined>(resolve => { labWaiters.current.set(requestId, resolve); window.setTimeout(() => { labWaiters.current.delete(requestId); resolve(undefined); }, 30000); });
          await Sms.send({ subscriptionId: selectedSubscriptionId, destination, body: message, requestId });
          result = await wait;
        } catch (error) { labStop.current = true; setLabRows(rows => rows.map(row => row.requestId === requestId ? { ...row, state: 'FAILED', message: error instanceof Error ? error.message : String(error) } : row)); }
        if (!result) { labStop.current = true; setLabNotice({ text: 'Teste interrompido: não houve retorno em 30 segundos.', tone: 'danger' }); break; }
        if (result.state === 'FAILED' || result.state === 'UNKNOWN') { setLabNotice({ text: `Teste interrompido após ${result.state}.`, tone: 'danger' }); break; }
        if (intervalSeconds > 0) await new Promise(resolve => window.setTimeout(resolve, intervalSeconds * 1000));
      }
    } catch (error) {
      labStop.current = true;
      setLabNotice({ text: error instanceof Error ? error.message : String(error), tone: 'danger' });
      throw error;
    } finally { setLabRunning(false); if (!labStop.current) setLabNotice({ text: 'Teste concluído; resultados persistidos no histórico.', tone: 'success' }); await refresh(); }
  }, [refresh, selectedSubscriptionId]);

  const stopLaboratory = useCallback(() => { labStop.current = true; setLabRunning(false); setLabNotice({ text: 'Interrupção solicitada; nenhuma nova solicitação será iniciada.' }); }, []);
  const exportContactsCsv = useCallback(() => {
    downloadCsv('sms-vivo-contatos.csv', [
      ['id', 'nome', 'telefone', 'telefoneNormalizado', 'empresa', 'cidade', 'estado', 'autorizado', 'descadastrado'],
      ...contacts.map(contact => [contact.id, contact.name, contact.phone, contact.normalizedPhone, contact.company ?? '', contact.city ?? '', contact.state ?? '', contact.authorized, contact.unsubscribed]),
    ]);
  }, [contacts]);
  const exportHistoryCsv = useCallback((filters: HistoryFilters) => {
    const filtered = filterHistory(results, filters);
    downloadCsv('sms-vivo-historico.csv', [
      ['requestId', 'state', 'code', 'sentAt', 'destination', 'campaignId', 'listId', 'subscriptionId', 'message'],
      ...filtered.map(result => [result.requestId, result.state, result.code ?? '', result.sentAt, result.destination ?? '', result.campaignId ?? '', result.listId ?? '', result.subscriptionId ?? '', result.message ?? '']),
    ]);
  }, [results]);
  const exportLabCsv = useCallback(() => {
    downloadCsv('sms-vivo-laboratorio.csv', [
      ['requestId', 'destination', 'state', 'interRequestMs', 'responseMs', 'code', 'message'],
      ...labRows.map(row => [row.requestId, row.destination, row.state, row.interRequestMs, row.responseMs ?? '', row.code ?? '', row.message ?? '']),
    ]);
  }, [labRows]);

  const value = useMemo<AppContextValue>(() => ({ diagnostic, contacts, lists, templates, campaigns, results, diagnosticEvents, queueStats, selectedSubscriptionId, setSelectedSubscriptionId, refresh, notice, setNotice, sendSms, requestSendPermission, requestPhoneStatePermission, requestNotificationPermission, importContacts, saveContact, deleteContacts, toggleUnsubscribe, createList, updateList, deleteList, saveTemplate, deleteTemplate, prepareCampaign, campaignRecipientIds, createCampaignAndQueue, startQueue, pauseQueue, stopQueue, labRows, labRunning, labNotice, runLaboratory, stopLaboratory, exportContactsCsv, exportHistoryCsv, exportLabCsv }), [campaignRecipientIds, campaigns, contacts, createCampaignAndQueue, createList, deleteContacts, deleteList, deleteTemplate, diagnostic, diagnosticEvents, exportContactsCsv, exportHistoryCsv, exportLabCsv, importContacts, labNotice, labRows, labRunning, lists, notice, pauseQueue, prepareCampaign, refresh, requestNotificationPermission, requestPhoneStatePermission, requestSendPermission, results, runLaboratory, saveContact, saveTemplate, selectedSubscriptionId, sendSms, startQueue, stopLaboratory, stopQueue, templates, toggleUnsubscribe, updateList]);
  return <SmsAppContext.Provider value={value}>{children}</SmsAppContext.Provider>;
}

export function useSmsApp() {
  const context = useContext(SmsAppContext);
  if (!context) throw new Error('useSmsApp deve ser usado dentro de SmsAppProvider');
  return context;
}

function downloadCsv(filename: string, rows: unknown[][]) {
  const content = rows.map(row => row.map(csvCell).join(',')).join('\n');
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }));
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); URL.revokeObjectURL(url);
}

export function useMessageMetrics(body: string) { return useMemo(() => smsSegments(body), [body]); }

