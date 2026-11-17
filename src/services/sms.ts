import { registerPlugin } from '@capacitor/core';
import type { SmsDiagnostic, SmsResult } from '../types/sms';
export interface SmsNativePlugin {
  diagnose(): Promise<SmsDiagnostic>;
  requestSendPermission(): Promise<{ granted: boolean }>;
  requestPhoneStatePermission(): Promise<{ granted: boolean }>;
  requestNotificationPermission(): Promise<{ granted: boolean }>;
  send(options: { subscriptionId: number; destination: string; body: string; requestId: string }): Promise<{ accepted: boolean; requestId: string }>;
  addListener(eventName: 'smsResult', listenerFunc: (result: SmsResult) => void): Promise<{ remove: () => Promise<void> }>;
  listContacts(): Promise<{ contacts: ContactRecord[] }>;
  upsertContact(contact: Partial<Pick<ContactRecord, 'id'>> & Omit<ContactRecord, 'id' | 'normalizedPhone'> & { normalizedPhone: string }): Promise<{ saved: boolean; id: number }>;
  deleteContacts(options: { ids: number[] }): Promise<{ deleted: number }>;
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

export interface ContactRecord { id: number; name: string; phone: string; normalizedPhone: string; company?: string; city?: string; state?: string; customFieldsJson?: string; authorized: boolean; unsubscribed: boolean; }
export interface ContactListRecord { id: number; name: string; description?: string; contactIds: number[]; }
export interface MessageTemplateRecord { id: number; name: string; body: string; }
export interface CampaignRecord { id: number; name: string; state: string; subscriptionId: number; listId?: number; createdAt: number; }
export interface DiagnosticRecord { id: number; category: string; code?: number; message: string; createdAt: number; }
export const Sms = registerPlugin<SmsNativePlugin>('Sms');
