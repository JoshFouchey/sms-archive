<template>
  <div class="flex h-screen bg-gray-100 dark:bg-slate-950 text-gray-900 dark:text-gray-100">
    <!-- Contacts panel - hidden on mobile when conversation is selected -->
    <aside
      :class="[
        'w-full md:w-1/3 md:max-w-sm border-r border-gray-200 dark:border-slate-700 bg-gray-50/90 dark:bg-slate-900/70 backdrop-blur p-4 overflow-y-auto',
        selectedConversation ? 'hidden md:block' : 'block'
      ]"
    >
      <h2 class="text-lg font-semibold mb-4 tracking-tight text-gray-700 dark:text-gray-200">
        Conversations
      </h2>

      <div v-if="contactsLoading" class="text-sm text-gray-500 dark:text-gray-400">Loading conversations...</div>
      <div v-else-if="contactsError" class="text-sm text-red-600 dark:text-red-400">{{ contactsError }}</div>
      <div v-else-if="!conversations.length" class="text-sm text-gray-500 dark:text-gray-400">No conversations.</div>

      <div
        v-for="conversation in conversations"
        :key="conversation.id"
        @click="selectConversation(conversation)"
        :class="[
          'group mb-3 p-4 md:p-3 rounded-lg border cursor-pointer transition-colors duration-150 flex flex-col gap-1 min-h-[72px] md:min-h-0',
          selectedConversation?.id === conversation.id
            ? 'accent-bg accent-border text-white shadow-sm'
            : 'bg-white/90 dark:bg-slate-800/80 border-gray-200 dark:border-slate-700 hover:accent-muted-bg dark:hover:bg-slate-700/70 active:scale-[0.98]'
        ]"
      >
        <div class="flex justify-between items-center">
          <h3 :class="['font-medium truncate text-base md:text-sm', selectedConversation?.id === conversation.id ? 'text-white' : 'text-gray-800 dark:text-gray-100']">
            {{ conversation.name }}
            <span v-if="conversation.participantCount > 2" class="text-xs opacity-70 ml-1">({{ conversation.participantCount }})</span>
          </h3>
          <span
            v-if="conversation.lastMessageHasImage"
            :class="[
              'text-sm',
              selectedConversation?.id === conversation.id
                ? 'accent-soft-text'
                : 'text-gray-400 dark:text-gray-500 group-hover:accent-text'
            ]"
          >📷</span>
        </div>
        <p
          class="text-xs truncate"
          :class="selectedConversation?.id === conversation.id ? 'accent-soft-text' : 'text-gray-600 dark:text-gray-400'"
        >{{ conversation.lastMessagePreview }}</p>
        <p
          class="text-[10px] uppercase tracking-wide"
          :class="selectedConversation?.id === conversation.id ? 'accent-subtle-text' : 'text-gray-400 dark:text-gray-500'"
        >{{ formatDate(conversation.lastMessageAt) }}</p>
      </div>
    </aside>

    <!-- Conversation panel - hidden on mobile when no conversation selected -->
    <main
      :class="[
        'flex-1 flex flex-col p-3 md:p-4 bg-white/70 dark:bg-slate-900/60 backdrop-blur',
        !selectedConversation ? 'hidden md:flex' : 'flex'
      ]"
    >
      <!-- Header with back button on mobile -->
      <div class="flex items-center gap-2 mb-2">
        <button
          v-if="selectedConversation"
          @click="clearConversation"
          class="md:hidden flex items-center justify-center w-10 h-10 rounded-lg bg-gray-200 dark:bg-slate-700 hover:bg-gray-300 dark:hover:bg-slate-600 active:scale-95 transition-all"
          aria-label="Back to conversations"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h2 class="text-lg md:text-xl font-semibold tracking-tight text-gray-700 dark:text-gray-200 flex-1 truncate">
          <span v-if="selectedConversation?.participantCount && selectedConversation.participantCount > 2">Group:</span>
          <span v-else>Conversation with</span>
          <span class="accent-text">{{ selectedConversation?.name || '...' }}</span>
        </h2>
        <!-- Delete conversation button (desktop + mobile when conversation selected) -->
        <button
          v-if="selectedConversation"
          @click="openDeleteConversationModal(selectedConversation)"
          class="flex items-center justify-center w-10 h-10 rounded-lg bg-red-500/90 hover:bg-red-600 text-white active:scale-95 transition focus:outline-none focus:ring-2 focus:ring-red-400"
          aria-label="Delete conversation"
          title="Delete conversation"
        >
          <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 6h18M8 6V4h8v2m1 0v14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V6h10z" />
          </svg>
        </button>
      </div>

      <!-- Status messages (outside scroll container) -->
      <div v-if="!selectedConversation && !messagesLoading" class="mt-4 text-sm text-gray-500 dark:text-gray-500 italic">
        Select a conversation to view messages.
      </div>

      <!-- Scrollable messages container -->
      <div v-if="selectedConversation" ref="messageContainer" class="flex-1 overflow-y-auto mt-2 pr-1 -mr-3 md:mr-0" @scroll="handleScroll">
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
          <template v-if="!msg.reaction">
            <!-- Sender name for group messages with color dot indicator -->
            <div
              v-if="!msg.isMe && selectedConversation?.participantCount && selectedConversation.participantCount > 2 && msg.senderName"
              class="text-xs mb-1 ml-2 flex items-center gap-1.5"
            >
              <span
                class="w-2 h-2 rounded-full inline-block"
                :class="getParticipantColor(msg.senderIdentifier)"
              ></span>
              <span class="text-gray-500 dark:text-gray-400">{{ msg.senderName }}</span>
            </div>
            <div class="relative">
              <div
                :class="[
                  'relative max-w-[78%] md:max-w-[85%] rounded-lg px-3 py-2 shadow-sm text-sm leading-snug space-y-2',
                  msg.isMe
                    ? 'accent-bg text-white'
                    : (selectedConversation?.participantCount && selectedConversation.participantCount > 2
                        ? getParticipantColor(msg.senderIdentifier)
                        : 'bg-gray-200 text-gray-900 dark:bg-slate-700 dark:text-gray-100')
                ]"
              >
                <div v-if="msg.body && msg.body !== '[media]'">{{ msg.body }}</div>
                <!-- Image thumbnails (outer wrapper changed from button to div to avoid nested button) -->
                <div v-if="msg.images && msg.images.length" class="flex flex-wrap gap-2">
                  <div
                    v-for="img in msg.images"
                    :key="img.id"
                    class="relative group cursor-pointer overflow-hidden rounded-md bg-black/10 dark:bg-black/30 focus:outline-none focus:ring-2 focus:ring-accent/70"
                    :class="img.isSingle ? 'w-48 h-48' : 'w-32 h-32'"
                    role="button"
                    tabindex="0"
                    aria-label="Open preview"
                    @click="openImage(img.globalIndex)"
                    @keydown="onThumbKey($event, img.globalIndex)"
                  >
                    <img
                      :src="img.thumbUrl"
                      :alt="img.contentType || 'preview'"
                      class="w-full h-full object-cover transition-transform duration-200 group-hover:scale-105"
                      loading="lazy"
                      @error="onThumbError($event, img)"
                    />
                    <button
                      type="button"
                      @click.stop="deleteImage(img.id)"
                      class="absolute top-2 right-2 bg-red-500 text-white px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition text-xs"
                      aria-label="Delete image"
                      title="Delete image"
                    >✕</button>
                    <div v-if="img.error" class="absolute inset-0 flex items-center justify-center text-[10px] bg-black/40 text-white">Image</div>
                  </div>
                </div>
                <!-- Reactions overlay -->
                <ul
                  v-if="getGroupedReactions(msg.id).length"
                  class="absolute -bottom-2 right-2 flex gap-1"
                  :aria-label="'Reactions for message ' + msg.id"
                >
                  <li
                    v-for="(r, idx) in getGroupedReactions(msg.id)"
                    :key="idx"
                    class="bg-white dark:bg-slate-800 border border-gray-300 dark:border-slate-600 rounded-full px-2 py-0.5 text-xs shadow-sm flex items-center gap-1"
                    :title="r.tooltip"
                    :aria-label="r.count > 1 ? r.emoji + ' reaction count ' + r.count : r.emoji + ' reaction'"
                  >
                    <span aria-hidden="true">{{ r.emoji }}</span>
                    <span v-if="r.count > 1" class="text-[10px] font-semibold" aria-hidden="true">{{ r.count }}</span>
                  </li>
                </ul>
              </div>
              <span
                class="mt-1 text-[10px] tracking-wide uppercase block"
                :class="msg.isMe ? 'accent-dark-text' : 'text-gray-400 dark:text-gray-500'"
              >{{ formatDateTime(msg.timestamp) }}</span>
            </div>
          </template>
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

  <!-- Delete Conversation Confirmation Modal -->
  <teleport to="body">
    <div
      v-if="showDeleteConversationModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8"
      aria-modal="true"
      role="dialog"
      aria-labelledby="delete-conversation-title"
    >
      <!-- Backdrop -->
      <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="cancelDeleteConversation" aria-hidden="true"></div>
      <!-- Modal panel -->
      <div
        class="relative w-full max-w-sm md:max-w-md bg-white dark:bg-slate-800 rounded-xl shadow-lg border border-gray-200 dark:border-slate-700 flex flex-col overflow-hidden animate-fadeIn"
      >
        <div class="px-5 pt-5 pb-3">
          <h3 id="delete-conversation-title" class="text-lg font-semibold tracking-tight text-gray-800 dark:text-gray-100 flex items-center gap-2">
            <svg class="w-5 h-5 text-red-600" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 6h18M8 6V4h8v2m1 0v14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V6h10z" />
            </svg>
            Delete Conversation
          </h3>
          <p class="mt-2 text-sm text-gray-600 dark:text-gray-300">
            This will permanently delete all messages in <strong class="font-medium">{{ conversationPendingDelete?.name }}</strong>.
            <span class="block mt-1">Images associated with this conversation will also be removed. This action cannot be undone.</span>
          </p>
        </div>
        <div class="px-5 pb-5 flex flex-col md:flex-row gap-3 md:gap-2">
          <button
            @click="confirmDeleteConversation"
            :disabled="deletingConversation"
            class="w-full md:w-auto inline-flex items-center justify-center gap-2 rounded-lg bg-red-600 hover:bg-red-700 disabled:bg-red-400 text-white text-sm font-medium px-4 py-2.5 focus:outline-none focus:ring-2 focus:ring-red-400 active:scale-[0.97]"
          >
            <span v-if="!deletingConversation">Delete</span>
            <span v-else class="inline-flex items-center gap-1">
              <svg class="animate-spin w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <circle cx="12" cy="12" r="10" stroke-width="4" class="opacity-25"></circle>
                <path d="M4 12a8 8 0 0 1 8-8" stroke-width="4" class="opacity-75" stroke-linecap="round"></path>
              </svg>
              Deleting...
            </span>
          </button>
          <button
            @click="cancelDeleteConversation"
            :disabled="deletingConversation"
            class="w-full md:w-auto inline-flex items-center justify-center rounded-lg bg-gray-200 hover:bg-gray-300 dark:bg-slate-700 dark:hover:bg-slate-600 disabled:opacity-50 text-gray-800 dark:text-gray-100 text-sm font-medium px-4 py-2.5 focus:outline-none focus:ring-2 focus:ring-gray-400 active:scale-[0.97]"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  </teleport>
</template>
<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue';
import { getAllConversations, getConversationMessages, type ConversationSummary, type Message as ApiMessage, type PagedResponse } from '../services/api';
import ImageViewer from '@/components/ImageViewer.vue';
import type { ViewerImage } from '@/components/ImageViewer.vue';
import { useToast } from 'primevue/usetoast';
import { deleteImageById, deleteConversation } from '../services/api';

interface UiImagePart { id: number; fullUrl: string; thumbUrl: string; contentType: string; isSingle: boolean; error?: boolean; globalIndex: number; }
interface UiReaction { emoji: string; targetMessageBody: string; targetNormalizedBody: string; senderName?: string; targetMessageId?: number; }
interface UiMessage { id: number; body: string; timestamp: string; isMe: boolean; senderName?: string; senderIdentifier?: string; images?: UiImagePart[]; reaction?: UiReaction; normalizedBody?: string; }

const toast = useToast();

// Color palette for group conversation participants
const participantColors = [
  'bg-blue-700 text-white',
  'bg-green-700 text-white',
  'bg-purple-700 text-white',
  'bg-orange-700 text-white',
  'bg-pink-700 text-white',
  'bg-teal-700 text-white',
  'bg-indigo-700 text-white',
  'bg-rose-700 text-white',
  'bg-amber-700 text-white',
  'bg-cyan-700 text-white',
];

// Map to store participant -> color assignments for current conversation
const participantColorMap = ref(new Map<string, string>());

function getParticipantColor(senderIdentifier: string | undefined | null): string {
  const defaultColor = 'bg-gray-200 text-gray-900 dark:bg-slate-700 dark:text-gray-100';

  // Return default color if no identifier
  if (!senderIdentifier) {
    return defaultColor;
  }

  const map = participantColorMap.value;
  const id = String(senderIdentifier);

  // Assign color if not already assigned
  if (!map.has(id)) {
    const colorIndex = map.size % participantColors.length;
    const color = participantColors[colorIndex] ?? defaultColor;
    map.set(id, color);
  }

  return map.get(id) || defaultColor;
}

const conversations = ref<ConversationSummary[]>([]);
const contactsLoading = ref(true);
const contactsError = ref('');

const selectedConversation = ref<ConversationSummary | null>(null);

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
    conversations.value = await getAllConversations();
  } catch (e: any) {
    contactsError.value = e?.message || 'Failed to load conversations';
  } finally {
    contactsLoading.value = false;
  }
});

async function selectConversation(conversation: ConversationSummary) {
  if (selectedConversation.value?.id === conversation.id) return;
  selectedConversation.value = conversation;
  // Reset state
  participantColorMap.value.clear(); // Clear color assignments for new conversation
  messages.value = [];
  messagesError.value = '';
  messagesLoading.value = true;
  nextPage.value = 0;
  hasMoreOlder.value = false;
  olderLoading.value = false;
  await loadInitialMessages();
}

function clearConversation() {
  selectedConversation.value = null;
  messages.value = [];
  participantColorMap.value.clear();
}

async function loadInitialMessages() {
  if (!selectedConversation.value) return;
  try {
    const paged: PagedResponse<ApiMessage> = await getConversationMessages(selectedConversation.value.id, 0, pageSize, 'desc');
    messages.value = paged.content
      .slice()
      .reverse()
      .map(m => toUiMessage(m));
    hasMoreOlder.value = !paged.last;
    nextPage.value = 1;
    await nextTick();
    scrollToBottom();
  } catch (e: any) {
    messagesError.value = e?.message || 'Failed to load messages';
  } finally {
    messagesLoading.value = false;
  }
  rebuildReactionIndex();
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

function normalizeForMatch(s: string): string {
  return (s || '')
    .replace(/[“”]/g, '"')
    .replace(/[\u2000-\u200F\uFEFF]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function extractImageParts(m: ApiMessage): UiImagePart[] {
  const result: UiImagePart[] = [];
  if (!Array.isArray(m.parts)) return result;
  const imageParts = m.parts.filter((p) => p.contentType && p.contentType.startsWith('image'));
  const single = imageParts.length === 1;
  for (const p of imageParts) {
    const rel = extractRelativeMediaPath(p.filePath || '');
    if (!rel) continue;
    const fullUrl = buildMediaUrl(rel, false);
    const thumbUrl = buildMediaUrl(rel, true);
    result.push({ id: p.id, fullUrl, thumbUrl, contentType: p.contentType, isSingle: single, globalIndex: -1 });
  }
  return result;
}

function parseReaction(normalizedBody: string, m: ApiMessage): UiReaction | undefined {
  const match = normalizedBody.match(/^(.+?)\s+to\s+"(.+)"$/);
  if (!match || match.length < 3) return undefined;
  const rawEmoji: string = match[1] ?? '';
  const rawTarget: string = match[2] ?? '';
  if (!rawEmoji || !rawTarget) return undefined;
  const emoji = rawEmoji.trim();
  if (emoji.length > 12) return undefined;
  const senderNameValue: string | undefined = m.senderContactName ?? m.senderContactNumber ?? undefined;
  return {
    emoji,
    targetMessageBody: rawTarget,
    targetNormalizedBody: normalizeForMatch(rawTarget),
    ...(senderNameValue ? { senderName: senderNameValue } : {})
  } as UiReaction;
}

function toUiMessage(m: ApiMessage): UiMessage {
  const images = extractImageParts(m);
  const bodyRaw = m.body ? m.body.trim() : '';
  let body: string;
  if (bodyRaw.length) {
    body = bodyRaw;
  } else if (images.length) {
    body = '';
  } else {
    body = '[media]';
  }
  const normalizedBody = normalizeForMatch(body);
  const reaction = parseReaction(normalizedBody, m);
  if (reaction) body = '';
  const isMe = m.direction === 'OUTBOUND' || !m.senderContactId;
  const senderName = m.senderContactName || m.senderContactNumber || undefined;
  const senderIdentifier = m.senderContactNumber || m.senderContactId?.toString();
  return { id: m.id, body, timestamp: m.timestamp, isMe, senderName, senderIdentifier, images: images.length ? images : undefined, reaction, normalizedBody };
}

const formatDate = (iso: string) => iso ? new Date(iso).toLocaleDateString() : '';
const formatDateTime = (iso: string) => iso ? new Date(iso).toLocaleString() : '';
function handleScroll(e: Event) { const el = e.target as HTMLElement; if (el.scrollTop < 64) loadOlderMessages(); }
function onThumbError(ev: Event, img: UiImagePart) { const el = ev.target as HTMLImageElement; if (el.src === img.fullUrl) { img.error = true; } else { el.src = img.fullUrl; } }
function onThumbKey(e: KeyboardEvent, index: number) {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault();
    openImage(index);
  }
}
// Refactor single-line functions to multi-line for style warnings
function prevImage() {
  if (currentIndex.value == null) return;
  if (currentIndex.value <= 0) return;
  currentIndex.value--;
}
function nextImage() {
  if (currentIndex.value == null) return;
  if (currentIndex.value >= conversationImages.value.length - 1) return;
  currentIndex.value++;
}
function closeViewer() {
  viewerOpen.value = false;
  currentIndex.value = null;
  unlockBodyScroll();
}
function onIndexChange(i: number) {
  currentIndex.value = i;
}
function scrollToBottom() {
  const el = messageContainer.value;
  if (!el) return;
  el.scrollTop = el.scrollHeight;
}

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

async function loadOlderMessages() {
  if (!selectedConversation.value) return;
  if (!hasMoreOlder.value) return;
  if (olderLoading.value) return;
  olderLoading.value = true;
  const container = messageContainer.value;
  const prevHeight = container ? container.scrollHeight : 0;
  const prevTop = container ? container.scrollTop : 0;
  try {
    const paged: PagedResponse<ApiMessage> = await getConversationMessages(selectedConversation.value.id, nextPage.value, pageSize, 'desc');
    const newMessages = paged.content.slice().reverse().map(m => toUiMessage(m));
    messages.value = [...newMessages, ...messages.value];
    hasMoreOlder.value = !paged.last;
    nextPage.value += 1;
    await nextTick();
    if (container) { const newHeight = container.scrollHeight; container.scrollTop = newHeight - prevHeight + prevTop; }
  } catch (e: any) {
    messagesError.value = e?.message || 'Failed to load older messages';
  } finally { olderLoading.value = false; }
  rebuildReactionIndex();
}

const reactionIndex = ref(new Map<number, UiReaction[]>()); // messageId -> reactions

function rebuildReactionIndex() {
  reactionIndex.value.clear();
  const priorMessages: UiMessage[] = [];
  for (const msg of messages.value) {
    if (msg.reaction) {
      for (let i = priorMessages.length - 1; i >= 0; i--) {
        const candidate: UiMessage | undefined = priorMessages[i];
        if (!candidate) continue;
        if (candidate.normalizedBody === msg.reaction.targetNormalizedBody) {
          msg.reaction.targetMessageId = candidate.id;
          const arr = reactionIndex.value.get(candidate.id) || [];
            arr.push(msg.reaction);
            reactionIndex.value.set(candidate.id, arr);
          break;
        }
      }
    } else {
      priorMessages.push(msg);
    }
  }
}

function getGroupedReactions(messageId: number) {
  const reactions = reactionIndex.value.get(messageId) || [];
  if (!reactions.length) return [] as { emoji: string; count: number; tooltip: string }[];
  const counts = new Map<string, { emoji: string; count: number; senders: string[] }>();
  for (const r of reactions) {
    const key = r.emoji;
    if (!counts.has(key)) counts.set(key, { emoji: r.emoji, count: 0, senders: [] });
    const entry = counts.get(key)!;
    entry.count += 1;
    if (r.senderName) entry.senders.push(r.senderName);
  }
  return Array.from(counts.values()).map(e => ({
    emoji: e.emoji,
    count: e.count,
    tooltip: e.senders.length ? `${e.emoji} by ${e.senders.join(', ')}` : `${e.emoji}`
  }));
}

// Ensure media viewer reactive state & helpers (re-added after earlier removal)
const viewerOpen = ref(false);
const currentIndex = ref<number | null>(null);
const conversationImages = computed(() => {
  const acc: UiImagePart[] = []; let idx = 0;
  for (const m of messages.value) {
    if (!m.images) continue;
    for (const img of m.images) { img.globalIndex = idx++; acc.push(img); }
  }
  return acc;
});
const currentImage = computed(() => currentIndex.value == null ? null : conversationImages.value[currentIndex.value]);
const viewerImages = computed<ViewerImage[]>(() => conversationImages.value.map(img => ({ id: img.id, fullUrl: img.fullUrl, thumbUrl: img.thumbUrl, contentType: img.contentType })));

function openImage(index: number) {
  currentIndex.value = index;
  viewerOpen.value = true;
  lockBodyScroll();
}
function confirmAndDeleteCurrent() {
  if (!currentImage.value) return;
  deleteImage(currentImage.value.id);
}
function deleteImage(id: number) {
  deleteImageById(id).then(ok => {
    if (!ok) { toast.add({ severity:'error', summary:'Delete Failed', detail:'Server error', life:3000 }); return; }
    for (const m of messages.value) { if (m.images) m.images = m.images.filter(im => im.id !== id); }
    messages.value = messages.value.map(m => ({ ...m }));
    const imgs = conversationImages.value;
    if (currentIndex.value != null && currentIndex.value >= imgs.length) currentIndex.value = imgs.length - 1;
    if (!imgs.length) closeViewer();
    toast.add({ severity:'success', summary:'Deleted', detail:'Image removed', life:2000 });
  }).catch(e => toast.add({ severity:'error', summary:'Delete Failed', detail:e?.message || 'Error', life:3000 }));
}

// Delete conversation modal state
const showDeleteConversationModal = ref(false);
const deletingConversation = ref(false);
const conversationPendingDelete = ref<ConversationSummary | null>(null);

function openDeleteConversationModal(c: ConversationSummary) {
  conversationPendingDelete.value = c;
  showDeleteConversationModal.value = true;
  // Optional: lock scroll on mobile to prevent background scroll
  lockBodyScroll();
}
function cancelDeleteConversation() {
  if (deletingConversation.value) return; // don't allow cancel during delete
  showDeleteConversationModal.value = false;
  conversationPendingDelete.value = null;
  unlockBodyScroll();
}
async function confirmDeleteConversation() {
  if (!conversationPendingDelete.value) return;
  deletingConversation.value = true;
  try {
    const result = await deleteConversation(conversationPendingDelete.value.id);
    if (result === 'deleted') {
      const deletedId = conversationPendingDelete.value!.id;
      conversations.value = conversations.value.filter(c => c.id !== deletedId);
      if (selectedConversation.value?.id === deletedId) {
        // Close any open viewer before clearing
        if (viewerOpen.value) closeViewer();
        clearConversation();
      }
      toast.add({ severity: 'success', summary: 'Conversation Deleted', detail: 'All messages removed', life: 3000 });
    } else {
      toast.add({ severity: 'warn', summary: 'Not Found', detail: 'Conversation no longer exists', life: 3000 });
    }
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Delete Failed', detail: e?.message || 'Server error', life: 4000 });
  } finally {
    deletingConversation.value = false;
    showDeleteConversationModal.value = false;
    conversationPendingDelete.value = null;
    unlockBodyScroll();
  }
}

// Close modal if conversation changes while open (e.g., user selects different conversation via aside)
watch(selectedConversation, (newVal) => {
  if (showDeleteConversationModal.value && conversationPendingDelete.value && newVal && newVal.id !== conversationPendingDelete.value.id) {
    cancelDeleteConversation();
  }
});

// Ensure escape key closes the modal even if image viewer also open
function handleDeleteModalKey(e: KeyboardEvent) {
  if (!showDeleteConversationModal.value) return;
  if (e.key === 'Escape') {
    cancelDeleteConversation();
  }
}
window.addEventListener('keydown', handleDeleteModalKey);
// Clean up listener on unmount
onUnmounted(() => window.removeEventListener('keydown', handleDeleteModalKey));
</script>
<style>
/* Reaction badges are positioned absolutely relative to message bubble */
@keyframes fadeIn { from { opacity:0; transform:translateY(8px); } to { opacity:1; transform:translateY(0); } }
.animate-fadeIn { animation: fadeIn 0.18s ease-out; }
</style>
