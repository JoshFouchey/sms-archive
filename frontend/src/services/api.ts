// frontend/src/services/api.ts
const API_BASE = "http://localhost:8080";

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
    sender: string;
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
    messageId?: number;      // (Server may not expose; keep optional.)
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
    const res = await fetch(`${API_BASE}/api/messages/contacts`);
    if (!res.ok) throw new Error("Failed to fetch contacts");
    return res.json();
}

/* (Optional older alias) */
export const getContacts = getAllContactSummaries;

export async function getDistinctContacts(): Promise<Contact[]> {
    const res = await fetch(`${API_BASE}/api/contacts`);
    if (!res.ok) throw new Error("Failed to fetch distinct contacts");
    return res.json();
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
    const q = p.toString();
    const res = await fetch(`${API_BASE}/api/analytics/dashboard${q ? `?${q}` : ""}`);
    if (!res.ok) throw new Error("Failed to fetch analytics dashboard");
    return res.json();
}

/* ==============================
   Messages
============================== */

/**
 * Fetch paginated messages for a contact by ID
 * Backend endpoint: GET /api/messages/contact/{contactId}?page=&size=&sort=(asc|desc)
 */
export async function getMessagesByContactId(
    contactId: number,
    page: number = 0,
    size: number = 50,
    sort: "asc" | "desc" = "desc"
): Promise<PagedResponse<Message>> {
    const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
        sort
    });
    const url = `${API_BASE}/api/messages/contact/${contactId}?${params.toString()}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error("Failed to load messages");
    return res.json();
}

/* ==============================
   Media
============================== */

export async function getImages(
    page: number = 0,
    size: number = 50,
    contactId?: number
): Promise<MessagePart[]> {
    const params = new URLSearchParams();
    params.append("page", page.toString());
    params.append("size", size.toString());
    if (contactId != null) params.append("contactId", contactId.toString());
    const url = `${API_BASE}/api/media/images?${params.toString()}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error("Failed to load images");
    return res.json();
}

export async function deleteImageById(id: number): Promise<boolean> {
    const res = await fetch(`${API_BASE}/api/media/images/${id}`, { method: "DELETE" });
    return res.ok;
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
  const fd = new FormData();
  fd.append("file", file);
  const res = await fetch(`${API_BASE}/import/stream`, { method: "POST", body: fd });
  if (!res.ok) throw new Error("Failed to start streaming import");
  return res.json();
}

export async function getImportProgress(jobId: string): Promise<ImportProgress | null> {
  const res = await fetch(`${API_BASE}/import/progress/${jobId}`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error("Failed to fetch import progress");
  return res.json();
}

/* ==============================
   Search
============================== */

export async function searchBySender(sender: string): Promise<Message[]> {
    const res = await fetch(`${API_BASE}/search/sender?sender=${encodeURIComponent(sender)}`);
    return res.json();
}

export async function searchByRecipient(recipient: string): Promise<Message[]> {
    const res = await fetch(`${API_BASE}/search/recipient?recipient=${encodeURIComponent(recipient)}`);
    return res.json();
}

export async function searchByText(text: string): Promise<Message[]> {
    const res = await fetch(`${API_BASE}/search/text?text=${encodeURIComponent(text)}`);
    return res.json();
}

export async function searchByDateRange(start: string, end: string): Promise<Message[]> {
    const res = await fetch(`${API_BASE}/search/dates?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`);
    return res.json();
}