import { registerPlugin } from '@capacitor/core';
import type { SmsDiagnostic, SmsResult } from '../types/sms';
export interface SmsNativePlugin {
  diagnose(): Promise<SmsDiagnostic>;
  requestSendPermission(): Promise<{ granted: boolean }>;
  requestPhoneStatePermission(): Promise<{ granted: boolean }>;
  requestNotificationPermission(): Promise<{ granted: boolean }>;
  requestContactsPermission(): Promise<{ granted: boolean }>;
  getDeviceContacts(): Promise<{ contacts: DeviceContactRecord[] }>;
  importDeviceContacts(options: { contacts: Array<{ name: string; phone: string; normalizedPhone: string; phoneClassification?: string }> }): Promise<{ imported: number; existing: number; skipped: number; ids: number[] }>;
  send(options: { subscriptionId: number; destination: string; body: string; requestId: string }): Promise<{ accepted: boolean; requestId: string }>;
  addListener(eventName: 'smsResult', listenerFunc: (result: SmsResult) => void): Promise<{ remove: () => Promise<void> }>;
  listContacts(): Promise<{ contacts: ContactRecord[] }>;
  upsertContact(contact: ContactUpsert): Promise<{ saved: boolean; id: number }>;
  authorizeContacts(options: { ids: number[]; authorized: boolean; source?: string }): Promise<{ updated: number }>;
  createImportLists(options: { name: string; contactIds: number[]; correctedIds?: number[]; incompleteIds?: number[]; invalidIds?: number[] }): Promise<{ id: number; sublistIds: number[] }>;
  deleteContacts(options: { ids: number[] }): Promise<{ deleted: number; blocked: number }>;
  removeContactsFromList(options: { listId: number; ids: number[] }): Promise<{ removed: number }>;
  correctLegacyContacts(options: { corrections: Array<{ id: number; phone: string; normalizedPhone: string }> }): Promise<{ corrected: number; conflicts: number; failed: number; selected: number }>;
  queueStats(): Promise<Record<string, number>>;
  listHistory(): Promise<{ results: SmsResult[] }>;
  listDiagnostics(): Promise<{ events: DiagnosticRecord[] }>;
  enqueue(options: { requestId: string; destination: string; body: string; subscriptionId: number; contactId?: number; campaignId?: number }): Promise<{ queued: boolean; queueItemId: number; requestId: string }>;
  startQueue(options?: { intervalMs?: number; batchSize?: number }): Promise<{ started: boolean; intervalMs?: number; batchSize?: number }>;
  pauseQueue(): Promise<{ paused: boolean }>;
  stopQueue(): Promise<{ stopped: boolean }>;
  listContactLists(): Promise<{ lists: ContactListRecord[] }>;
  createContactList(options: { name: string; description?: string; contactIds?: number[] }): Promise<{ id: number }>;
  updateContactList(options: { id: number; name: string; description?: string; contactIds?: number[] }): Promise<{ updated: boolean }>;
  deleteContactList(options: { id: number }): Promise<{ deleted: boolean }>;
  listTemplates(): Promise<{ templates: MessageTemplateRecord[] }>;
  saveTemplate(options: { id?: number; name: string; body: string }): Promise<{ saved: boolean }>;
  deleteTemplate(options: { id: number }): Promise<{ deleted: boolean }>;
  listCampaigns(): Promise<{ campaigns: CampaignRecord[] }>;
  createCampaign(options: { name: string; subscriptionId: number; intervalMs?: number; batchSize?: number; listId?: number; templateId?: number }): Promise<{ id: number }>;
}

export type ContactUpsert = Partial<Pick<ContactRecord, 'id' | 'authorized' | 'unsubscribed' | 'originalPhone' | 'phoneClassification' | 'correctedAt'>> & Omit<ContactRecord, 'id' | 'normalizedPhone' | 'authorized' | 'unsubscribed'> & { normalizedPhone: string };

export interface ContactRecord { id: number; name: string; phone: string; normalizedPhone: string; originalPhone?: string; phoneClassification?: import('./contacts').PhoneClassification; correctedAt?: number; company?: string; city?: string; state?: string; customFieldsJson?: string; authorized: boolean; unsubscribed: boolean; }
export interface DeviceContactRecord { sourceId: string; contactId: number; name: string; phone: string; type?: number; }
export interface ContactListRecord { id: number; name: string; description?: string; parentListId?: number; contactIds: number[]; }
export interface MessageTemplateRecord { id: number; name: string; body: string; }
export interface CampaignRecord { id: number; name: string; state: string; subscriptionId: number; listId?: number; createdAt: number; }
export interface DiagnosticRecord { id: number; category: string; code?: number; message: string; createdAt: number; }
export const Sms = registerPlugin<SmsNativePlugin>('Sms');
