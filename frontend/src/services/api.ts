const API_BASE = "http://localhost:8080";

export interface Sms {
    id: number;
    protocol?: number;
    address: string;
    date: string;  // ISO string from backend
    msgBox?: number;
    body?: string;
    contactName?: string;
}

export async function importXml(file: File): Promise<Response> {
    const formData = new FormData();
    formData.append("file", file);

    return fetch(`${API_BASE}/import`, {
        method: "POST",
        body: formData,
    });
}

export async function searchSms(query: string): Promise<Sms[]> {
    const res = await fetch(`${API_BASE}/sms/search?query=${encodeURIComponent(query)}`);
    if (!res.ok) throw new Error("Search failed");
    return res.json();
}
