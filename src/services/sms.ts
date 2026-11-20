import { registerPlugin } from '@capacitor/core';
import type { SmsDiagnostic, SmsResult } from '../types/sms';
export interface SmsNativePlugin {
  getBuildInfo(): Promise<BuildInfo>;
  probeIntervalBridge(options: { operationId: string; intervalMs: number | string }): Promise<IntervalBridgeProbe>;
  recordBridgeDiagnostic(options: { operationId: string; method: string; namePresent?: boolean; intervalSeconds?: number; intervalMs?: number | string; intervalMsType?: string; subscriptionId?: number }): Promise<{ recorded: boolean }>;
  diagnose(): Promise<SmsDiagnostic>;
  requestSendPermission(): Promise<{ granted: boolean }>;
  requestPhoneStatePermission(): Promise<{ granted: boolean }>;
  requestNotificationPermission(): Promise<{ granted: boolean }>;
  requestContactsPermission(): Promise<{ granted: boolean }>;
  getDeviceContacts(): Promise<{ contacts: DeviceContactRecord[] }>;
  importDeviceContacts(options: { contacts: Array<{ name: string; phone: string; normalizedPhone: string; phoneClassification?: string }> }): Promise<{ imported: number; existing: number; skipped: number; duplicates?: number; rejected?: number; ids: number[] }>;
  send(options: { subscriptionId: number; destination: string; body: string; requestId: string }): Promise<{ accepted: boolean; requestId: string }>;
  addListener(eventName: 'smsResult', listenerFunc: (result: SmsResult) => void): Promise<{ remove: () => Promise<void> }>;
  listContacts(): Promise<{ contacts: ContactRecord[] }>;
  listContactsPaged(options?: { query?: string; limit?: number; offset?: number }): Promise<{ contacts: ContactRecord[]; total: number; limit: number; offset: number }>;
  listContactsByList(options: { listId: number; query?: string; limit?: number; offset?: number }): Promise<{ contacts: ContactRecord[]; total: number; eligible: number; limit: number; offset: number }>;
  upsertContact(contact: ContactUpsert): Promise<{ saved: boolean; id: number }>;
  authorizeContacts(options: { ids: number[]; authorized: boolean; source?: string }): Promise<{ updated: number }>;
  createImportLists(options: { name: string; contactIds: number[]; correctedIds?: number[]; incompleteIds?: number[]; invalidIds?: number[] }): Promise<{ id: number; sublistIds: number[] }>;
  deleteContacts(options: { ids: number[] }): Promise<{ deleted: number; blocked: number }>;
  removeContactsFromList(options: { listId: number; ids: number[] }): Promise<{ removed: number }>;
  correctLegacyContacts(options: { corrections: Array<{ id: number; phone: string; normalizedPhone: string }> }): Promise<{ corrected: number; conflicts: number; failed: number; selected: number }>;
  queueStats(): Promise<QueueStats>;
  listHistory(): Promise<{ results: SmsResult[] }>;
  listDiagnostics(): Promise<{ events: DiagnosticRecord[] }>;
  enqueue(options: { requestId: string; destination: string; body: string; subscriptionId: number; contactId?: number; campaignId?: number }): Promise<{ queued: boolean; queueItemId: number; requestId: string }>;
  enqueueCampaign(options: { campaignId: number; subscriptionId: number; body: string; listId?: number; query?: string; selectAll?: boolean; contactIds?: number[]; excludedIds?: number[]; batchSize?: number }): Promise<{ queued: number; eligible: number; ignored: number }>;
  startQueue(options?: { operationId?: string; intervalMs?: number; batchSize?: number }): Promise<{ started: boolean; intervalMs?: number; batchSize?: number }>;
  pauseQueue(): Promise<{ paused: boolean }>;
  stopQueue(): Promise<{ stopped: boolean }>;
  resumeQueue(): Promise<{ resumed: boolean }>;
  listContactLists(): Promise<{ lists: ContactListRecord[] }>;
  createContactList(options: { name: string; description?: string; origin?: string; contactIds?: number[] }): Promise<{ id: number }>;
  ensureDeviceContactsList(options: { contactIds: number[]; listId?: number; createNew?: boolean; name?: string }): Promise<{ id: number; created: boolean; name: string }>;
  updateContactList(options: { id: number; name: string; description?: string; contactIds?: number[] }): Promise<{ updated: boolean }>;
  deleteContactList(options: { id: number }): Promise<{ deleted: boolean }>;
  listTemplates(): Promise<{ templates: MessageTemplateRecord[] }>;
  saveTemplate(options: { id?: number; name: string; body: string }): Promise<{ saved: boolean }>;
  deleteTemplate(options: { id: number }): Promise<{ deleted: boolean }>;
  listCampaigns(): Promise<{ campaigns: CampaignRecord[] }>;
  createCampaign(options: { operationId?: string; name: string; subscriptionId: number; intervalMs?: number; batchSize?: number; listId?: number; templateId?: number }): Promise<{ id: number; intervalMs?: number }>;
}

export interface BuildInfo { applicationId: string; versionName: string; versionCode: number; commit: string; buildDate: string; buildType: string; }
export interface IntervalBridgeProbe { operationId: string; fieldPresent: boolean; rawType: string; rawValue?: string; converted?: number; }

export type ContactUpsert = Partial<Pick<ContactRecord, 'id' | 'authorized' | 'unsubscribed' | 'originalPhone' | 'phoneClassification' | 'correctedAt'>> & Omit<ContactRecord, 'id' | 'normalizedPhone' | 'authorized' | 'unsubscribed'> & { normalizedPhone: string };

export interface ContactRecord { id: number; name: string; phone: string; normalizedPhone: string; originalPhone?: string; phoneClassification?: import('./contacts').PhoneClassification; correctedAt?: number; company?: string; city?: string; state?: string; customFieldsJson?: string; authorized: boolean; unsubscribed: boolean; }
export interface QueueStats { pending: number; processing: number; sent: number; delivered: number; failed: number; unknown: number; cancelled: number; skipped: number; processed: number; total: number; elapsedMs: number; ratePerMinute: number; contacts: number; ineligiblePending?: number; pauseReason?: string; currentRequestId?: string; currentDestination?: string; currentCampaignId?: number; currentSmsRequestedAt?: number; currentRequestIntervalMs?: number; }
export interface DeviceContactRecord { sourceId: string; contactId: number; name: string; phone: string; type?: number; }
export interface ContactListRecord { id: number; name: string; description?: string; parentListId?: number; origin?: string; contactIds: number[]; totalCount?: number; eligibleCount?: number; }
export interface MessageTemplateRecord { id: number; name: string; body: string; }
export interface CampaignRecord { id: number; name: string; state: string; subscriptionId: number; listId?: number; createdAt: number; }
export interface DiagnosticRecord { id: number; category: string; code?: number; message: string; createdAt: number; }
export const Sms = registerPlugin<SmsNativePlugin>('Sms');
