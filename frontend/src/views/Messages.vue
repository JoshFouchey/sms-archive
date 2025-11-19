<template>
  <div class="flex h-screen bg-gray-100 dark:bg-slate-950 text-gray-900 dark:text-gray-100">
    <!-- Contacts panel - hidden on mobile when conversation is selected -->
    <aside
      :class="[
        'w-full md:w-1/3 md:max-w-sm border-r border-gray-200 dark:border-slate-700 bg-gradient-to-b from-gray-50 to-gray-100 dark:from-slate-900 dark:to-slate-950 backdrop-blur overflow-y-auto flex flex-col',
        selectedConversation ? 'hidden md:flex' : 'flex'
      ]"
    >
      <!-- Sidebar Header -->
      <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 p-4 shadow-md">
        <div class="flex items-center gap-3">
          <div class="bg-white/20 backdrop-blur-sm p-2.5 rounded-xl">
            <i class="pi pi-comments text-2xl text-white"></i>
          </div>
          <div>
            <h2 class="text-xl font-bold text-white tracking-tight">
              Conversations
            </h2>
            <p class="text-xs text-blue-100 dark:text-blue-200">{{ conversations.length }} total</p>
          </div>
        </div>
      </div>

      <!-- Conversations List -->
      <div class="flex-1 overflow-y-auto p-4 space-y-2">

      <div v-if="contactsLoading" class="flex flex-col items-center justify-center py-8 text-gray-500 dark:text-gray-400">
        <i class="pi pi-spin pi-spinner text-3xl text-blue-600 dark:text-blue-400 mb-2"></i>
        <span class="text-sm">Loading conversations...</span>
      </div>
      <div v-else-if="contactsError" class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl p-4 text-sm text-red-600 dark:text-red-400">
        <i class="pi pi-exclamation-circle mr-2"></i>{{ contactsError }}
      </div>
      <div v-else-if="!conversations.length" class="flex flex-col items-center justify-center py-8 text-gray-500 dark:text-gray-400">
        <i class="pi pi-inbox text-4xl mb-2"></i>
        <span class="text-sm">No conversations yet</span>
      </div>

      <div
        v-for="conversation in conversations"
        :key="conversation.id"
        @click="selectConversation(conversation)"
        :class="[
          'group p-4 rounded-xl border cursor-pointer transition-all duration-200 flex flex-col gap-1.5 shadow-sm hover:shadow-md',
          selectedConversation?.id === conversation.id
            ? 'bg-gradient-to-br from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 border-blue-500 text-white scale-[1.02]'
            : 'bg-white dark:bg-slate-800 border-gray-200 dark:border-slate-700 hover:border-blue-300 dark:hover:border-blue-700 hover:bg-blue-50 dark:hover:bg-slate-700 active:scale-[0.98]'
        ]"
      >
        <div class="flex justify-between items-start gap-2">
          <div class="flex items-center gap-2 flex-1 min-w-0">
            <div :class="[
              'w-10 h-10 rounded-full flex items-center justify-center text-lg font-bold shrink-0',
              selectedConversation?.id === conversation.id
                ? 'bg-white/20 text-white'
                : 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
            ]">
              {{ conversation.name.charAt(0).toUpperCase() }}
            </div>
            <div class="flex-1 min-w-0">
              <h3 :class="[
                'font-semibold truncate text-sm flex items-center gap-1.5',
                selectedConversation?.id === conversation.id ? 'text-white' : 'text-gray-900 dark:text-gray-100'
              ]">
                {{ conversation.name }}
                <span v-if="conversation.participantCount > 2" :class="[
                  'text-xs px-1.5 py-0.5 rounded-full font-medium',
                  selectedConversation?.id === conversation.id
                    ? 'bg-white/20 text-white'
                    : 'bg-gray-200 dark:bg-slate-700 text-gray-600 dark:text-gray-400'
                ]">
                  {{ conversation.participantCount }}
                </span>
              </h3>
              <p :class="[
                'text-xs truncate mt-0.5',
                selectedConversation?.id === conversation.id ? 'text-blue-100' : 'text-gray-600 dark:text-gray-400'
              ]">
                {{ conversation.lastMessagePreview }}
              </p>
            </div>
          </div>
          <div class="flex flex-col items-end gap-1 shrink-0">
            <span :class="[
              'text-[10px] uppercase tracking-wider font-medium',
              selectedConversation?.id === conversation.id ? 'text-blue-100' : 'text-gray-500 dark:text-gray-500'
            ]">
              {{ formatDate(conversation.lastMessageAt) }}
            </span>
            <span
              v-if="conversation.lastMessageHasImage"
              :class="[
                'text-lg',
                selectedConversation?.id === conversation.id ? 'opacity-80' : 'opacity-60'
              ]"
            >📷</span>
          </div>
        </div>
      </div>
      </div>
    </aside>

    <!-- Conversation panel - hidden on mobile when no conversation selected -->
    <main
      :class="[
        'flex-1 flex flex-col bg-gradient-to-br from-gray-50 via-white to-gray-50 dark:from-slate-900 dark:via-slate-950 dark:to-slate-900',
        !selectedConversation ? 'hidden md:flex' : 'flex'
      ]"
    >
      <!-- Header with back button on mobile -->
      <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 p-4 shadow-md flex items-center gap-3">
        <button
          v-if="selectedConversation"
          @click="clearConversation"
          class="md:hidden flex items-center justify-center w-10 h-10 rounded-xl bg-white/20 hover:bg-white/30 backdrop-blur-sm active:scale-95 transition-all"
          aria-label="Back to conversations"
        >
          <i class="pi pi-arrow-left text-white"></i>
        </button>
        <div class="flex-1 min-w-0">
          <h2 class="text-xl font-bold text-white tracking-tight truncate flex items-center gap-2">
            <span v-if="selectedConversation?.participantCount && selectedConversation.participantCount > 2">
              <i class="pi pi-users text-lg"></i>
            </span>
            {{ selectedConversation?.name || 'Select a conversation' }}
          </h2>
          <p v-if="selectedConversation" class="text-xs text-blue-100 dark:text-blue-200 mt-0.5">
            <span v-if="selectedConversation.participantCount > 2">
              {{ selectedConversation.participantCount }} participants
            </span>
            <span v-else>1-on-1 conversation</span>
          </p>
        </div>
        <!-- Delete conversation button -->
        <button
          v-if="selectedConversation"
          @click="openDeleteConversationModal(selectedConversation)"
          class="flex items-center justify-center w-10 h-10 rounded-xl bg-white/10 hover:bg-white/20 border-2 border-white/30 hover:border-white/50 text-white backdrop-blur-sm active:scale-95 transition-all group"
          aria-label="Delete conversation"
          title="Delete conversation"
        >
          <i class="pi pi-trash group-hover:scale-110 transition-transform"></i>
        </button>
      </div>

      <!-- Status messages (outside scroll container) -->
      <div v-if="!selectedConversation && !messagesLoading" class="flex-1 flex items-center justify-center p-8">
        <div class="text-center">
          <div class="bg-blue-100 dark:bg-blue-900/30 p-6 rounded-full inline-flex mb-4">
            <i class="pi pi-comments text-5xl text-blue-600 dark:text-blue-400"></i>
          </div>
          <h3 class="text-xl font-semibold text-gray-800 dark:text-gray-200 mb-2">No Conversation Selected</h3>
          <p class="text-sm text-gray-600 dark:text-gray-400">Choose a conversation from the sidebar to view messages</p>
        </div>
      </div>

      <!-- Scrollable messages container -->
      <div v-if="selectedConversation" ref="messageContainer" class="flex-1 overflow-y-auto p-4 space-y-3" @scroll="handleScroll">
        <!-- Older loader / end marker at top -->
        <div class="flex justify-center my-3">
          <div v-if="olderLoading" class="flex items-center gap-2 text-gray-600 dark:text-gray-400">
            <i class="pi pi-spin pi-spinner text-sm"></i>
            <span class="text-xs font-medium">Loading older messages...</span>
          </div>
          <div v-else-if="!hasMoreOlder && messages.length" class="bg-gray-200 dark:bg-slate-700 px-3 py-1.5 rounded-full">
            <span class="text-xs text-gray-600 dark:text-gray-400 font-medium">Beginning of conversation</span>
          </div>
        </div>

        <!-- Main loading state (initial) -->
        <div v-if="messagesLoading" class="flex flex-col items-center justify-center py-12">
          <i class="pi pi-spin pi-spinner text-4xl text-blue-600 dark:text-blue-400 mb-3"></i>
          <span class="text-sm text-gray-600 dark:text-gray-400">Loading messages...</span>
        </div>
        <div v-else-if="messagesError" class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl p-4 text-sm text-red-600 dark:text-red-400">
          <i class="pi pi-exclamation-circle mr-2"></i>{{ messagesError }}
        </div>
        <div v-else-if="!messages.length" class="flex flex-col items-center justify-center py-12 text-gray-500 dark:text-gray-400">
          <i class="pi pi-inbox text-5xl mb-3"></i>
          <span class="text-sm font-medium">No messages in this conversation</span>
        </div>

        <!-- Messages list -->
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="flex flex-col"
          :class="msg.isMe ? 'items-end' : 'items-start'"
        >
          <template v-if="!msg.reaction">
            <!-- Sender name for group messages with color dot indicator -->
            <div
              v-if="!msg.isMe && selectedConversation?.participantCount && selectedConversation.participantCount > 2 && msg.senderName"
              class="text-xs mb-1.5 ml-3 flex items-center gap-1.5"
            >
              <span
                class="w-2 h-2 rounded-full inline-block"
                :class="getParticipantColor(msg.senderIdentifier)"
              ></span>
              <span class="text-gray-600 dark:text-gray-400 font-medium">{{ msg.senderName }}</span>
            </div>
            <div class="relative">
              <div
                :class="[
                  'relative max-w-[78%] md:max-w-[85%] rounded-2xl px-4 py-2.5 shadow-md text-sm leading-relaxed space-y-2 transition-all hover:shadow-lg',
                  msg.isMe
                    ? 'bg-gradient-to-br from-blue-600 to-cyan-500 text-white'
                    : (selectedConversation?.participantCount && selectedConversation.participantCount > 2
                        ? getParticipantColor(msg.senderIdentifier)
                        : 'bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 border border-gray-200 dark:border-slate-700')
                ]"
              >
                <div v-if="msg.body && msg.body !== '[media]'">{{ msg.body }}</div>
                <!-- Image thumbnails -->
                <div v-if="msg.images && msg.images.length" class="flex flex-wrap gap-2">
                  <div
                    v-for="img in msg.images"
                    :key="img.id"
                    class="relative group cursor-pointer overflow-hidden rounded-xl bg-black/10 dark:bg-black/30 focus:outline-none focus:ring-2 focus:ring-blue-400 transition-all hover:scale-105"
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
                      class="w-full h-full object-cover transition-transform duration-200 group-hover:scale-110"
                      loading="lazy"
                      @error="onThumbError($event, img)"
                    />
                    <button
                      type="button"
                      @click.stop="deleteImage(img.id)"
                      class="absolute top-2 right-2 bg-red-500 hover:bg-red-600 text-white w-7 h-7 rounded-full opacity-0 group-hover:opacity-100 transition-all flex items-center justify-center shadow-lg"
                      aria-label="Delete image"
                      title="Delete image"
                    >
                      <i class="pi pi-trash text-xs"></i>
                    </button>
                    <div v-if="img.error" class="absolute inset-0 flex items-center justify-center text-xs bg-black/40 text-white">Image</div>
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
                    class="bg-white dark:bg-slate-800 border-2 border-gray-300 dark:border-slate-600 rounded-full px-2.5 py-1 text-xs shadow-md flex items-center gap-1"
                    :title="r.tooltip"
                    :aria-label="r.count > 1 ? r.emoji + ' reaction count ' + r.count : r.emoji + ' reaction'"
                  >
                    <span aria-hidden="true">{{ r.emoji }}</span>
                    <span v-if="r.count > 1" class="text-[10px] font-bold" aria-hidden="true">{{ r.count }}</span>
                  </li>
                </ul>
              </div>
              <span
                class="mt-1.5 text-[10px] tracking-wide uppercase block px-1"
                :class="msg.isMe ? 'text-blue-600 dark:text-blue-400' : 'text-gray-400 dark:text-gray-500'"
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
      <div class="absolute inset-0 bg-black/50 backdrop-blur-sm" @click="cancelDeleteConversation" aria-hidden="true"></div>
      <!-- Modal panel -->
      <div
        class="relative w-full max-w-sm md:max-w-md bg-white dark:bg-slate-800 rounded-2xl shadow-2xl border border-gray-200 dark:border-slate-700 flex flex-col overflow-hidden animate-fadeIn"
      >
        <!-- Icon Header -->
        <div class="flex items-center justify-center pt-6 pb-4">
          <div class="bg-red-100 dark:bg-red-900/30 p-4 rounded-full">
            <i class="pi pi-exclamation-triangle text-4xl text-red-600 dark:text-red-400"></i>
          </div>
        </div>

        <!-- Content -->
        <div class="px-6 pb-4 text-center">
          <h3 id="delete-conversation-title" class="text-xl font-bold tracking-tight text-gray-900 dark:text-gray-100 mb-2">
            Delete Conversation?
          </h3>
          <p class="text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
            This will permanently delete all messages in
          </p>
          <p class="text-base font-semibold text-gray-900 dark:text-gray-100 mt-1 mb-3">
            {{ conversationPendingDelete?.name }}
          </p>
          <div class="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-3 text-left">
            <div class="flex gap-2">
              <i class="pi pi-info-circle text-yellow-600 dark:text-yellow-400 text-sm mt-0.5 shrink-0"></i>
              <p class="text-xs text-gray-700 dark:text-gray-300">
                All messages and associated images will be permanently removed. This action cannot be undone.
              </p>
            </div>
          </div>
        </div>

        <!-- Actions -->
        <div class="px-6 pb-6 flex flex-col-reverse sm:flex-row gap-3">
          <button
            @click="cancelDeleteConversation"
            :disabled="deletingConversation"
            class="flex-1 inline-flex items-center justify-center rounded-xl bg-gray-100 hover:bg-gray-200 dark:bg-slate-700 dark:hover:bg-slate-600 disabled:opacity-50 text-gray-900 dark:text-gray-100 text-sm font-semibold px-5 py-3 focus:outline-none focus:ring-2 focus:ring-gray-300 dark:focus:ring-slate-600 active:scale-[0.98] transition-all"
          >
            Cancel
          </button>
          <button
            @click="confirmDeleteConversation"
            :disabled="deletingConversation"
            class="flex-1 inline-flex items-center justify-center gap-2 rounded-xl bg-red-600 hover:bg-red-700 disabled:bg-red-400 text-white text-sm font-semibold px-5 py-3 focus:outline-none focus:ring-2 focus:ring-red-500 active:scale-[0.98] transition-all shadow-lg hover:shadow-xl"
          >
            <span v-if="!deletingConversation" class="flex items-center gap-2">
              <i class="pi pi-trash"></i>
              Delete
            </span>
            <span v-else class="inline-flex items-center gap-2">
              <i class="pi pi-spin pi-spinner"></i>
              Deleting...
            </span>
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
