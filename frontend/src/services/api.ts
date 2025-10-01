// src/services/api.ts
const API_BASE = "http://localhost:8080";

export interface Message {
    id: number;
    protocol: string;
    sender: string;
    recipient?: string;
    timestamp: string;  // ISO date
    body?: string;
    media?: any;
    metadata?: any;
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
