<template>
  <div class="flex h-screen bg-gray-100 dark:bg-slate-950 text-gray-900 dark:text-gray-100">
    <!-- Conversations List Sidebar -->
    <aside
      :class="[
        'w-full md:w-80 lg:w-96 border-r border-gray-200 dark:border-slate-700 bg-gradient-to-b from-gray-50 to-gray-100 dark:from-slate-900 dark:to-slate-950 backdrop-blur overflow-y-auto flex flex-col',
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
            <h2 class="text-xl font-bold text-white tracking-tight">Conversations</h2>
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
                  'font-semibold truncate text-sm',
                  selectedConversation?.id === conversation.id ? 'text-white' : 'text-gray-900 dark:text-gray-100'
                ]">
                  {{ conversation.name }}
                </h3>
                <p :class="[
                  'text-xs truncate mt-0.5',
                  selectedConversation?.id === conversation.id ? 'text-blue-100' : 'text-gray-600 dark:text-gray-400'
                ]">
                  {{ conversation.lastMessagePreview }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <!-- Main Content Area -->
    <main
      :class="[
        'flex-1 flex flex-col bg-gradient-to-br from-gray-50 via-white to-gray-50 dark:from-slate-900 dark:via-slate-950 dark:to-slate-900 overflow-hidden',
        !selectedConversation ? 'hidden md:flex' : 'flex'
      ]"
    >
      <!-- Header -->
      <div class="flex-shrink-0 bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 p-4 shadow-md">
        <div class="flex items-center gap-3">
          <!-- Back button (mobile) -->
          <button
            v-if="selectedConversation"
            @click="clearConversation"
            class="md:hidden flex items-center justify-center w-10 h-10 rounded-xl bg-white/20 hover:bg-white/30 backdrop-blur-sm active:scale-95 transition-all"
          >
            <i class="pi pi-arrow-left text-white"></i>
          </button>

          <div class="flex-1 min-w-0">
            <h2 class="text-xl font-bold text-white tracking-tight truncate">
              {{ selectedConversation?.name || 'Select a conversation' }}
            </h2>
            <p v-if="selectedConversation" class="text-xs text-blue-100 dark:text-blue-200 mt-0.5">
              {{ isSearchActive ? `${filteredMessages.length} of ${totalMessages}` : totalMessages }} messages
            </p>
          </div>
        </div>

        <!-- Search Bar (only when conversation selected) -->
        <div v-if="selectedConversation" class="mt-3 flex gap-2">
          <div class="flex-1 relative">
            <input
              v-model="searchQuery"
              type="text"
              placeholder="Search messages..."
              class="w-full px-4 py-2 pr-10 rounded-xl bg-white/20 backdrop-blur-sm text-white placeholder-white/70 border border-white/30 focus:bg-white/30 focus:border-white/50 focus:outline-none transition-all"
              @keyup.enter="applySearch"
            />
            <i class="pi pi-search absolute right-3 top-1/2 -translate-y-1/2 text-white/70"></i>
          </div>
          
          <!-- Search navigation controls (only when search is active and has results) -->
          <div v-if="isSearchActive && searchMatches.length > 0" class="flex items-center gap-2 px-3 py-2 rounded-xl bg-white/20 backdrop-blur-sm border border-white/30">
            <span class="text-xs text-white font-medium whitespace-nowrap">
              {{ currentMatchIndex + 1 }} / {{ searchMatches.length }}
            </span>
            <div class="flex gap-1">
              <button
                @click="prevMatch"
                class="p-1 rounded hover:bg-white/20 transition-all"
                title="Previous match"
              >
                <i class="pi pi-chevron-up text-white text-xs"></i>
              </button>
              <button
                @click="nextMatch"
                class="p-1 rounded hover:bg-white/20 transition-all"
                title="Next match"
              >
                <i class="pi pi-chevron-down text-white text-xs"></i>
              </button>
            </div>
          </div>
          
          <button
            @click="toggleFilters"
            :class="[
              'px-4 py-2 rounded-xl backdrop-blur-sm border transition-all',
              showFilters
                ? 'bg-white/30 border-white/50'
                : 'bg-white/20 border-white/30 hover:bg-white/30'
            ]"
            title="More filters"
          >
            <i class="pi pi-filter text-white"></i>
          </button>
          <button
            @click="applySearch"
            class="px-4 py-2 rounded-xl bg-white/20 hover:bg-white/30 backdrop-blur-sm border border-white/30 transition-all"
            title="Search"
          >
            <i class="pi pi-search text-white"></i>
          </button>
          <button
            v-if="isSearchActive"
            @click="clearSearch"
            class="px-4 py-2 rounded-xl bg-white/20 hover:bg-white/30 backdrop-blur-sm border border-white/30 transition-all"
            title="Clear search"
          >
            <i class="pi pi-times text-white"></i>
          </button>
        </div>

        <!-- Loading Status Indicators -->
        <div v-if="selectedConversation && loadingInBackground" class="mt-3">
          <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-700 rounded-xl p-3">
            <div class="flex items-center gap-2 text-blue-700 dark:text-blue-300">
              <i class="pi pi-spin pi-spinner text-sm flex-shrink-0"></i>
              <span class="text-sm font-medium truncate">
                Loading full history... ({{ messages.length.toLocaleString() }} / {{ totalMessages.toLocaleString() }} messages)
              </span>
            </div>
          </div>
        </div>

        <div v-else-if="selectedConversation && fullyLoaded && messages.length > 200" class="mt-3">
          <div class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-700 rounded-xl p-3">
            <div class="flex items-center gap-2 text-green-700 dark:text-green-300">
              <i class="pi pi-check-circle text-sm flex-shrink-0"></i>
              <span class="text-sm font-medium truncate">
                ✓ All {{ messages.length.toLocaleString() }} messages loaded (search ready)
              </span>
            </div>
          </div>
        </div>

        <!-- Warning when searching before fully loaded -->
        <div v-if="selectedConversation && isSearchActive && !fullyLoaded" class="mt-3">
          <div class="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-700 rounded-xl p-3">
            <div class="flex items-center gap-2 text-yellow-700 dark:text-yellow-300">
              <i class="pi pi-exclamation-triangle text-sm flex-shrink-0"></i>
              <span class="text-sm truncate">
                Searching {{ messages.length.toLocaleString() }} messages (still loading full history...)
              </span>
            </div>
          </div>
        </div>

        <!-- Advanced Filters (collapsible) -->
        <div
          v-if="selectedConversation && showFilters"
          class="mt-3 p-4 rounded-xl bg-white/10 backdrop-blur-sm border border-white/20 space-y-3 overflow-hidden"
        >
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3 min-w-0">
            <!-- Date From -->
            <div class="min-w-0">
              <label class="block text-xs text-white/90 mb-1.5 font-medium">From Date</label>
              <input
                v-model="dateFrom"
                type="date"
                class="w-full px-3 py-2 rounded-lg bg-white/20 backdrop-blur-sm text-white border border-white/30 focus:bg-white/30 focus:border-white/50 focus:outline-none transition-all text-sm min-w-0"
              />
            </div>

            <!-- Date To -->
            <div class="min-w-0">
              <label class="block text-xs text-white/90 mb-1.5 font-medium">To Date</label>
              <input
                v-model="dateTo"
                type="date"
                class="w-full px-3 py-2 rounded-lg bg-white/20 backdrop-blur-sm text-white border border-white/30 focus:bg-white/30 focus:border-white/50 focus:outline-none transition-all text-sm min-w-0"
              />
            </div>

            <!-- Sender Filter -->
            <div class="min-w-0">
              <label class="block text-xs text-white/90 mb-1.5 font-medium">Sender</label>
              <select
                v-model="selectedSender"
                class="w-full px-3 py-2 rounded-lg bg-white/20 backdrop-blur-sm text-white border border-white/30 focus:bg-white/30 focus:border-white/50 focus:outline-none transition-all text-sm min-w-0"
              >
                <option value="" class="bg-slate-700">All Senders</option>
                <option
                  v-for="sender in uniqueSenders"
                  :key="sender"
                  :value="sender"
                  class="bg-slate-700"
                >
                  {{ sender }}
                </option>
              </select>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div v-if="!selectedConversation" class="flex-1 flex items-center justify-center p-8">
        <div class="text-center">
          <div class="bg-blue-100 dark:bg-blue-900/30 p-6 rounded-full inline-flex mb-4">
            <i class="pi pi-comments text-5xl text-blue-600 dark:text-blue-400"></i>
          </div>
          <h3 class="text-xl font-semibold text-gray-800 dark:text-gray-200 mb-2">No Conversation Selected</h3>
          <p class="text-sm text-gray-600 dark:text-gray-400">Choose a conversation to view messages</p>
        </div>
      </div>

      <!-- Messages Area -->
      <div v-else ref="messagesContainer" class="flex-1 overflow-y-auto p-4 pb-20 space-y-3 min-h-0" @scroll="handleScroll">
        <!-- Loading State -->
        <div v-if="messagesLoading" class="flex flex-col items-center justify-center py-12">
          <i class="pi pi-spin pi-spinner text-4xl text-blue-600 dark:text-blue-400 mb-3"></i>
          <span class="text-sm text-gray-600 dark:text-gray-400">Loading messages...</span>
        </div>

        <!-- Messages List -->
        <div v-else-if="filteredMessages.length" class="space-y-3">
          <!-- Active search/filter indicator -->
          <div v-if="isSearchActive && searchMatches.length === 0" class="flex items-center justify-center py-2">
            <div class="bg-yellow-100 dark:bg-yellow-900/30 px-4 py-2 rounded-full text-xs text-yellow-700 dark:text-yellow-300 flex items-center gap-2 font-medium">
              <i class="pi pi-exclamation-triangle"></i>
              No matches found
            </div>
          </div>

          <!-- Loading older indicator at top (only when not searching) -->
          <div v-if="olderLoading && !isSearchActive" class="flex items-center justify-center py-2">
            <div class="bg-gray-200 dark:bg-slate-700 px-3 py-1.5 rounded-full text-xs text-gray-600 dark:text-gray-400 flex items-center gap-2">
              <i class="pi pi-spin pi-spinner text-xs"></i>
              Loading older messages...
            </div>
          </div>

          <div
            v-for="msg in filteredMessages"
            :key="msg.id"
            :data-message-id="msg.id"
            class="flex"
            :class="msg.direction === 'OUTBOUND' ? 'justify-end' : 'justify-start'"
          >
            <div class="max-w-[85%]">
              <div
                :class="[
                  'rounded-2xl shadow-md text-sm leading-relaxed transition-all duration-300',
                  msg.direction === 'OUTBOUND'
                    ? 'bg-gradient-to-br from-blue-600 to-cyan-500 text-white'
                    : 'bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 border border-gray-200 dark:border-slate-700',
                  isCurrentMatch(msg.id) ? 'ring-4 ring-amber-400 dark:ring-amber-500 shadow-2xl scale-[1.03]' : '',
                  isMatch(msg.id) && !isCurrentMatch(msg.id) ? (msg.direction === 'OUTBOUND' ? 'ring-2 ring-amber-300/50 dark:ring-amber-400/50' : 'bg-amber-50 dark:bg-amber-900/20 border-amber-200 dark:border-amber-800') : '',
                  isSearchActive && !isMatch(msg.id) ? 'opacity-30' : ''
                ]"
              >
                <!-- Sender name header for group messages (inside bubble) -->
                <div
                  v-if="msg.direction === 'INBOUND' && selectedConversation?.participantCount && selectedConversation.participantCount > 2 && (msg.senderContactName || msg.senderContactNumber)"
                  class="px-4 pt-2.5 pb-1.5 border-b border-gray-200 dark:border-slate-600"
                >
                  <div class="flex items-center gap-2">
                    <span 
                      class="w-2 h-2 rounded-full flex-shrink-0"
                      :class="getParticipantColor(msg.senderContactId)"
                    ></span>
                    <span class="font-semibold text-xs text-gray-700 dark:text-gray-300">{{ msg.senderContactName || msg.senderContactNumber || 'Unknown' }}</span>
                  </div>
                </div>
                
                <!-- Message content -->
                <div class="px-4 py-2.5">
                  <div v-if="msg.body && msg.body !== '[media]'">{{ msg.body }}</div>

                  <!-- Image thumbnails -->
                  <div v-if="getImageParts(msg).length" class="flex flex-wrap gap-2 mt-2">
                    <div
                      v-for="img in getImageParts(msg)"
                      :key="img.id"
                      class="relative group cursor-pointer overflow-hidden rounded-xl bg-black/10 dark:bg-black/30 transition-all hover:scale-105"
                      :class="img.isSingle ? 'w-48 h-48' : 'w-32 h-32'"
                      @click="openImage(img.globalIndex)"
                      role="button"
                      tabindex="0"
                      aria-label="Open full size image"
                    >
                      <img
                        :src="img.thumbUrl"
                        :alt="img.contentType || 'attachment'"
                        class="w-full h-full object-cover transition-transform duration-200 group-hover:scale-110"
                        loading="lazy"
                        @error="handleImageError"
                      />
                    </div>
                  </div>

                  <div class="text-[10px] mt-1 opacity-75">{{ formatTime(msg.timestamp) }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- No messages -->
        <div v-else class="flex flex-col items-center justify-center py-12 text-gray-500 dark:text-gray-400">
          <i class="pi pi-inbox text-5xl mb-3"></i>
          <span class="text-sm font-medium">No messages in this conversation</span>
        </div>
      </div>

      <!-- Image Viewer Component -->
      <ImageViewer
        v-if="viewerOpen"
        :images="viewerImages"
        :initialIndex="currentIndex ?? 0"
        :allowDelete="false"
        aria-label="Message image viewer"
        @close="closeViewer"
        @indexChange="onIndexChange"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  getAllConversations,
  getConversationMessages,
  getAllConversationMessages,
  getConversationMessageCount,
  type ConversationSummary,
  type Message,
} from '../services/api';
import ImageViewer from '@/components/ImageViewer.vue';
import type { ViewerImage } from '@/components/ImageViewer.vue';

const route = useRoute();
const router = useRouter();

// State
const conversations = ref<ConversationSummary[]>([]);
const selectedConversation = ref<ConversationSummary | null>(null);
const messages = ref<Message[]>([]);
const messagesContainer = ref<HTMLElement | null>(null);

// Loading states
const contactsLoading = ref(false);
const messagesLoading = ref(false);
const olderLoading = ref(false);

// Pagination
const currentPage = ref(0);
const totalMessages = ref(0);
const hasMoreOlder = ref(false);

// Search and filter state
const searchQuery = ref('');
const showFilters = ref(false);
const dateFrom = ref('');
const dateTo = ref('');
const selectedSender = ref<string>('');
const isSearchActive = ref(false);
const currentMatchIndex = ref(0);
const searchMatches = ref<number[]>([]); // Array of message IDs that match

// Full conversation loading state
const fullyLoaded = ref(false);
const loadingInBackground = ref(false);

function formatTime(timestamp: string): string {
  const d = new Date(timestamp);
  return d.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

// Image handling functions (matching Messages.vue approach)
interface ImagePart {
  id: number;
  fullUrl: string;
  thumbUrl: string;
  contentType: string;
  isSingle: boolean;
  globalIndex: number;
}

// Image viewer state
const viewerOpen = ref(false);
const currentIndex = ref<number | null>(null);
let savedScrollY = 0;

function normalizePath(p: string) {
  return (p || '').replace(/\\/g, '/');
}

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

// Cache for message image parts
const messageImageCache = new Map<number, ImagePart[]>();

function getImageParts(msg: Message): ImagePart[] {
  // Check cache first
  if (messageImageCache.has(msg.id)) {
    return messageImageCache.get(msg.id)!;
  }

  const result: ImagePart[] = [];
  if (!Array.isArray(msg.parts)) return result;

  const imageParts = msg.parts.filter((p) => p.contentType && p.contentType.startsWith('image'));
  const single = imageParts.length === 1;

  for (const p of imageParts) {
    const rel = extractRelativeMediaPath(p.filePath || '');
    if (!rel) continue;
    const fullUrl = buildMediaUrl(rel, false);
    const thumbUrl = buildMediaUrl(rel, true);
    result.push({
      id: p.id,
      fullUrl,
      thumbUrl,
      contentType: p.contentType,
      isSingle: single,
      globalIndex: -1 // Will be set by allImages computed
    });
  }

  // Cache the result
  messageImageCache.set(msg.id, result);
  return result;
}

// Computed property for all images across all messages with global indices
const allImages = computed(() => {
  const acc: ImagePart[] = [];
  let idx = 0;
  for (const msg of messages.value) {
    const imgs = getImageParts(msg);
    for (const img of imgs) {
      img.globalIndex = idx++;
      acc.push(img);
    }
  }
  return acc;
});

// Convert to ViewerImage format
const viewerImages = computed<ViewerImage[]>(() =>
  allImages.value.map(img => ({
    id: img.id,
    fullUrl: img.fullUrl,
    thumbUrl: img.thumbUrl,
    contentType: img.contentType
  }))
);

// Get unique senders for filter dropdown
const uniqueSenders = computed(() => {
  const senders = new Set<string>();
  for (const msg of messages.value) {
    if (msg.direction === 'INBOUND' && msg.contactName) {
      senders.add(msg.contactName);
    }
  }
  return Array.from(senders).sort();
});

// Check if a message matches the current search/filter criteria
function messageMatchesSearch(msg: Message): boolean {
  if (!isSearchActive.value) return false;

  // Filter by search query (body text)
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase();
    if (!msg.body?.toLowerCase().includes(query)) {
      return false;
    }
  }

  // Filter by date range
  if (dateFrom.value) {
    const fromDate = new Date(dateFrom.value);
    if (new Date(msg.timestamp) < fromDate) {
      return false;
    }
  }
  if (dateTo.value) {
    const toDate = new Date(dateTo.value);
    toDate.setHours(23, 59, 59, 999);
    if (new Date(msg.timestamp) > toDate) {
      return false;
    }
  }

  // Filter by sender
  if (selectedSender.value) {
    if (msg.direction !== 'INBOUND' || msg.contactName !== selectedSender.value) {
      return false;
    }
  }

  return true;
}

// Compute search matches (array of matching message IDs)
const computedSearchMatches = computed(() => {
  if (!isSearchActive.value) return [];
  
  return messages.value
    .filter(msg => messageMatchesSearch(msg))
    .map(msg => msg.id);
});

// Filtered messages - NOW returns ALL messages, not just matches
const filteredMessages = computed(() => {
  return messages.value;
});

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

function getParticipantColor(senderContactId: number | undefined | null): string {
  const defaultColor = 'bg-gray-200 text-gray-900 dark:bg-slate-700 dark:text-gray-100';

  // Return default color if no identifier
  if (!senderContactId) {
    return defaultColor;
  }

  const map = participantColorMap.value;
  const id = String(senderContactId);

  // Assign color if not already assigned
  if (!map.has(id)) {
    const colorIndex = map.size % participantColors.length;
    const color = participantColors[colorIndex] ?? defaultColor;
    map.set(id, color);
  }

  return map.get(id) || defaultColor;
}

function handleImageError(event: Event) {
  const img = event.target as HTMLImageElement;
  console.warn('Failed to load image:', img.src);
  img.style.display = 'none';
}

// Image viewer functions
function openImage(index: number) {
  currentIndex.value = index;
  viewerOpen.value = true;
  lockBodyScroll();
}

function closeViewer() {
  viewerOpen.value = false;
  currentIndex.value = null;
  unlockBodyScroll();
}

function onIndexChange(newIndex: number) {
  currentIndex.value = newIndex;
}

function lockBodyScroll() {
  savedScrollY = window.scrollY || window.pageYOffset;
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

// Keyboard navigation for viewer
function handleKey(e: KeyboardEvent) {
  if (!viewerOpen.value) return;
  if (e.key === 'Escape') {
    closeViewer();
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey);
  unlockBodyScroll();
});

// Search and filter functions (client-side only)
function applySearch() {
  if (!fullyLoaded.value) {
    // Warn user to wait for full load
    console.warn('Not all messages loaded yet. Search results may be incomplete.');
  }
  isSearchActive.value = true;
  searchMatches.value = computedSearchMatches.value;
  currentMatchIndex.value = 0;
  
  // Scroll to first match
  if (searchMatches.value.length > 0) {
    nextTick(() => {
      scrollToMatch(0);
    });
  }
}

function clearSearch() {
  searchQuery.value = '';
  dateFrom.value = '';
  dateTo.value = '';
  selectedSender.value = '';
  isSearchActive.value = false;
  showFilters.value = false;
  searchMatches.value = [];
  currentMatchIndex.value = 0;
}

function nextMatch() {
  if (searchMatches.value.length === 0) return;
  currentMatchIndex.value = (currentMatchIndex.value + 1) % searchMatches.value.length;
  scrollToMatch(currentMatchIndex.value);
}

function prevMatch() {
  if (searchMatches.value.length === 0) return;
  currentMatchIndex.value = (currentMatchIndex.value - 1 + searchMatches.value.length) % searchMatches.value.length;
  scrollToMatch(currentMatchIndex.value);
}

function scrollToMatch(index: number) {
  if (searchMatches.value.length === 0) return;
  const messageId = searchMatches.value[index];
  const element = document.querySelector(`[data-message-id="${messageId}"]`);
  if (element) {
    element.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }
}

function isCurrentMatch(messageId: number): boolean {
  if (!isSearchActive.value || searchMatches.value.length === 0) return false;
  return searchMatches.value[currentMatchIndex.value] === messageId;
}

function isMatch(messageId: number): boolean {
  return searchMatches.value.includes(messageId);
}

function toggleFilters() {
  showFilters.value = !showFilters.value;
}

// Handle scroll event - now mostly unused since we load everything in background
function handleScroll() {
  // With full background loading, scroll handling is not needed
  // All messages are loaded automatically after initial display
  // This function is kept for compatibility but does nothing
}

// Load conversations
async function loadConversations() {
  contactsLoading.value = true;
  try {
    conversations.value = await getAllConversations();
  } catch (e) {
    console.error('Failed to load conversations', e);
  } finally {
    contactsLoading.value = false;
  }
}

// Select conversation
async function selectConversation(conversation: ConversationSummary) {
  selectedConversation.value = conversation;
  participantColorMap.value.clear(); // Clear color assignments for new conversation
  router.push({ name: 'messages', params: { id: conversation.id } });
  await loadMessages();
}

function clearConversation() {
  selectedConversation.value = null;
  messages.value = [];
  fullyLoaded.value = false;
  loadingInBackground.value = false;
  isSearchActive.value = false;
  participantColorMap.value.clear(); // Clear color assignments
  router.push({ name: 'messages' });
}

// Load messages with hybrid approach
async function loadMessages() {
  if (!selectedConversation.value) return;
  messagesLoading.value = true;
  currentPage.value = 0;
  fullyLoaded.value = false;

  try {
    // Step 1: Get total count
    totalMessages.value = await getConversationMessageCount(selectedConversation.value.id);

    // Step 2: Load first 200 messages for quick display
    const res = await getConversationMessages(
      selectedConversation.value.id,
      currentPage.value,
      200, // Load first 200
      'desc'
    );
    messages.value = res.content.reverse(); // Show chronological order (oldest first in view)
    hasMoreOlder.value = !res.last;

    messagesLoading.value = false;

    // Scroll to bottom after initial messages render
    await nextTick();
    requestAnimationFrame(() => {
      if (messagesContainer.value) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
      }
    });

    // Step 3: Start loading ALL messages in background (cached on backend)
    if (totalMessages.value > 200) {
      loadAllMessagesInBackground();
    } else {
      fullyLoaded.value = true;
    }
  } catch (e) {
    console.error('Failed to load messages', e);
    messagesLoading.value = false;
  }
}

// Load all messages in background (backend cached)
async function loadAllMessagesInBackground() {
  if (!selectedConversation.value) return;

  loadingInBackground.value = true;

  try {
    // This endpoint is cached in Caffeine on backend
    const allMessages = await getAllConversationMessages(selectedConversation.value.id);

    // Replace with full dataset
    messages.value = allMessages;
    fullyLoaded.value = true;
    hasMoreOlder.value = false; // No more to load

    console.log(`Loaded all ${allMessages.length} messages for conversation ${selectedConversation.value.id}`);
  } catch (e) {
    console.error('Failed to load all messages', e);
  } finally {
    loadingInBackground.value = false;
  }
}


// Initialize
onMounted(async () => {
  await loadConversations();

  // Check for conversation ID in route
  const id = route.params.id;
  if (id) {
    const conv = conversations.value.find(c => c.id === Number(id));
    if (conv) {
      await selectConversation(conv);
    }
  }
});
</script>
