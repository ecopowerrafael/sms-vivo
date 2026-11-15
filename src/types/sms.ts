export type SmsState = 'SENT' | 'DELIVERED' | 'FAILED' | 'UNKNOWN';
export interface SmsResult { requestId: string; state: SmsState; code?: number; message?: string; sentAt: string; deliveredAt?: string; destination?: string; campaignId?: number; listId?: number; subscriptionId?: number; }
export interface SmsDiagnostic { canSendSms: boolean; sendSmsGranted: boolean; phoneStateGranted: boolean; notificationGranted: boolean; subscriptions: Array<{ id: number; displayName: string; carrierName?: string; slotIndex?: number }>; }
