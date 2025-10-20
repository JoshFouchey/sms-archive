<template>
  <div class="flex h-screen bg-gray-100 dark:bg-slate-950 text-gray-900 dark:text-gray-100">
    <!-- Contacts panel -->
    <aside class="w-1/3 max-w-sm border-r border-gray-200 dark:border-slate-700 bg-gray-50/90 dark:bg-slate-900/70 backdrop-blur p-4 overflow-y-auto">
      <h2 class="text-lg font-semibold mb-4 tracking-tight text-gray-700 dark:text-gray-200">
        Contacts
      </h2>

      <div v-if="contactsLoading" class="text-sm text-gray-500 dark:text-gray-400">Loading contacts...</div>
      <div v-else-if="contactsError" class="text-sm text-red-600 dark:text-red-400">{{ contactsError }}</div>
      <div v-else-if="!contacts.length" class="text-sm text-gray-500 dark:text-gray-400">No contacts.</div>

      <div
        v-for="contact in contacts"
        :key="contact.contactName"
        @click="selectContact(contact)"
        :class="[
          'group mb-3 p-3 rounded-lg border cursor-pointer transition-colors duration-150 flex flex-col gap-1',
          selectedContact?.contactName === contact.contactName
            ? 'accent-bg accent-border text-white shadow-sm'
            : 'bg-white/90 dark:bg-slate-800/80 border-gray-200 dark:border-slate-700 hover:accent-muted-bg dark:hover:bg-slate-700/70'
        ]"
      >
        <div class="flex justify-between items-center">
          <h3 :class="['font-medium truncate', selectedContact?.contactName === contact.contactName ? 'text-white' : 'text-gray-800 dark:text-gray-100']">
            {{ contact.contactName }}
          </h3>
          <span
            v-if="contact.hasImage"
            :class="[
              'text-sm',
              selectedContact?.contactName === contact.contactName
                ? 'accent-soft-text'
                : 'text-gray-400 dark:text-gray-500 group-hover:accent-text'
            ]"
          >📷</span>
        </div>
        <p
          class="text-xs truncate"
          :class="selectedContact?.contactName === contact.contactName ? 'accent-soft-text' : 'text-gray-600 dark:text-gray-400'"
        >{{ contact.lastMessagePreview }}</p>
        <p
          class="text-[10px] uppercase tracking-wide"
          :class="selectedContact?.contactName === contact.contactName ? 'accent-subtle-text' : 'text-gray-400 dark:text-gray-500'"
        >{{ formatDate(contact.lastMessageTimestamp) }}</p>
      </div>
    </aside>

    <!-- Conversation panel -->
    <main class="flex-1 flex flex-col p-4 bg-white/70 dark:bg-slate-900/60 backdrop-blur">
      <h2 class="text-lg font-semibold mb-2 tracking-tight text-gray-700 dark:text-gray-200">
        Conversation with
        <span class="accent-text">{{ selectedContact?.contactName || '...' }}</span>
      </h2>

      <!-- Status messages (outside scroll container) -->
      <div v-if="!selectedContact && !messagesLoading" class="mt-4 text-sm text-gray-500 dark:text-gray-500 italic">
        Select a contact to view conversation.
      </div>

      <!-- Scrollable messages container -->
      <div v-if="selectedContact" ref="messageContainer" class="flex-1 overflow-y-auto mt-2 pr-1" @scroll="handleScroll">
        <!-- Older loader / end marker at top -->
        <div class="flex justify-center my-2">
          <span v-if="olderLoading" class="text-[11px] text-gray-400 dark:text-gray-500">Loading older messages...</span>
          <span v-else-if="!hasMoreOlder && messages.length" class="text-[11px] text-gray-400 dark:text-gray-500">Beginning of history</span>
        </div>

        <!-- Main loading state (initial) -->
        <div v-if="messagesLoading" class="text-sm text-gray-500 dark:text-gray-400">Loading messages...</div>
        <div v-else-if="messagesError" class="text-sm text-red-600 dark:text-red-400">{{ messagesError }}</div>
        <div v-else-if="!messages.length" class="text-sm text-gray-500 dark:text-gray-400">No messages.</div>

        <!-- Messages list -->
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="mb-3 flex flex-col"
          :class="msg.isMe ? 'items-end' : 'items-start'"
        >
          <div
            :class="[
              'max-w-[78%] rounded-lg px-3 py-2 shadow-sm text-sm leading-snug space-y-2',
              msg.isMe ? 'accent-bg text-white' : 'bg-gray-200 text-gray-900 dark:bg-slate-700 dark:text-gray-100'
            ]"
          >
            <div v-if="msg.body && msg.body !== '[media]'">{{ msg.body }}</div>
            <!-- Image thumbnails -->
            <div v-if="msg.images && msg.images.length" class="flex flex-wrap gap-2">
              <div
                v-for="(img, i) in msg.images"
                :key="i"
                class="relative group cursor-pointer overflow-hidden rounded-md bg-black/10 dark:bg-black/30"
                :class="img.isSingle ? 'w-48 h-48' : 'w-32 h-32'"
                @click="openImage(img.fullUrl)"
              >
                <img
                  :src="img.thumbUrl"
                  :alt="img.contentType"
                  class="w-full h-full object-cover transition-transform duration-200 group-hover:scale-105"
                  loading="lazy"
                  @error="onThumbError($event, img)"
                />
                <div v-if="img.error" class="absolute inset-0 flex items-center justify-center text-[10px] bg-black/40 text-white">Image</div>
              </div>
            </div>
          </div>
          <span
            class="mt-1 text-[10px] tracking-wide uppercase"
            :class="msg.isMe ? 'accent-dark-text' : 'text-gray-400 dark:text-gray-500'"
          >{{ formatDateTime(msg.timestamp) }}</span>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import {
  getAllContactSummaries,
  getMessagesByContactId,
  type ContactSummary as ApiContactSummary,
  type Message as ApiMessage,
  type PagedResponse
} from '../services/api';

interface UiImagePart {
  fullUrl: string;
  thumbUrl: string;
  contentType: string;
  isSingle: boolean; // helpful for sizing
  error?: boolean;
}
interface UiMessage {
  id: number;
  body: string;
  timestamp: string;
  isMe: boolean;
  images?: UiImagePart[];
}

const contacts = ref<ApiContactSummary[]>([]);
const contactsLoading = ref(true);
const contactsError = ref('');

const selectedContact = ref<ApiContactSummary | null>(null);

const messages = ref<UiMessage[]>([]);
const messagesLoading = ref(false); // initial load
const messagesError = ref('');

// Pagination / lazy loading state
const nextPage = ref(0); // page index to load next (descending sort pages)
const hasMoreOlder = ref(false); // whether there are older messages available
const olderLoading = ref(false); // loading older pages

const pageSize = 50; // tweak as desired

const messageContainer = ref<HTMLDivElement | null>(null);

onMounted(async () => {
  try {
    contacts.value = await getAllContactSummaries();
  } catch (e: any) {
    contactsError.value = e?.message || 'Failed to load contacts';
  } finally {
    contactsLoading.value = false;
  }
});

async function selectContact(contact: ApiContactSummary) {
  if (selectedContact.value?.contactId === contact.contactId) return;
  selectedContact.value = contact;
  // Reset state
  messages.value = [];
  messagesError.value = '';
  messagesLoading.value = true;
  nextPage.value = 0;
  hasMoreOlder.value = false;
  olderLoading.value = false;
  await loadInitialMessages();
}

async function loadInitialMessages() {
  if (!selectedContact.value) return;
  try {
    const paged: PagedResponse<ApiMessage> = await getMessagesByContactId(selectedContact.value.contactId, 0, pageSize, 'desc');
    // We want ascending internally so newest at bottom.
    const pageMessages = paged.content
      .slice() // shallow copy
      .reverse() // convert descending page to ascending
      .map(m => toUiMessage(m));
    messages.value = pageMessages; // now oldest (of page) at top, newest at bottom
    hasMoreOlder.value = !paged.last; // if backend says not last, there ARE older messages (higher page index)
    nextPage.value = 1; // next page (older) index to fetch
    await nextTick();
    scrollToBottom();
  } catch (e: any) {
    messagesError.value = e?.message || 'Failed to load messages';
  } finally {
    messagesLoading.value = false;
  }
}

function toUiMessage(m: ApiMessage): UiMessage {
  const images: UiImagePart[] = [];
  if (Array.isArray(m.parts)) {
    const imageParts = m.parts.filter(p => p.contentType && p.contentType.startsWith('image'));
    const single = imageParts.length === 1;
    for (const p of imageParts) {
      const normalized = normalizePath(p.filePath || '');
      // Use relative path so Nginx or reverse proxy can route without hardcoded host
      const fullUrl = `/${normalized}`;
      const thumbUrl = fullUrl.replace(/(\.[A-Za-z0-9]+)$/,'_thumb.jpg');
      images.push({ fullUrl, thumbUrl, contentType: p.contentType, isSingle: single });
    }
  }
  const body = (m.body && m.body.trim().length) ? m.body : (images.length ? '' : '[media]');
  return {
    id: m.id,
    body,
    timestamp: m.timestamp,
    isMe: (m.sender?.toLowerCase?.() === 'me'),
    images: images.length ? images : undefined
  };
}

async function loadOlderMessages() {
  if (!selectedContact.value) return;
  if (!hasMoreOlder.value) return;
  if (olderLoading.value) return;
  olderLoading.value = true;
  const container = messageContainer.value;
  const prevScrollHeight = container ? container.scrollHeight : 0;
  const prevScrollTop = container ? container.scrollTop : 0; // for safety (should be near 0)
  try {
    const paged: PagedResponse<ApiMessage> = await getMessagesByContactId(selectedContact.value.contactId, nextPage.value, pageSize, 'desc');
    const pageMessages = paged.content.slice().reverse().map(m => toUiMessage(m));
    // Prepend older messages (they are earlier chronologically)
    messages.value = [...pageMessages, ...messages.value];
    hasMoreOlder.value = !paged.last;
    nextPage.value += 1;
    await nextTick();
    // Preserve scroll position (so content doesn't jump)
    if (container) {
      const newScrollHeight = container.scrollHeight;
      container.scrollTop = newScrollHeight - prevScrollHeight + prevScrollTop;
    }
  } catch (e: any) {
    // Show a transient error (reuse messagesError or separate?)
    messagesError.value = e?.message || 'Failed to load older messages';
  } finally {
    olderLoading.value = false;
  }
}

function handleScroll(e: Event) {
  const el = e.target as HTMLElement;
  if (el.scrollTop < 64) { // threshold
    loadOlderMessages();
  }
}

function scrollToBottom() {
  const el = messageContainer.value;
  if (!el) return;
  el.scrollTop = el.scrollHeight; // jump to bottom
}

function formatDate(iso: string) {
  return iso ? new Date(iso).toLocaleString() : '';
}
function formatDateTime(iso: string) {
  if (!iso) return '';
  const d = new Date(iso);
  // Provide concise date-time; fallback to locale full string
  return d.toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  });
}
function onThumbError(ev: Event, img: UiImagePart) {
  const el = ev.target as HTMLImageElement;
  // fallback to full image; if already full, mark error
  if (el.src === img.fullUrl) {
    img.error = true;
  } else {
    el.src = img.fullUrl;
  }
}
function openImage(url: string) {
  window.open(url, '_blank');
}
function normalizePath(p: string) {
  return (p || '').replace(/\\/g,'/');
}
</script>