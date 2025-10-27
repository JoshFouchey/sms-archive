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
                v-for="img in msg.images"
                :key="img.id"
                class="relative group cursor-pointer overflow-hidden rounded-md bg-black/10 dark:bg-black/30"
                :class="img.isSingle ? 'w-48 h-48' : 'w-32 h-32'"
                role="button"
                tabindex="0"
                @click="openImage(img.globalIndex, $event)"
                @keydown.enter.prevent="openImage(img.globalIndex, $event)"
                @keydown.space.prevent="openImage(img.globalIndex, $event)"
              >
                <img
                  :src="img.thumbUrl"
                  :alt="img.contentType"
                  class="w-full h-full object-cover transition-transform duration-200 group-hover:scale-105"
                  loading="lazy"
                  @error="onThumbError($event, img)"
                />
                <button
                  @click.stop="deleteImage(img.id)"
                  class="absolute top-2 right-2 bg-red-500 text-white px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition"
                >✕</button>
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

      <!-- Shared Image Viewer Component -->
      <ImageViewer
        v-if="viewerOpen"
        :images="viewerImages"
        :initialIndex="currentIndex ?? 0"
        :allowDelete="true"
        aria-label="Conversation image viewer"
        @close="closeViewer"
        @delete="deleteImage"
        @indexChange="onIndexChange"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue';
import { getAllContactSummaries, getMessagesByContactId, type ContactSummary as ApiContactSummary, type Message as ApiMessage, type PagedResponse } from '../services/api';
import ImageViewer from '@/components/ImageViewer.vue';
import type { ViewerImage } from '@/components/ImageViewer.vue';
import { useToast } from 'primevue/usetoast';
import { deleteImageById } from '../services/api';

interface UiImagePart { id: number; fullUrl: string; thumbUrl: string; contentType: string; isSingle: boolean; error?: boolean; globalIndex: number; }
interface UiMessage { id: number; body: string; timestamp: string; isMe: boolean; images?: UiImagePart[]; }

const toast = useToast();

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

function normalizePath(p: string) { return (p || '').replace(/\\/g,'/'); }
function extractRelativeMediaPath(fp: string): string | null {
  if (!fp) return null;
  const norm = normalizePath(fp);
  const markers = ['/media/messages/', '/app/media/messages/', 'media/messages/'];
  for (const m of markers) {
    const idx = norm.indexOf(m);
    if (idx >= 0) {
      if (m === '/media/messages/') return norm.substring(idx + '/media/messages/'.length);
      return norm.substring(idx + m.length);
    }
  }
  const parts = norm.split('/').filter(Boolean);
  if (parts.length >= 2) return parts.slice(-2).join('/');
  return null;
}
function buildMediaUrl(rel: string, thumb: boolean): string {
  if (!rel) return '';
  if (thumb) return `/media/messages/${rel.replace(/(\.[A-Za-z0-9]{1,6})$/, '_thumb.jpg')}`;
  return `/media/messages/${rel}`;
}

function toUiMessage(m: ApiMessage): UiMessage {
  const images: UiImagePart[] = [];
  if (Array.isArray(m.parts)) {
    const imageParts = m.parts.filter(p => p.contentType && p.contentType.startsWith('image'));
    const single = imageParts.length === 1;
    for (const p of imageParts) {
      const rel = extractRelativeMediaPath(p.filePath || '');
      if (!rel) continue; // skip if cannot derive relative path
      const fullUrl = buildMediaUrl(rel, false);
      const thumbUrl = buildMediaUrl(rel, true);
      images.push({ id: p.id, fullUrl, thumbUrl, contentType: p.contentType, isSingle: single, globalIndex: -1 });
    }
  }
  const body = (m.body && m.body.trim().length) ? m.body : (images.length ? '' : '[media]');
  return { id: m.id, body, timestamp: m.timestamp, isMe: (m.sender?.toLowerCase?.() === 'me'), images: images.length ? images : undefined };
}

function formatDate(iso: string) { return iso ? new Date(iso).toLocaleDateString() : ''; }
function formatDateTime(iso: string) { return iso ? new Date(iso).toLocaleString() : ''; }
function handleScroll(e: Event) { const el = e.target as HTMLElement; if (el.scrollTop < 64) loadOlderMessages(); }
function onThumbError(ev: Event, img: UiImagePart) { const el = ev.target as HTMLImageElement; if (el.src === img.fullUrl) { img.error = true; } else { el.src = img.fullUrl; } }
function prevImage() { if (currentIndex.value == null || currentIndex.value <= 0) return; currentIndex.value--; }
function nextImage() { if (currentIndex.value == null || currentIndex.value >= conversationImages.value.length - 1) return; currentIndex.value++; }
function closeViewer() { viewerOpen.value = false; currentIndex.value = null; unlockBodyScroll(); }
function onIndexChange(i: number) { currentIndex.value = i; }

// Replace conversationImages computed with flattened list that assigns globalIndex
const conversationImages = computed(() => {
  let acc: UiImagePart[] = []; let idx = 0;
  for (const m of messages.value) {
    if (!m.images) continue;
    for (const img of m.images) { img.globalIndex = idx++; acc.push(img); }
  }
  return acc;
});
const currentIndex = ref<number | null>(null);

const currentImage = computed(() => currentIndex.value == null ? null : conversationImages.value[currentIndex.value]);
const viewerImages = computed<ViewerImage[]>(() => conversationImages.value.map(img => ({ id: img.id, fullUrl: img.fullUrl, thumbUrl: img.thumbUrl, contentType: img.contentType })));

function openImage(index: number) {
  currentIndex.value = index;
  viewerOpen.value = true;
  lockBodyScroll();
}

function confirmAndDeleteCurrent() {
  if (currentImage.value) deleteImage(currentImage.value.id);
}

function deleteImage(id: number) {
  deleteImageById(id).then((ok) => {
    if (!ok) { toast.add({ severity:'error', summary:'Delete Failed', detail:'Server error', life:3000 }); return; }
    for (const m of messages.value) { if (m.images) m.images = m.images.filter(im => im.id !== id); }
    messages.value = messages.value.map(m => ({ ...m }));
    const imgs = conversationImages.value;
    if (currentIndex.value != null) {
      if (currentIndex.value >= imgs.length) currentIndex.value = imgs.length - 1;
      if (!imgs.length) closeViewer();
    }
    toast.add({ severity:'success', summary:'Deleted', detail:'Image removed', life:2000 });
  }).catch(e => toast.add({ severity:'error', summary:'Delete Failed', detail:e.message || 'Error', life:3000 }));
}

const viewerOpen = ref(false);

// Body scroll lock helpers (reuse from Gallery semantics)
let savedScrollY = 0;
function lockBodyScroll() {
  savedScrollY = window.scrollY;
  document.body.style.position = 'fixed';
  document.body.style.top = `-${savedScrollY}px`;
  document.body.style.left = '0';
  document.body.style.right = '0';
  document.body.style.width = '100%';
}
function unlockBodyScroll() {
  document.body.style.position = '';
  document.body.style.top = '';
  document.body.style.left = '';
  document.body.style.right = '';
  document.body.style.width = '';
  window.scrollTo(0, savedScrollY);
}

// Keyboard navigation
function handleKey(e: KeyboardEvent) {
  if (!viewerOpen.value) return;
  switch (e.key) {
    case 'Escape':
      closeViewer();
      break;
    case 'ArrowLeft':
      prevImage();
      break;
    case 'ArrowRight':
      nextImage();
      break;
    case 'Delete':
    case 'Backspace':
      if (currentImage.value) confirmAndDeleteCurrent();
      break;
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey);
  unlockBodyScroll();
});

function scrollToBottom() { const el = messageContainer.value; if (el) el.scrollTop = el.scrollHeight; }
async function loadOlderMessages() {
  if (!selectedContact.value) return;
  if (!hasMoreOlder.value) return;
  if (olderLoading.value) return;
  olderLoading.value = true;
  const container = messageContainer.value;
  const prevHeight = container ? container.scrollHeight : 0;
  const prevTop = container ? container.scrollTop : 0;
  try {
    const paged: PagedResponse<ApiMessage> = await getMessagesByContactId(selectedContact.value.contactId, nextPage.value, pageSize, 'desc');
    const pageMessages = paged.content.slice().reverse().map(m => toUiMessage(m));
    messages.value = [...pageMessages, ...messages.value];
    hasMoreOlder.value = !paged.last;
    nextPage.value += 1;
    await nextTick();
    if (container) { const newHeight = container.scrollHeight; container.scrollTop = newHeight - prevHeight + prevTop; }
  } catch (e: any) {
    messagesError.value = e?.message || 'Failed to load older messages';
  } finally { olderLoading.value = false; }
}
</script>

<style>
</style>
