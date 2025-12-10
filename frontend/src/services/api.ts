import axios from 'axios';
// Use VITE_API_BASE if set, otherwise empty string for relative URLs
// e.g., if app is at http://localhost:8071, API calls go to http://localhost:8071/api
const API_BASE = (import.meta.env.VITE_API_BASE ?? '').trim();

export { API_BASE };

/* ==============================
   Types / Interfaces
============================== */

export interface PagedResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
}

export interface Message {
    id: number;
    protocol: string;
    direction: string;
    senderContactId?: number | null;
    senderContactName?: string | null;
    senderContactNumber?: string | null;
    sender?: string; // legacy field for backward compat
    recipient?: string;
    contactName?: string;
    contactNumber?: string;
    contactNormalizedNumber?: string;
    timestamp: string;
    body?: string;
    msgBox?: number;
    deliveredAt?: string;
    readAt?: string;
    media?: any;
    metadata?: any;
    createdAt?: string;
    updatedAt?: string;
    parts?: MessagePart[];
}

export interface MessagePart {
    id: number;
    messageId?: number;
    sender?: string;
    recipient?: string;
    timestamp?: number | string;
    filePath: string;
    contentType: string;
}

export interface ContactSummary {
    contactId: number;
    contactName: string;
    lastMessageTimestamp: string;
    lastMessagePreview: string;
    hasImage: boolean;
}

// New conversation-based types
export interface ConversationSummary {
    id: number;
    name: string;
    participantNames: string[];
    participantCount: number;
    lastMessageAt: string;
    lastMessagePreview: string;
    lastMessageHasImage: boolean;
    unreadCount: number;
}

export interface GalleryImage {
    id: number;
    messageId: number;
    filePath: string;
    contentType: string;
    timestamp: string;
    contactId?: number;
    contactName?: string;
    contactNumber?: string;
}

// Analytics DTOs
export interface AnalyticsSummary {
    totalContacts: number;
    totalMessages: number;
    totalImages: number;
}
export interface TopContactDto {
    contactId: number;
    displayName: string;
    messageCount: number;
}
export interface MessageCountPerDayDto {
    day: string; // ISO date (yyyy-MM-dd)
    count: number;
}
export interface AnalyticsDashboardDto {
    summary: AnalyticsSummary;
    topContacts: TopContactDto[];
    messagesPerDay: MessageCountPerDayDto[];
}

export interface Contact {
    id: number;
    name: string | null;
    number: string;
    normalizedNumber: string;
}

/* ==============================
   Contacts
============================== */

export async function getAllContactSummaries(): Promise<ContactSummary[]> {
    const res = await axios.get(`${API_BASE}/api/messages/contacts`);
    return res.data;
}

/* (Optional older alias) */
export const getContacts = getAllContactSummaries;

export async function getDistinctContacts(): Promise<Contact[]> {
    const res = await axios.get(`${API_BASE}/api/contacts`);
    return res.data;
}

/* ==============================
   Analytics
============================== */
export async function getAnalyticsDashboard(params?: {
    topContactDays?: number;
    topLimit?: number;
    perDayDays?: number;
    startDate?: string; // yyyy-MM-dd
    endDate?: string;   // yyyy-MM-dd
    contactId?: number | null;
}): Promise<AnalyticsDashboardDto> {
    const p = new URLSearchParams();
    if (params?.topContactDays) p.append("topContactDays", String(params.topContactDays));
    if (params?.topLimit) p.append("topLimit", String(params.topLimit));
    if (params?.perDayDays) p.append("perDayDays", String(params.perDayDays));
    if (params?.startDate) p.append("startDate", params.startDate);
    if (params?.endDate) p.append("endDate", params.endDate);
    if (params?.contactId != null) p.append("contactId", String(params.contactId));
    const res = await axios.get(`${API_BASE}/api/analytics/dashboard${p.toString()?`?${p.toString()}`:''}`);
    return res.data;
}

/* ==============================
   Conversations
============================== */

/**
 * Get all conversations (replaces contact summaries for new UI)
 * Backend endpoint: GET /api/conversations
 */
export async function getAllConversations(): Promise<ConversationSummary[]> {
    const res = await axios.get(`${API_BASE}/api/conversations`);
    return res.data;
}

/**
 * Fetch paginated messages for a conversation by ID
 * Backend endpoint: GET /api/conversations/{conversationId}/messages?page=&size=&sort=(asc|desc)
 */
export async function getConversationMessages(
    conversationId: number,
    page: number = 0,
    size: number = 50,
    sort: "asc" | "desc" = "desc"
): Promise<PagedResponse<Message>> {
    const params = { page, size, sort };
    const res = await axios.get(`${API_BASE}/api/conversations/${conversationId}/messages`, { params });
    return res.data;
}

/**
 * Delete a conversation by ID. Returns 'deleted' or 'not_found'.
 */
export async function deleteConversation(conversationId: number): Promise<'deleted' | 'not_found'> {
    try {
        const res = await axios.delete(`${API_BASE}/api/conversations/${conversationId}`);
        return res.status === 204 ? 'deleted' : 'not_found';
    } catch (e: any) {
        if (e.response && e.response.status === 404) return 'not_found';
        throw e;
    }
}

/* ==============================
   Messages (Legacy - deprecated)
============================== */

/**
 * Fetch paginated messages for a contact by ID (DEPRECATED - use getConversationMessages)
 * Backend endpoint: GET /api/messages/contact/{contactId}?page=&size=&sort=(asc|desc)
 */
export async function getMessagesByContactId(
    contactId: number,
    page: number = 0,
    size: number = 50,
    sort: "asc" | "desc" = "desc"
): Promise<PagedResponse<Message>> {
    const params = { page, size, sort };
    const res = await axios.get(`${API_BASE}/api/messages/contact/${contactId}`, { params });
    return res.data;
}

/* ==============================
   Media
============================== */

export async function getImages(
    page: number = 0,
    size: number = 50,
    contactId?: number
): Promise<PagedResponse<GalleryImage>> {
    const params: any = { page, size };
    if (contactId != null) params.contactId = contactId;
    const res = await axios.get(`${API_BASE}/api/media/images`, { params });
    return res.data;
}

export async function deleteImageById(id: number): Promise<boolean> {
    const res = await axios.delete(`${API_BASE}/api/media/images/${id}`);
    return res.status === 200;
}

/* ==============================
   Import
============================== */

export interface ImportProgress {
  id: string;
  totalBytes: number;
  bytesRead: number;
  processedMessages: number;
  importedMessages: number;
  duplicateMessages: number; // atomic in-flight count
  duplicateMessagesFinal?: number; // final count when completed
  status: string; // PENDING/RUNNING/COMPLETED/FAILED
  error?: string;
  startedAt?: string;
  finishedAt?: string;
  percentBytes: number; // derived on server
}

export async function startStreamingImport(file: File): Promise<{ jobId: string; status: string; }> {
    const fd = new FormData(); fd.append("file", file);
    const res = await axios.post(`${API_BASE}/import/stream`, fd);
    return res.data;
}

export async function getImportProgress(jobId: string): Promise<ImportProgress | null> {
    try { const res = await axios.get(`${API_BASE}/import/progress/${jobId}`); return res.data; } catch (e: any) { if (e.response?.status === 404) return null; throw e; }
}

/* ==============================
   Search
============================== */

export async function searchBySender(sender: string): Promise<Message[]> { const res = await axios.get(`${API_BASE}/search/sender`, { params: { sender }}); return res.data; }
export async function searchByRecipient(recipient: string): Promise<Message[]> { const res = await axios.get(`${API_BASE}/search/recipient`, { params: { recipient }}); return res.data; }
export async function searchByText(text: string): Promise<Message[]> { const res = await axios.get(`${API_BASE}/search/text`, { params: { text }}); return res.data; }
export async function searchByDateRange(start: string, end: string): Promise<Message[]> { const res = await axios.get(`${API_BASE}/search/dates`, { params: { start, end }}); return res.data; }

/* ==============================
   Contacts
============================== */

export async function updateContactName(id: number, name: string | null): Promise<Contact> {
    const res = await axios.put(`${API_BASE}/api/contacts/${id}`, { name });
    return res.data;
}

/* ==============================
   Duplicate Cleanup
============================== */

export interface DuplicatePreview {
    duplicateGroups: number;
    totalDuplicates: number;
    totalMessages: number;
    sampleGroups?: Array<{
        count: number;
        ids: number[];
        body: string;
        timestamp: string;
    }>;
}

export async function previewDuplicates(): Promise<DuplicatePreview> {
    const res = await axios.get(`${API_BASE}/api/admin/cleanup/duplicates/preview`);
    return res.data;
}

export async function removeDuplicates(): Promise<{
    totalGroups: number;
    totalDeleted: number;
    totalKept: number;
}> {
    const res = await axios.delete(`${API_BASE}/api/admin/cleanup/duplicates`);
    return res.data;
}

/* ==============================
   Message Context
============================== */

export interface MessageContext {
  conversationId: number;
  center: Message;
  before: Message[];
  after: Message[];
}

export async function getMessageContext(
  messageId: number,
  before: number = 25,
  after: number = 25
): Promise<MessageContext> {
  const params = { before, after };
  const res = await axios.get(`${API_BASE}/api/messages/${messageId}/context`, { params });
  return res.data;
}

/* ==============================
   Conversation Timeline & Historical Navigation
============================== */

export interface ConversationTimeline {
  conversationId: number;
  years: YearBucket[];
}

export interface YearBucket {
  year: number;
  count: number;
  months: MonthBucket[];
}

export interface MonthBucket {
  year: number;
  month: number; // 1-12
  count: number;
  firstMessageId: number | null;
  lastMessageId: number | null;
}

/**
 * Get conversation timeline index with year/month buckets and message counts
 * Backend endpoint: GET /api/conversations/{conversationId}/timeline
 */
export async function getConversationTimeline(conversationId: number): Promise<ConversationTimeline> {
  const res = await axios.get(`${API_BASE}/api/conversations/${conversationId}/timeline`);
  return res.data;
}

/**
 * Fetch messages by date range within a conversation
 * Backend endpoint: GET /api/conversations/{conversationId}/messages?dateFrom=&dateTo=&page=&size=&sort=
 */
export async function getConversationMessagesByDateRange(
  conversationId: number,
  dateFrom: string | null,
  dateTo: string | null,
  page: number = 0,
  size: number = 50,
  sort: "asc" | "desc" = "asc"
): Promise<PagedResponse<Message>> {
  const params: any = { page, size, sort };
  if (dateFrom) params.dateFrom = dateFrom;
  if (dateTo) params.dateTo = dateTo;
  const res = await axios.get(`${API_BASE}/api/conversations/${conversationId}/messages`, { params });
  return res.data;
}
