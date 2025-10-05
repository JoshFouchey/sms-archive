// src/services/api.ts
const API_BASE = "http://localhost:8080";

export interface Message {
    id: number;
    protocol: string;
    sender: string;
    recipient?: string;
    contactName?: string;   // add this
    timestamp: string;
    body?: string;
    media?: any;
    metadata?: any;
    parts?: any[];          // add this
}

export interface MessagePart {
    id: number;
    messageId: number;
    sender: string;
    recipient: string;
    timestamp: number;
    filePath: string;
    contentType: string;
}

export async function getImages(
    contact?: string,
    page: number = 0,
    size: number = 50
): Promise<MessagePart[]> {
    const params = new URLSearchParams();

    if (contact && contact.trim().length > 0) {
        params.append("contact", contact);
    }

    params.append("page", page.toString());
    params.append("size", size.toString());

    const url = `${API_BASE}/api/media/images?${params.toString()}`;
    const res = await fetch(url);

    if (!res.ok) throw new Error("Failed to load images");

    return res.json();
}


export async function deleteImageById(id: number): Promise<boolean> {
    const res = await fetch(`${API_BASE}/api/media/images/${id}`, { method: "DELETE" });
    return res.ok;
}


export async function importXml(file: File): Promise<Response> {
    const formData = new FormData();
    formData.append("file", file);

    return fetch(`${API_BASE}/import`, {
        method: "POST",
        body: formData,
    });
}

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
