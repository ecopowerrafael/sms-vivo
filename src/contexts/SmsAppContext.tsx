import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Sms } from '../services/sms';
import type { CampaignRecord, ContactListRecord, ContactRecord, DeviceContactRecord, DiagnosticRecord, MessageTemplateRecord, QueueStats } from '../services/sms';
import { classifyPhone, correctLegacyPhone, csvCell, findDuplicates, isSmsEligibleContact, parseContactsFile, smsEligibilityReason, summarizeImportPhones } from '../services/contacts';
import type { ImportProgress, ImportPhoneCounts, SmsEligibilityReason } from '../services/contacts';
import type { SmsDiagnostic, SmsResult } from '../types/sms';
import { filterHistory, type HistoryFilters } from '../utils/history';
import { normalizePhone, renderTemplate, smsSegments } from '../utils/sms';

export type LabRow = { requestId: string; destination: string; state: SmsResult['state'] | 'PENDING'; interRequestMs: number; startedAt?: number; responseMs?: number; code?: number; message?: string };
export type AppNotice = { text: string; tone?: 'info' | 'success' | 'danger' };
export type CampaignDraft = { step: number; name: string; listId: number | ''; recipientIds: number[]; body: string; intervalSeconds: string; batchSize: string; templateId: number | '' };
export type CampaignProgress = { stage: 'validating' | 'creating' | 'queueing' | 'complete'; completed: number; total: number };
export type CampaignQueueResult = { campaignId: number; queued: number; eligible: number; ignored: Partial<Record<SmsEligibilityReason, number>> };

const emptyCampaignDraft: CampaignDraft = { step: 1, name: '', listId: '', recipientIds: [], body: '', intervalSeconds: '0', batchSize: '0', templateId: '' };

type AppContextValue = {
  diagnostic: SmsDiagnostic | null;
  contacts: ContactRecord[];
  lists: ContactListRecord[];
  templates: MessageTemplateRecord[];
  campaigns: CampaignRecord[];
  results: SmsResult[];
  diagnosticEvents: DiagnosticRecord[];
  queueStats: QueueStats;
  selectedSubscriptionId: number | '';
  setSelectedSubscriptionId: (id: number | '') => void;
  refresh: () => Promise<void>;
  notice: AppNotice | null;
  setNotice: (notice: AppNotice | null) => void;
  sendSms: (options: { phone: string; body: string }) => Promise<void>;
  requestSendPermission: () => Promise<void>;
  requestPhoneStatePermission: () => Promise<void>;
  requestNotificationPermission: () => Promise<void>;
  requestContactsPermission: () => Promise<boolean>;
  getDeviceContacts: () => Promise<DeviceContactRecord[]>;
  importDeviceContacts: (contacts: DeviceContactRecord[]) => Promise<number[]>;
  importContacts: (file: File, options: { listName: string; authorize: boolean; correctLegacy: boolean }, onProgress?: (progress: ImportProgress) => void) => Promise<{ saved: number; skipped: number; duplicates: number; errors: { row: number; reason: string }[]; legacyIds: number[]; importedIds: number[]; excludedByReason: ImportPhoneCounts }>;
  authorizeContacts: (ids: number[], authorized: boolean, source?: string) => Promise<number>;
  saveContact: (contact: { id?: number; name: string; phone: string; authorized: boolean; unsubscribed?: boolean; company?: string; city?: string; state?: string }) => Promise<void>;
  deleteContacts: (ids: number[]) => Promise<{ deleted: number; blocked: number }>;
  removeContactsFromList: (listId: number, ids: number[]) => Promise<number>;
  correctLegacyContacts: (ids: number[]) => Promise<{ corrected: number; conflicts: number; failed: number }>;
  toggleUnsubscribe: (ids: number[]) => Promise<void>;
  createList: (options: { name: string; description?: string; contactIds: number[] }) => Promise<void>;
  updateList: (options: { id: number; name: string; description?: string; contactIds: number[] }) => Promise<void>;
  deleteList: (id: number) => Promise<void>;
  saveTemplate: (options: { id?: number; name: string; body: string }) => Promise<void>;
  deleteTemplate: (id: number) => Promise<void>;
  campaignDraft: CampaignDraft;
  updateCampaignDraft: (patch: Partial<CampaignDraft>) => void;
  prepareCampaign: (contactIds: number[]) => void;
  resetCampaignDraft: () => void;
  createCampaignAndQueue: (options: { name: string; body: string; subscriptionId: number; listId?: number; intervalSeconds: number; batchSize: number; contactIds?: number[] }, onProgress?: (progress: CampaignProgress) => void) => Promise<CampaignQueueResult>;
  startQueue: (options: { intervalSeconds: number; batchSize: number }) => Promise<void>;
  pauseQueue: () => Promise<void>;
  stopQueue: () => Promise<void>;
  resumeQueue: () => Promise<void>;
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
  const [queueStats, setQueueStats] = useState<QueueStats>({ pending: 0, processing: 0, sent: 0, delivered: 0, failed: 0, unknown: 0, cancelled: 0, skipped: 0, processed: 0, total: 0, elapsedMs: 0, ratePerMinute: 0, contacts: 0, ineligiblePending: 0 });
  const [selectedSubscriptionId, setSelectedSubscriptionIdState] = useState<number | ''>('');
  const [notice, setNotice] = useState<AppNotice | null>(null);
  const [campaignDraft, setCampaignDraft] = useState<CampaignDraft>(emptyCampaignDraft);
  const [labRows, setLabRows] = useState<LabRow[]>([]);
  const [labRunning, setLabRunning] = useState(false);
  const [labNotice, setLabNotice] = useState<AppNotice | null>(null);
  const labStop = useRef(false);
  const labWaiters = useRef(new Map<string, (result?: SmsResult) => void>());
  const labRequests = useRef(new Set<string>());

  const refresh = useCallback(async () => {
    const [diagnosticResponse, contactsResponse, statsResponse, historyResponse, diagnosticsResponse, listsResponse, templatesResponse, campaignsResponse] = await Promise.allSettled([
      Sms.diagnose(), Sms.listContacts(), Sms.queueStats(), Sms.listHistory(), Sms.listDiagnostics(), Sms.listContactLists(), Sms.listTemplates(), Sms.listCampaigns(),
    ]);
    const nextDiagnostic = diagnosticResponse.status === 'fulfilled' ? diagnosticResponse.value : null;
    if (!nextDiagnostic && diagnosticResponse.status === 'rejected') setNotice({ text: `Diagnóstico indisponível: ${diagnosticResponse.reason instanceof Error ? diagnosticResponse.reason.message : String(diagnosticResponse.reason)}`, tone: 'danger' });
    setDiagnostic(nextDiagnostic);
    if (contactsResponse.status === 'fulfilled') setContacts(contactsResponse.value.contacts);
    if (statsResponse.status === 'fulfilled') setQueueStats(statsResponse.value);
    if (historyResponse.status === 'fulfilled') setResults(historyResponse.value.results);
    if (diagnosticsResponse.status === 'fulfilled') setDiagnosticEvents(diagnosticsResponse.value.events);
    if (listsResponse.status === 'fulfilled') setLists(listsResponse.value.lists);
    if (templatesResponse.status === 'fulfilled') setTemplates(templatesResponse.value.templates);
    if (campaignsResponse.status === 'fulfilled') setCampaigns(campaignsResponse.value.campaigns);
    setSelectedSubscriptionIdState(current => current === '' && nextDiagnostic?.subscriptions[0] ? nextDiagnostic.subscriptions[0].id : current);
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
  const requestContactsPermission = useCallback(async () => { const response = await Sms.requestContactsPermission(); setNotice({ text: response.granted ? 'Permissão para ler a agenda concedida.' : 'Permissão READ_CONTACTS não concedida. Tente novamente ou habilite-a nas configurações do Android.', tone: response.granted ? 'success' : 'danger' }); return response.granted; }, []);
  const getDeviceContacts = useCallback(async () => (await Sms.getDeviceContacts()).contacts, []);
  const importDeviceContacts = useCallback(async (deviceContacts: DeviceContactRecord[]) => { const result = await Sms.importDeviceContacts({ contacts: deviceContacts.map(contact => ({ name: contact.name, phone: contact.phone, normalizedPhone: normalizePhone(contact.phone), phoneClassification: classifyPhone(contact.phone) })) }); await refresh(); setNotice({ text: `${result.imported} contato(s) importado(s) da agenda; ${result.existing} já existia(m); ${result.skipped} ignorado(s).`, tone: 'success' }); return result.ids; }, [refresh]);

  const importContacts = useCallback(async (file: File, options: { listName: string; authorize: boolean; correctLegacy: boolean }, onProgress?: (progress: ImportProgress) => void) => {
    let parsed;
    try { parsed = await parseContactsFile(file, onProgress); }
    catch (error) { throw new Error(`Não foi possível ler “${file.name}”. Use um CSV ou XLSX compatível. ${error instanceof Error ? error.message : String(error)}`); }
    const inputs = parsed.inputs;
    if (!options.listName.trim()) throw new Error('Informe o nome da lista antes de importar.');
    const duplicates = findDuplicates(inputs, normalizePhone).length;
    let saved = 0; let skipped = parsed.errors.length; const importedIds: number[] = []; const correctedIds: number[] = []; const incompleteIds: number[] = []; const invalidIds: number[] = []; const legacyIds: number[] = [];
    onProgress?.({ stage: 'importing', processed: 0, total: inputs.length });
    for (const [index, contact] of inputs.entries()) {
      if (!contact.phone) { skipped += 1; continue; }
      try { const originalClassification = classifyPhone(contact.phone); const corrected = options.correctLegacy && originalClassification === 'MOBILE_LEGACY_CANDIDATE'; const phone = corrected ? correctLegacyPhone(contact.phone) : contact.phone; const classification = corrected ? 'MOBILE_VALID' : originalClassification; const importCandidates = contact.phoneCandidates && contact.phoneCandidates.length > 1 ? JSON.stringify({ importPhoneCandidates: contact.phoneCandidates }) : undefined; const response = await Sms.upsertContact({ name: contact.name, phone, normalizedPhone: normalizePhone(phone), originalPhone: contact.originalPhone || contact.phone, phoneClassification: classification, correctedAt: corrected ? Date.now() : undefined, customFieldsJson: importCandidates, company: contact.company, city: contact.city, state: contact.state }); importedIds.push(response.id); saved += 1; if (originalClassification === 'MOBILE_LEGACY_CANDIDATE') { legacyIds.push(response.id); if (corrected) correctedIds.push(response.id); else incompleteIds.push(response.id); } if (classification === 'LANDLINE' || classification === 'INVALID' || classification === 'AMBIGUOUS') invalidIds.push(response.id); } catch { skipped += 1; }
      onProgress?.({ stage: 'importing', processed: index + 1, total: inputs.length });
    }
    if (importedIds.length) await Sms.createImportLists({ name: options.listName.trim(), contactIds: importedIds, correctedIds, incompleteIds, invalidIds });
    if (options.authorize && importedIds.length) await Sms.authorizeContacts({ ids: importedIds, authorized: true, source: `import:${options.listName.trim()}` });
    await refresh();
    onProgress?.({ stage: 'complete', processed: inputs.length, total: inputs.length });
    return { saved, skipped, duplicates, errors: parsed.errors, legacyIds, importedIds, excludedByReason: summarizeImportPhones(inputs) };
  }, [refresh]);

  const authorizeContacts = useCallback(async (ids: number[], authorized: boolean, source = 'bulk-ui') => { if (!ids.length) return 0; const result = await Sms.authorizeContacts({ ids, authorized, source }); await refresh(); return result.updated; }, [refresh]);

  const saveContact = useCallback(async (contact: { id?: number; name: string; phone: string; authorized: boolean; unsubscribed?: boolean; company?: string; city?: string; state?: string }) => {
    const normalizedPhone = normalizePhone(contact.phone);
    await Sms.upsertContact({ ...contact, phone: contact.phone.trim(), name: contact.name.trim(), normalizedPhone, unsubscribed: contact.unsubscribed ?? false });
    await refresh();
  }, [refresh]);

  const deleteContacts = useCallback(async (ids: number[]) => { if (ids.length === 0) return { deleted: 0, blocked: 0 }; const result = await Sms.deleteContacts({ ids }); await refresh(); return result; }, [refresh]);
  const removeContactsFromList = useCallback(async (listId: number, ids: number[]) => { if (!ids.length) return 0; const result = await Sms.removeContactsFromList({ listId, ids }); await refresh(); return result.removed; }, [refresh]);
  const correctLegacyContacts = useCallback(async (ids: number[]) => { const candidates = contacts.filter(contact => ids.includes(contact.id) && classifyPhone(contact.phone) === 'MOBILE_LEGACY_CANDIDATE'); const corrections = candidates.map(contact => { const phone = correctLegacyPhone(contact.phone); return { id: contact.id, phone, normalizedPhone: normalizePhone(phone) }; }); const result = await Sms.correctLegacyContacts({ corrections }); await refresh(); setNotice({ text: `${result.corrected} corrigido(s); ${result.conflicts} conflito(s); ${result.failed} falha(s).`, tone: result.conflicts || result.failed ? 'danger' : 'success' }); return result; }, [contacts, refresh]);
  const toggleUnsubscribe = useCallback(async (ids: number[]) => { const selected = contacts.filter(contact => ids.includes(contact.id)); const unsubscribed = selected.some(contact => !contact.unsubscribed); for (const contact of selected) await Sms.upsertContact({ ...contact, normalizedPhone: contact.normalizedPhone, unsubscribed }); await refresh(); }, [contacts, refresh]);
  const createList = useCallback(async (options: { name: string; description?: string; contactIds: number[] }) => { await Sms.createContactList(options); await refresh(); }, [refresh]);
  const updateList = useCallback(async (options: { id: number; name: string; description?: string; contactIds: number[] }) => { await Sms.updateContactList(options); await refresh(); }, [refresh]);
  const deleteList = useCallback(async (id: number) => { await Sms.deleteContactList({ id }); await refresh(); }, [refresh]);
  const saveTemplate = useCallback(async (options: { id?: number; name: string; body: string }) => { await Sms.saveTemplate(options); await refresh(); }, [refresh]);
  const deleteTemplate = useCallback(async (id: number) => { await Sms.deleteTemplate({ id }); await refresh(); }, [refresh]);
  const updateCampaignDraft = useCallback((patch: Partial<CampaignDraft>) => setCampaignDraft(current => ({ ...current, ...patch })), []);
  const prepareCampaign = useCallback((ids: number[]) => setCampaignDraft(current => ({ ...current, recipientIds: ids, step: 2 })), []);
  const resetCampaignDraft = useCallback(() => setCampaignDraft(emptyCampaignDraft), []);

  const createCampaignAndQueue = useCallback(async (options: { name: string; body: string; subscriptionId: number; listId?: number; intervalSeconds: number; batchSize: number; contactIds?: number[] }, onProgress?: (progress: CampaignProgress) => void): Promise<CampaignQueueResult> => {
    const source = options.contactIds ?? (options.listId ? lists.find(list => list.id === options.listId)?.contactIds ?? [] : campaignDraft.recipientIds);
    onProgress?.({ stage: 'validating', completed: 0, total: source.length });
    const selected = source.map(id => contacts.find(contact => contact.id === id)).filter((contact): contact is ContactRecord => Boolean(contact));
    const ignored: Partial<Record<SmsEligibilityReason, number>> = {};
    selected.forEach(contact => { const reason = smsEligibilityReason(contact); if (reason) ignored[reason] = (ignored[reason] ?? 0) + 1; });
    const eligible = selected.filter(isSmsEligibleContact);
    if (eligible.length === 0) throw new Error('Nenhum contato com celular válido, autorizado e não descadastrado foi selecionado. Revise os números antes de criar a campanha.');
    if (!options.name.trim()) throw new Error('Informe o nome da campanha.');
    if (!options.body.trim()) throw new Error('Informe a mensagem da campanha.');
    if (!Number.isFinite(options.intervalSeconds) || options.intervalSeconds < 0) throw new Error('Informe um intervalo válido, igual ou maior que zero.');
    const intervalMs = Math.round(options.intervalSeconds * 1000);
    if (!Number.isSafeInteger(intervalMs)) throw new Error('O intervalo informado é muito grande.');
    onProgress?.({ stage: 'creating', completed: 0, total: eligible.length });
    const campaign = await Sms.createCampaign({ name: options.name.trim(), subscriptionId: options.subscriptionId, intervalMs, batchSize: Math.max(0, Math.floor(options.batchSize)), listId: options.listId });
    let queued = 0;
    try {
      for (const contact of eligible) {
        onProgress?.({ stage: 'queueing', completed: queued, total: eligible.length });
        const result = await Sms.enqueue({ requestId: crypto.randomUUID(), destination: contact.normalizedPhone, body: renderTemplate(options.body, { nome: contact.name, telefone: contact.phone, empresa: contact.company, cidade: contact.city, estado: contact.state }), subscriptionId: options.subscriptionId, contactId: contact.id, campaignId: campaign.id });
        if (!result.queued) throw new Error(`O Android não confirmou a gravação do contato ${contact.name}.`);
        queued += 1;
      }
    } catch (error) {
      throw new Error(`Campanha ${campaign.id} criada parcialmente: ${queued} de ${eligible.length} itens gravados. ${error instanceof Error ? error.message : String(error)} Não repita automaticamente; revise a fila.`);
    }
    await refresh();
    onProgress?.({ stage: 'complete', completed: queued, total: eligible.length });
    return { campaignId: campaign.id, queued, eligible: eligible.length, ignored };
  }, [campaignDraft.recipientIds, contacts, lists, refresh]);

  const startQueue = useCallback(async ({ intervalSeconds, batchSize }: { intervalSeconds: number; batchSize: number }) => { await Sms.startQueue({ intervalMs: Math.max(0, intervalSeconds) * 1000, batchSize: Math.max(0, Math.floor(batchSize)) }); await refresh(); }, [refresh]);
  const pauseQueue = useCallback(async () => { await Sms.pauseQueue(); await refresh(); }, [refresh]);
  const stopQueue = useCallback(async () => { await Sms.stopQueue(); await refresh(); }, [refresh]);
  const resumeQueue = useCallback(async () => { await Sms.resumeQueue(); await refresh(); setNotice({ text: 'Fila retomada após revisão manual.', tone: 'info' }); }, [refresh]);

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
      ['requestId', 'state', 'code', 'createdAt', 'smsRequestedAt', 'sentAt', 'deliveredAt', 'unknownAt', 'requestIntervalMs', 'lastCallbackAt', 'lastCallbackCode', 'partsExpected', 'sentParts', 'deliveredParts', 'destination', 'campaignId', 'listId', 'subscriptionId', 'message'],
      ...filtered.map(result => [result.requestId, result.state, result.code ?? '', result.createdAt ?? '', result.smsRequestedAt ?? '', result.sentAt, result.deliveredAt ?? '', result.unknownAt ?? '', result.requestIntervalMs ?? '', result.lastCallbackAt ?? '', result.lastCallbackCode ?? '', result.partsExpected ?? '', result.sentParts ?? '', result.deliveredParts ?? '', result.destination ?? '', result.campaignId ?? '', result.listId ?? '', result.subscriptionId ?? '', result.message ?? '']),
    ]);
  }, [results]);
  const exportLabCsv = useCallback(() => {
    downloadCsv('sms-vivo-laboratorio.csv', [
      ['requestId', 'destination', 'state', 'interRequestMs', 'responseMs', 'code', 'message'],
      ...labRows.map(row => [row.requestId, row.destination, row.state, row.interRequestMs, row.responseMs ?? '', row.code ?? '', row.message ?? '']),
    ]);
  }, [labRows]);

  const value = useMemo<AppContextValue>(() => ({ diagnostic, contacts, lists, templates, campaigns, results, diagnosticEvents, queueStats, selectedSubscriptionId, setSelectedSubscriptionId, refresh, notice, setNotice, sendSms, requestSendPermission, requestPhoneStatePermission, requestNotificationPermission, requestContactsPermission, getDeviceContacts, importDeviceContacts, importContacts, authorizeContacts, saveContact, deleteContacts, removeContactsFromList, correctLegacyContacts, toggleUnsubscribe, createList, updateList, deleteList, saveTemplate, deleteTemplate, campaignDraft, updateCampaignDraft, prepareCampaign, resetCampaignDraft, createCampaignAndQueue, startQueue, pauseQueue, stopQueue, resumeQueue, labRows, labRunning, labNotice, runLaboratory, stopLaboratory, exportContactsCsv, exportHistoryCsv, exportLabCsv }), [authorizeContacts, campaignDraft, campaigns, contacts, correctLegacyContacts, createCampaignAndQueue, createList, deleteContacts, deleteList, deleteTemplate, diagnostic, diagnosticEvents, exportContactsCsv, exportHistoryCsv, exportLabCsv, getDeviceContacts, importContacts, importDeviceContacts, labNotice, labRows, lists, notice, pauseQueue, prepareCampaign, refresh, removeContactsFromList, requestContactsPermission, requestNotificationPermission, requestPhoneStatePermission, requestSendPermission, resetCampaignDraft, results, resumeQueue, runLaboratory, saveContact, saveTemplate, selectedSubscriptionId, sendSms, startQueue, stopLaboratory, stopQueue, templates, toggleUnsubscribe, updateCampaignDraft, updateList]);
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

