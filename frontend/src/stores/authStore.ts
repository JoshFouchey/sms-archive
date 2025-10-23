import { defineStore } from 'pinia';
import axios from 'axios';

const API_BASE = (import.meta.env.VITE_API_BASE as string) || '';

interface User { id?: string; username: string; createdAt?: string; updatedAt?: string }

type AuthStatus = 'idle' | 'authenticating' | 'authenticated' | 'error';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as User | null,
    accessToken: localStorage.getItem('accessToken') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    status: 'idle' as AuthStatus,
  }),
  actions: {
    setTokens(access: string, refresh: string) {
      this.accessToken = access; this.refreshToken = refresh;
      localStorage.setItem('accessToken', access);
      localStorage.setItem('refreshToken', refresh);
    },
    clear() { this.user = null; this.accessToken=''; this.refreshToken=''; localStorage.removeItem('accessToken'); localStorage.removeItem('refreshToken'); this.status='idle'; },
    async register(username: string, password: string) {
      this.status='authenticating';
      try {
        const res = await axios.post(`${API_BASE}/api/auth/register`, {username, password});
        this.setTokens(res.data.accessToken, res.data.refreshToken); await this.fetchMe(); this.status='authenticated';
      } catch (e) { this.status='error'; throw e; }
    },
    async login(username: string, password: string) {
      this.status='authenticating';
      try {
        const res = await axios.post(`${API_BASE}/api/auth/login`, {username, password});
        this.setTokens(res.data.accessToken, res.data.refreshToken); await this.fetchMe(); this.status='authenticated';
      } catch (e) { this.status='error'; throw e; }
    },
    async refresh() {
      if (!this.refreshToken) return; try { const res = await axios.post(`${API_BASE}/api/auth/refresh`, {refreshToken: this.refreshToken}); this.setTokens(res.data.accessToken, res.data.refreshToken); } catch { this.clear(); }
    },
    async fetchMe() {
      if (!this.accessToken) return; try { const res = await axios.get(`${API_BASE}/api/auth/me`, { headers: { Authorization: `Bearer ${this.accessToken}` }}); this.user = res.data; } catch { /* ignore */ }
    },
    logout() { this.clear(); }
  }
});

axios.interceptors.request.use(config => {
  const store = useAuthStore();
  if (store.accessToken) config.headers['Authorization'] = `Bearer ${store.accessToken}`;
  return config;
});

axios.interceptors.response.use(r => r, async err => {
  const store = useAuthStore();
  if (err.response && err.response.status === 401) {
    try { await store.refresh(); if (store.accessToken) { err.config.headers['Authorization'] = `Bearer ${store.accessToken}`; return axios.request(err.config); } } catch { store.logout(); window.location.href='/login'; }
  }
  return Promise.reject(err);
});
