<template>
  <div class="space-y-6">
    <!-- Header Section -->
    <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 rounded-2xl shadow-lg p-6 text-white">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 class="text-4xl font-bold mb-2 flex items-center gap-3">
            <i class="pi pi-search"></i>
            Search Messages
          </h1>
          <p class="text-blue-100 dark:text-blue-200">Just type naturally — AI picks the best search strategy</p>
        </div>
        <div v-if="filteredResults.length" class="flex items-center gap-3">
          <div v-if="activeMode" class="bg-white/10 backdrop-blur-sm rounded-lg px-3 py-2 border border-white/20">
            <p class="text-xs text-blue-200">Strategy</p>
            <p class="text-sm font-bold">{{ activeMode }}</p>
          </div>
          <div class="bg-white/10 backdrop-blur-sm rounded-lg px-4 py-2 border border-white/20">
            <div class="flex items-center gap-2">
              <i class="pi pi-check-circle text-lg"></i>
              <div class="text-left">
                <p class="text-xs text-blue-100">Results Found</p>
                <p class="text-2xl font-bold">{{ totalResults.toLocaleString() }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Search Form -->
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 border border-gray-200 dark:border-gray-700">
      <form
        class="flex gap-4 items-end"
        @submit.prevent="performSearch"
      >
        <div class="flex-1 flex flex-col gap-2">
          <div class="flex items-center gap-2">
            <label for="searchText" class="text-xs font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wide">
              <i class="pi pi-search text-xs mr-1"></i>
              Search
            </label>
            <!-- Search Tips Tooltip -->
            <div class="relative" @mouseenter="showTips = true" @mouseleave="showTips = false">
              <button type="button" class="text-gray-400 hover:text-blue-500 transition-colors" aria-label="Search tips">
                <i class="pi pi-question-circle text-sm"></i>
              </button>
              <div
                v-if="showTips"
                class="absolute left-0 top-6 z-50 w-80 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-xl shadow-xl p-4 text-sm"
              >
                <h4 class="font-bold text-gray-800 dark:text-gray-100 mb-2 flex items-center gap-1.5">
                  <i class="pi pi-info-circle text-blue-500"></i>
                  Search Tips
                </h4>
                <ul class="space-y-2 text-gray-600 dark:text-gray-400">
                  <li class="flex items-start gap-2">
                    <span class="inline-block px-1.5 py-0.5 rounded bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 text-[10px] font-bold mt-0.5 shrink-0">KEYWORD</span>
                    <span>Use <strong class="text-gray-800 dark:text-gray-200">quotes</strong> for exact match: <code class="text-xs bg-gray-100 dark:bg-gray-800 px-1 rounded">"Ford Mustang"</code></span>
                  </li>
                  <li class="flex items-start gap-2">
                    <span class="inline-block px-1.5 py-0.5 rounded bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 text-[10px] font-bold mt-0.5 shrink-0">KEYWORD</span>
                    <span>Short terms (1-2 words) search by keyword: <code class="text-xs bg-gray-100 dark:bg-gray-800 px-1 rounded">birthday</code></span>
                  </li>
                  <li class="flex items-start gap-2">
                    <span class="inline-block px-1.5 py-0.5 rounded bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300 text-[10px] font-bold mt-0.5 shrink-0">SEMANTIC</span>
                    <span>Ask questions for meaning-based search: <code class="text-xs bg-gray-100 dark:bg-gray-800 px-1 rounded">when did we talk about vacation?</code></span>
                  </li>
                  <li class="flex items-start gap-2">
                    <span class="inline-block px-1.5 py-0.5 rounded bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300 text-[10px] font-bold mt-0.5 shrink-0">HYBRID</span>
                    <span>Longer phrases use both keyword + AI: <code class="text-xs bg-gray-100 dark:bg-gray-800 px-1 rounded">planning the family road trip</code></span>
                  </li>
                </ul>
                <p class="text-[11px] text-gray-400 dark:text-gray-500 mt-3 border-t border-gray-100 dark:border-gray-800 pt-2">
                  The search engine automatically picks the best strategy based on your query.
                </p>
              </div>
            </div>
          </div>
          <input
            id="searchText"
            v-model="searchText"
            type="text"
            placeholder="Try &quot;birthday party&quot; or &quot;what does Tom drive?&quot;"
            class="px-4 py-2.5 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all"
            @keyup.enter="performSearch"
          />
        </div>
        <div class="flex gap-2">
          <button
            type="submit"
            :disabled="loading"
            class="bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white rounded-lg px-6 py-2.5 disabled:opacity-50 transition-all font-semibold shadow-sm hover:shadow-md flex items-center justify-center gap-2"
          >
            <i v-if="!loading" class="pi pi-search"></i>
            <i v-else class="pi pi-spin pi-spinner"></i>
            <span>{{ loading ? 'Searching...' : 'Search' }}</span>
          </button>
          <button
            type="button"
            @click="clearSearch"
            class="bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-800 dark:text-gray-100 rounded-lg px-4 py-2.5 transition-all font-semibold shadow-sm hover:shadow-md"
            title="Clear search"
          >
            <i class="pi pi-times"></i>
          </button>
        </div>
      </form>
    </div>

    <!-- States -->
    <div v-if="error" class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl p-6 text-center shadow-md">
      <i class="pi pi-exclamation-circle text-3xl text-red-600 dark:text-red-400 mb-2"></i>
      <p class="text-sm text-red-600 dark:text-red-400 font-medium">{{ error }}</p>
    </div>
    <div
      v-else-if="loading"
      class="flex flex-col items-center justify-center py-12 bg-white dark:bg-gray-800 rounded-xl shadow-md border border-gray-200 dark:border-gray-700"
    >
      <i class="pi pi-spin pi-spinner text-4xl text-blue-600 dark:text-blue-400 mb-3"></i>
      <span class="text-sm text-gray-600 dark:text-gray-400 font-medium">Searching messages...</span>
    </div>
    <div
      v-else-if="touched && !filteredResults.length"
      class="bg-white dark:bg-gray-800 rounded-xl shadow-md p-8 border border-gray-200 dark:border-gray-700 text-center"
    >
      <div class="flex flex-col items-center gap-4">
        <div class="bg-yellow-100 dark:bg-yellow-900/30 p-4 rounded-full">
          <i class="pi pi-inbox text-4xl text-yellow-600 dark:text-yellow-400"></i>
        </div>
        <div>
          <h3 class="text-xl font-semibold text-gray-800 dark:text-gray-200 mb-2">No Results Found</h3>
          <p class="text-gray-600 dark:text-gray-400">Try rephrasing your search or using different terms</p>
        </div>
      </div>
    </div>
    <div
      v-else-if="!touched"
      class="bg-white dark:bg-gray-800 rounded-xl shadow-md p-8 border border-gray-200 dark:border-gray-700 text-center"
    >
      <div class="flex flex-col items-center gap-4">
        <div class="bg-blue-100 dark:bg-blue-900/30 p-4 rounded-full">
          <i class="pi pi-search text-4xl text-blue-600 dark:text-blue-400"></i>
        </div>
        <div>
          <h3 class="text-xl font-semibold text-gray-800 dark:text-gray-200 mb-2">Ready to Search</h3>
          <p class="text-gray-600 dark:text-gray-400">Type a question, phrase, or keyword to find messages</p>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">
            <i class="pi pi-question-circle mr-1"></i>
            Hover the <strong>?</strong> icon above for search tips
          </p>
        </div>
      </div>
    </div>

    <!-- Results Cards -->
    <div
      v-if="filteredResults.length"
      class="space-y-4"
    >
      <div
        v-for="m in filteredResults"
        :key="m.id"
        class="group border border-gray-200 dark:border-gray-700 rounded-xl p-5 bg-white dark:bg-gray-800 hover:shadow-xl hover:border-blue-300 dark:hover:border-blue-700 transition-all duration-300 hover:scale-[1.01]"
      >
        <!-- Header row: Avatar, Contact name and timestamp -->
        <div class="flex items-start gap-3 mb-4">
          <div class="w-12 h-12 rounded-full bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center text-white text-lg font-bold shrink-0 shadow-md">
            {{ (m.conversationName || m.contactName || m.contactNumber || 'U').charAt(0).toUpperCase() }}
          </div>
          <div class="flex-1 min-w-0">
            <h3 class="font-bold text-lg text-gray-900 dark:text-gray-100 truncate">
              {{ m.conversationName || m.contactName || m.contactNumber || 'Unknown' }}
            </h3>
            <div class="flex items-center gap-2 mt-1">
              <span
                :class="{
                  'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300': m.direction === 'INBOUND',
                  'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300': m.direction === 'OUTBOUND'
                }"
                class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold"
              >
                <i :class="m.direction === 'INBOUND' ? 'pi pi-arrow-down' : 'pi pi-arrow-up'"></i>
                <span>{{ m.direction === 'INBOUND' ? 'Received' : 'Sent' }}</span>
              </span>
              <span class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1">
                <i class="pi pi-clock text-[10px]"></i>
                {{ formatDateTime(m.timestamp) }}
              </span>
              <!-- Provenance badges inline -->
              <span v-if="m._source" :class="{
                'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300': m._source === 'KEYWORD',
                'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300': m._source === 'SEMANTIC',
                'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300': m._source === 'BOTH',
              }" class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold">
                <i :class="{
                  'pi pi-search': m._source === 'KEYWORD',
                  'pi pi-lightbulb': m._source === 'SEMANTIC',
                  'pi pi-bolt': m._source === 'BOTH',
                }" class="text-[8px]"></i>
                {{ m._source === 'BOTH' ? 'Keyword + Semantic' : m._source === 'KEYWORD' ? 'Keyword Match' : 'Semantic Match' }}
              </span>
              <span v-if="m._score != null && m._score > 0" class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300">
                <i class="pi pi-star-fill text-[8px]"></i>
                {{ formatScore(m._score) }}
              </span>
            </div>
          </div>
          <!-- Actions -->
          <div class="flex items-start gap-2">
            <button
              class="bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-700 hover:border-blue-500 text-blue-600 dark:text-blue-400 rounded-lg px-3 py-2 text-sm font-semibold shadow-sm hover:shadow-md"
              @click="openContext(m)"
              title="View messages around this hit"
            >
              <i class="pi pi-list mr-1"></i>
              View context
            </button>
          </div>
        </div>

        <!-- Message body -->
        <div class="bg-gray-50 dark:bg-gray-900/50 rounded-lg p-4 border border-gray-200 dark:border-gray-700">
          <p class="text-sm text-gray-800 dark:text-gray-200 leading-relaxed break-words whitespace-pre-wrap">{{ m.body }}</p>
        </div>
      </div>
    </div>

    <!-- Context Modal -->
    <div v-if="contextOpen" class="fixed inset-0 z-50 flex items-center justify-center">
      <!-- Backdrop -->
      <div class="absolute inset-0 bg-black/50 backdrop-blur-sm" @click="closeContext"></div>
      <!-- Dialog -->
      <div class="relative bg-white dark:bg-slate-800 rounded-2xl shadow-2xl border border-gray-200 dark:border-slate-700 w-full max-w-3xl mx-4 overflow-hidden">
        <!-- Modal Header (gradient like conversations) -->
        <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 p-4 shadow-md flex items-center justify-between">
          <h2 class="text-lg font-bold text-white tracking-tight">Message Context</h2>
          <button class="flex items-center justify-center w-9 h-9 rounded-xl bg-white/20 hover:bg-white/30 backdrop-blur-sm active:scale-95 transition-all" @click="closeContext" title="Close">
            <i class="pi pi-times text-white"></i>
          </button>
        </div>
        <!-- Modal Body -->
        <div class="p-4 max-h-[70vh] overflow-auto bg-gradient-to-br from-gray-50 via-white to-gray-50 dark:from-slate-900 dark:via-slate-950 dark:to-slate-900">
          <div v-if="contextError" class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl p-4 mb-3">
            <p class="text-sm text-red-700 dark:text-red-300">{{ contextError }}</p>
          </div>
          <div v-else-if="contextLoading" class="flex items-center justify-center py-8">
            <i class="pi pi-spin pi-spinner text-3xl text-blue-600 dark:text-blue-400"></i>
          </div>
          <div v-else-if="contextData" class="space-y-3">
            <!-- Before messages (older than center) -->
            <div v-if="contextData.before.length" class="space-y-2">
              <div class="text-[10px] uppercase tracking-wide text-gray-500 dark:text-gray-400">Earlier</div>
              <MessageBubble
                v-for="b in beforeChrono"
                :key="b.id"
                :message="b"
                :is-group-chat="isGroupChat(b)"
                :participant-color-map="participantColorMap"
              />
            </div>

            <!-- Center message (highlight) -->
            <MessageBubble
              v-if="contextData.center"
              :message="contextData.center"
              :is-group-chat="isGroupChat(contextData.center)"
              :participant-color-map="participantColorMap"
              highlight-class="ring-4 ring-yellow-400 dark:ring-yellow-500 shadow-2xl scale-[1.02]"
            />

            <!-- After messages (newer than center) -->
            <div v-if="contextData.after.length" class="space-y-2">
              <div class="text-[10px] uppercase tracking-wide text-gray-500 dark:text-gray-400">Later</div>
              <MessageBubble
                v-for="a in contextData.after"
                :key="a.id"
                :message="a"
                :is-group-chat="isGroupChat(a)"
                :participant-color-map="participantColorMap"
              />
            </div>
          </div>
        </div>
        <!-- Modal Footer -->
        <div class="flex items-center justify-between p-4 border-t border-gray-200 dark:border-slate-700 bg-white dark:bg-slate-800">
          <div class="text-[10px] uppercase tracking-wide text-gray-500 dark:text-gray-400" v-if="contextData">Conversation ID: {{ contextData.conversationId }}</div>
          <div class="flex items-center gap-2">
            <button class="px-3 py-2 text-sm rounded-xl bg-gray-100 hover:bg-gray-200 dark:bg-slate-700 dark:hover:bg-slate-600 text-gray-900 dark:text-gray-100" @click="closeContext">Close</button>
            <RouterLink
              v-if="contextData"
              class="px-3 py-2 text-sm rounded-xl bg-blue-600 hover:bg-blue-700 text-white"
              :to="{ name: 'MessagesDetail', params: { id: contextData.conversationId } }"
              title="Open conversation"
            >
              Open conversation
            </RouterLink>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { RouterLink } from 'vue-router';
import {
  searchUnified,
  getMessageContext,
  type Message,
  type MessageContext,
} from '../services/api';
import MessageBubble from '../components/MessageBubble.vue';

// Extend Message with search metadata
interface SearchMessage extends Message {
  _score?: number;
  _source?: string;
}

const searchText = ref('');
const showTips = ref(false);

const results = ref<SearchMessage[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const touched = ref(false);
const totalResults = ref(0);
const activeMode = ref<string>('');

// Context modal state
const contextOpen = ref(false);
const contextLoading = ref(false);
const contextError = ref<string | null>(null);
const contextData = ref<MessageContext | null>(null);

// Participant colors for group chats
const participantColorMap = ref(new Map<string, string>());

function isGroupChat(msg: Message): boolean {
  return (msg.conversationParticipantCount ?? 0) >= 2;
}

async function performSearch() {
  touched.value = true;
  error.value = null;
  if (!searchText.value.trim()) {
    results.value = [];
    return;
  }
  loading.value = true;
  results.value = [];
  try {
    const response = await searchUnified(searchText.value.trim(), 'AUTO', 50);
    results.value = response.hits.map(h => ({
      ...h.message,
      _score: h.score,
      _source: h.source,
    }));
    totalResults.value = response.totalHits;
    activeMode.value = response.mode;
  } catch (e: any) {
    error.value = e?.message || 'Search failed';
  } finally {
    loading.value = false;
  }
}

function clearSearch() {
  searchText.value = '';
  results.value = [];
  error.value = null;
  touched.value = false;
  totalResults.value = 0;
  activeMode.value = '';
}

const filteredResults = computed(() => results.value);

function formatDateTime(iso: string) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatScore(score: number): string {
  // Semantic scores are 0-1 (show as %), RRF scores are small decimals (show raw)
  if (score > 0 && score <= 1) {
    return `${(score * 100).toFixed(0)}% match`;
  }
  return `${score.toFixed(3)} relevance`;
}

const beforeChrono = computed(() => {
  if (!contextData.value) return [] as Message[];
  return [...contextData.value.before].reverse();
});

async function openContext(m: Message) {
  contextOpen.value = true;
  contextLoading.value = true;
  contextError.value = null;
  contextData.value = null;
  participantColorMap.value.clear();
  try {
    contextData.value = await getMessageContext(m.id, 25, 25);
  } catch (e: any) {
    contextError.value = e?.message || 'Failed to load message context';
  } finally {
    contextLoading.value = false;
  }
}

function closeContext() {
  contextOpen.value = false;
}
</script>
