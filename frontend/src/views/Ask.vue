<template>
  <div class="h-full flex flex-col">
    <!-- Header with search bar -->
    <div class="shrink-0 px-4 pt-4 pb-3 sm:px-6 sm:pt-6 sm:pb-4">
      <div class="max-w-3xl mx-auto text-center mb-4">
        <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-1 flex items-center justify-center gap-2">
          <i class="pi pi-sparkles text-amber-500"></i>
          Ask
        </h1>
      </div>

      <!-- Mode Tabs -->
      <div class="max-w-3xl mx-auto mb-4">
        <div class="flex items-center justify-center gap-1 p-1 bg-gray-100 dark:bg-gray-800 rounded-xl w-fit mx-auto">
          <button
            @click="switchMode('SEARCH')"
            :class="[
              'flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all',
              mode === 'SEARCH'
                ? 'bg-white dark:bg-gray-700 text-emerald-600 dark:text-emerald-400 shadow-sm'
                : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
            ]"
          >
            <i class="pi pi-search text-xs"></i>
            <span class="hidden sm:inline">Search</span>
          </button>
          <button
            @click="switchMode('DATA')"
            :class="[
              'flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all',
              mode === 'DATA'
                ? 'bg-white dark:bg-gray-700 text-blue-600 dark:text-blue-400 shadow-sm'
                : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
            ]"
          >
            <i class="pi pi-database text-xs"></i>
            <span class="hidden sm:inline">Data Query</span>
          </button>
        </div>
        <p class="text-xs text-gray-400 dark:text-gray-500 text-center mt-2">
          {{ mode === 'SEARCH' ? 'Find messages by meaning — great for conversations, topics, and agreements' : 'Ask data questions — the AI writes SQL to query your archive' }}
        </p>
      </div>

      <!-- Search Bar -->
      <div class="max-w-3xl mx-auto">
        <div class="relative">
          <i :class="[
            'absolute left-4 top-1/2 -translate-y-1/2 text-lg',
            mode === 'SEARCH' ? 'pi pi-search text-emerald-400' : 'pi pi-database text-blue-400'
          ]"></i>
          <input
            ref="searchInput"
            v-model="query"
            type="text"
            :placeholder="mode === 'SEARCH'
              ? 'That argument about the wedding · Vacation plans with wife · Bob agreed to pay me back...'
              : 'How many texts in 2024? · Who did I message most last month? · First text to Sarah...'"
            :class="[
              'w-full pl-12 pr-14 py-4 text-lg rounded-2xl border-2 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-4 shadow-lg transition-all',
              mode === 'SEARCH'
                ? 'border-gray-200 dark:border-gray-600 focus:border-emerald-500 dark:focus:border-emerald-400 focus:ring-emerald-500/10'
                : 'border-gray-200 dark:border-gray-600 focus:border-blue-500 dark:focus:border-blue-400 focus:ring-blue-500/10'
            ]"
            @keyup.enter="submitQuestion"
          />
          <button
            @click="submitQuestion"
            :disabled="!query.trim() || loading"
            :class="[
              'absolute right-2 top-1/2 -translate-y-1/2 p-2.5 rounded-xl text-white transition-all active:scale-95',
              mode === 'SEARCH'
                ? 'bg-emerald-500 hover:bg-emerald-600 disabled:bg-gray-300 dark:disabled:bg-gray-600'
                : 'bg-blue-500 hover:bg-blue-600 disabled:bg-gray-300 dark:disabled:bg-gray-600'
            ]"
          >
            <i :class="loading ? 'pi pi-spin pi-spinner' : 'pi pi-arrow-right'" class="text-lg"></i>
          </button>
        </div>

        <!-- Suggestion Chips -->
        <div v-if="!response && !loading" class="flex flex-wrap gap-2 mt-3 justify-center">
          <button
            v-for="chip in activeSuggestions"
            :key="chip"
            @click="query = chip; submitQuestion()"
            :class="[
              'px-3 py-1.5 rounded-full text-sm transition-all border',
              mode === 'SEARCH'
                ? 'bg-gray-100 dark:bg-gray-700 hover:bg-emerald-100 dark:hover:bg-emerald-900/30 text-gray-600 dark:text-gray-300 hover:text-emerald-700 dark:hover:text-emerald-300 border-gray-200 dark:border-gray-600 hover:border-emerald-300'
                : 'bg-gray-100 dark:bg-gray-700 hover:bg-blue-100 dark:hover:bg-blue-900/30 text-gray-600 dark:text-gray-300 hover:text-blue-700 dark:hover:text-blue-300 border-gray-200 dark:border-gray-600 hover:border-blue-300'
            ]"
          >
            {{ chip }}
          </button>
        </div>
      </div>
    </div>

    <!-- Results Area -->
    <div class="flex-1 overflow-y-auto px-4 pb-4 sm:px-6 sm:pb-6">
      <div class="max-w-3xl mx-auto space-y-4">

        <!-- Loading State -->
        <div v-if="loading" class="flex flex-col items-center py-12">
          <div class="relative">
            <div class="w-16 h-16 rounded-full bg-amber-100 dark:bg-amber-900/30 flex items-center justify-center animate-pulse">
              <i class="pi pi-sparkles text-2xl text-amber-500"></i>
            </div>
          </div>
          <p class="mt-4 text-sm text-gray-500 dark:text-gray-400 animate-pulse">Thinking...</p>
        </div>

        <!-- Error State -->
        <div v-if="error" class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl p-4">
          <p class="text-sm text-red-600 dark:text-red-400"><i class="pi pi-exclamation-circle mr-1"></i> {{ error }}</p>
        </div>

        <!-- AI Answer Panel (FACTUAL & ANALYTICS) -->
        <div v-if="response && response.answer" class="bg-gradient-to-br from-amber-50 to-orange-50 dark:from-amber-900/20 dark:to-orange-900/20 border border-amber-200 dark:border-amber-800 rounded-2xl p-5 shadow-md">
          <div class="flex items-start gap-3">
            <div class="w-8 h-8 rounded-full bg-amber-500 flex items-center justify-center shrink-0">
              <i class="pi pi-sparkles text-white text-sm"></i>
            </div>
            <div class="flex-1 min-w-0">
              <div class="prose prose-sm dark:prose-invert max-w-none text-gray-800 dark:text-gray-200" v-html="renderMarkdown(response.answer)"></div>
              <div class="flex items-center gap-3 mt-3 text-xs text-gray-400">
                <span class="flex items-center gap-1">
                  <i class="pi pi-tag"></i>
                  {{ response.intent }}
                </span>
                <span class="flex items-center gap-1">
                  <i class="pi pi-clock"></i>
                  {{ (response.processingTimeMs / 1000).toFixed(1) }}s
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Source Messages -->
        <div v-if="response && response.sources.length > 0">
          <h3 class="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-2 flex items-center gap-2">
            <i class="pi pi-comments text-xs"></i>
            Source Messages
          </h3>
          <div class="space-y-2">
            <div v-for="src in response.sources" :key="src.messageId"
              class="p-3 rounded-xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 hover:border-amber-300 dark:hover:border-amber-700 transition-colors cursor-pointer"
              @click="goToMessage(src.messageId, src.contactName)">
              <div class="flex items-center gap-2 mb-1">
                <span class="text-xs font-semibold text-blue-600 dark:text-blue-400">{{ src.contactName }}</span>
                <span class="text-[10px] text-gray-400">{{ formatDate(src.timestamp) }}</span>
                <span class="ml-auto text-[10px] text-gray-400">{{ (src.relevance * 100).toFixed(0) }}% match</span>
              </div>
              <p class="text-sm text-gray-700 dark:text-gray-300 line-clamp-2">{{ src.body }}</p>
            </div>
          </div>
        </div>

        <!-- Analytics Data (charts/tables) -->
        <div v-if="response && response.intent === 'ANALYTICS' && response.analyticsData">

          <!-- SQL Result Table (from text-to-SQL) -->
          <div v-if="response.analyticsData.type === 'sql_result' && response.analyticsData.rows?.length" class="mt-2">
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
              <div class="overflow-x-auto">
              <table class="w-full text-sm min-w-[400px]">
                <thead>
                  <tr class="bg-gray-50 dark:bg-gray-800">
                    <th v-for="col in response.analyticsData.columns" :key="col"
                      class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                      {{ col.replace(/_/g, ' ') }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, i) in response.analyticsData.rows" :key="i"
                    class="border-t border-gray-100 dark:border-gray-700 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors">
                    <td v-for="col in response.analyticsData.columns" :key="col"
                      class="px-4 py-2.5 text-gray-700 dark:text-gray-300">
                      {{ formatCell(row[col], col) }}
                    </td>
                  </tr>
                </tbody>
              </table>
              </div>
            </div>
            <p class="text-[10px] text-gray-400 dark:text-gray-500 mt-2 font-mono truncate"
               :title="response.analyticsData.sql">
              <i class="pi pi-database mr-1"></i>{{ response.analyticsData.sql }}
            </p>
          </div>

          <!-- Top Contacts Table (legacy fast-path) -->
          <div v-else-if="Array.isArray(response.analyticsData)" class="mt-2">
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
              <div v-for="(item, i) in response.analyticsData" :key="i"
                class="flex items-center gap-3 px-4 py-3 border-b border-gray-100 dark:border-gray-700 last:border-0 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors">
                <span class="text-lg font-bold text-gray-300 dark:text-gray-600 w-6 text-right">{{ i + 1 }}</span>
                <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center text-white text-sm font-bold">
                  {{ (item.displayName || '?').charAt(0).toUpperCase() }}
                </div>
                <span class="flex-1 font-medium text-sm text-gray-800 dark:text-gray-200">{{ item.displayName }}</span>
                <span class="text-sm text-gray-500 font-mono">{{ Number(item.messageCount).toLocaleString() }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Search Results (SEARCH intent) -->
        <div v-if="response && response.searchResults && response.searchResults.hits.length > 0">
          <h3 v-if="response.intent === 'SEARCH'" class="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-2 flex items-center gap-2">
            <i class="pi pi-search text-xs"></i>
            {{ response.searchResults.totalHits }} results
            <span class="text-xs font-normal text-gray-400">({{ response.searchResults.mode }})</span>
            <span class="ml-auto text-xs font-normal text-gray-400">{{ (response.processingTimeMs / 1000).toFixed(1) }}s</span>
          </h3>
          <div class="space-y-2">
            <div
              v-for="hit in response.searchResults.hits"
              :key="hit.message.id"
              class="p-3 rounded-xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 hover:border-emerald-300 dark:hover:border-emerald-700 transition-colors cursor-pointer"
              @click="goToMessage(hit.message.id, hit.message.contactName || hit.message.conversationName || hit.message.contactNumber)"
            >
              <div class="flex items-center gap-2 mb-1">
                <span class="text-xs font-semibold text-blue-600 dark:text-blue-400">{{ hit.message.contactName || hit.message.conversationName || hit.message.contactNumber }}</span>
                <span :class="[
                  'text-[10px] px-1.5 py-0.5 rounded-full font-medium',
                  hit.source === 'BOTH' ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300' :
                  hit.source === 'SEMANTIC' ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300' :
                  'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                ]">{{ hit.source }}</span>
                <span v-if="hit.moreFromConversation > 0"
                  class="text-[10px] px-1.5 py-0.5 rounded-full font-medium bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                  +{{ hit.moreFromConversation }} more
                </span>
                <span class="text-[10px] text-gray-400">{{ formatDate(hit.message.timestamp) }}</span>
                <span class="ml-auto text-[10px] text-gray-400">{{ formatScore(hit.score) }}</span>
              </div>
              <p class="text-sm text-gray-700 dark:text-gray-300 line-clamp-2">{{ hit.message.body }}</p>
            </div>
          </div>
        </div>

        <!-- No Results -->
        <div v-if="response && !loading && !response.answer && (!response.searchResults || response.searchResults.hits.length === 0)"
          class="text-center py-8 text-gray-400 dark:text-gray-500">
          <i class="pi pi-inbox text-3xl mb-2 block"></i>
          <p class="text-sm">No results found. Try rephrasing your question.</p>
        </div>
      </div>
    </div>

    <!-- Message Context Modal -->
    <Teleport to="body">
      <div v-if="contextModal.open" class="fixed inset-0 z-50 flex items-center justify-center p-4" @click.self="closeContext">
        <div class="absolute inset-0 bg-black/50 backdrop-blur-sm" @click="closeContext"></div>
        <div class="relative w-full max-w-2xl max-h-[85vh] bg-gray-50 dark:bg-gray-900 rounded-2xl shadow-2xl flex flex-col overflow-hidden">
          <!-- Modal Header -->
          <div class="shrink-0 flex items-center justify-between px-5 py-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
            <div class="flex items-center gap-2">
              <i class="pi pi-comments text-emerald-500"></i>
              <span class="font-semibold text-sm text-gray-800 dark:text-gray-200">Conversation Context</span>
              <span v-if="contextModal.contactName" class="text-xs text-gray-400">— {{ contextModal.contactName }}</span>
              <span v-if="contextIsGroupChat" class="text-[10px] px-1.5 py-0.5 rounded-full bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400 font-medium">Group</span>
            </div>
            <div class="flex items-center gap-2">
              <button @click="openInMessages" class="text-xs text-emerald-600 hover:text-emerald-700 dark:text-emerald-400 font-medium">
                <i class="pi pi-external-link mr-1"></i>Open in Messages
              </button>
              <button @click="closeContext" class="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-400 hover:text-gray-600 transition-colors">
                <i class="pi pi-times text-sm"></i>
              </button>
            </div>
          </div>

          <!-- Loading -->
          <div v-if="contextModal.loading" class="flex-1 flex items-center justify-center py-12">
            <i class="pi pi-spin pi-spinner text-2xl text-emerald-500"></i>
          </div>

          <!-- Messages -->
          <div v-else ref="contextScroll" class="flex-1 overflow-y-auto px-4 py-3 space-y-1">
            <MessageBubble
              v-for="msg in contextModal.messages"
              :key="msg.id"
              :message="msg"
              :is-group-chat="contextIsGroupChat"
              :participant-color-map="contextParticipantColorMap"
              :highlight-class="msg.id === contextModal.centerId ? 'ring-4 ring-emerald-400 dark:ring-emerald-500 shadow-2xl scale-[1.02]' : ''"
            />
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { marked } from 'marked';
import { askQuestion, getMessageContext, type QaResponse, type Message } from '../services/api';
import MessageBubble from '../components/MessageBubble.vue';

const router = useRouter();
const searchInput = ref<HTMLInputElement | null>(null);
const query = ref('');
const loading = ref(false);
const error = ref('');
const response = ref<QaResponse | null>(null);
const mode = ref<'SEARCH' | 'DATA'>('SEARCH');

marked.setOptions({ breaks: true, gfm: true });

function renderMarkdown(text: string): string {
  if (!text) return '';
  return marked.parse(text) as string;
}

const searchSuggestions = [
  'That argument about the wedding',
  'When we discussed vacation plans',
  'The time Bob said he\'d pay me back',
  'Conversation about moving to a new house',
  'When Alice talked about her new job',
];

const dataSuggestions = [
  'How many texts did I send in 2024?',
  'Who do I text the most?',
  'When did I first message Sarah?',
  'Which month had the most messages?',
  'How many photos did I send?',
];

const activeSuggestions = computed(() =>
  mode.value === 'SEARCH' ? searchSuggestions : dataSuggestions
);

function switchMode(newMode: 'SEARCH' | 'DATA') {
  mode.value = newMode;
  response.value = null;
  error.value = '';
  searchInput.value?.focus();
}

onMounted(() => {
  searchInput.value?.focus();
});

async function submitQuestion() {
  const q = query.value.trim();
  if (!q || loading.value) return;

  loading.value = true;
  error.value = '';
  response.value = null;

  try {
    response.value = await askQuestion({ question: q, mode: mode.value });
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || 'Failed to get answer';
  } finally {
    loading.value = false;
  }
}

const contextScroll = ref<HTMLElement | null>(null);
const contextModal = ref<{
  open: boolean;
  loading: boolean;
  centerId: number | null;
  contactName: string;
  messages: Message[];
  conversationId: number | null;
}>({
  open: false,
  loading: false,
  centerId: null,
  contactName: '',
  messages: [],
  conversationId: null,
});

const contextIsGroupChat = computed(() => {
  const msgs = contextModal.value.messages;
  if (!msgs.length) return false;
  const uniqueSenders = new Set(
    msgs.filter(m => m.senderContactId).map(m => m.senderContactId)
  );
  return uniqueSenders.size > 1;
});

const contextParticipantColorMap = ref(new Map<string, string>());

async function goToMessage(messageId: number, contactName?: string) {
  contextParticipantColorMap.value = new Map<string, string>();
  contextModal.value = {
    open: true,
    loading: true,
    centerId: messageId,
    contactName: contactName || '',
    messages: [],
    conversationId: null,
  };

  try {
    const ctx = await getMessageContext(messageId, 50, 50);
    const allMessages = [...ctx.before, ctx.center, ...ctx.after];
    contextModal.value.messages = allMessages;
    contextModal.value.conversationId = ctx.conversationId;
    contextModal.value.loading = false;

    // Scroll to the highlighted message after render
    await nextTick();
    const el = contextScroll.value?.querySelector(`[data-message-id="${messageId}"]`);
    if (el) {
      el.scrollIntoView({ block: 'center' });
    }
  } catch (e) {
    console.error('Failed to load message context', e);
    contextModal.value.loading = false;
  }
}

function closeContext() {
  contextModal.value.open = false;
}

function openInMessages() {
  if (contextModal.value.centerId) {
    router.push({ path: '/messages', query: { messageId: String(contextModal.value.centerId) } });
  }
  closeContext();
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function formatScore(score: number): string {
  return score >= 1 ? score.toFixed(1) : (score * 100).toFixed(0) + '%';
}

function formatCell(val: any, col?: string): string {
  if (val == null) return '—';
  if (typeof val === 'number') {
    const lowerCol = (col || '').toLowerCase();
    // Don't add commas to year/month/day columns or small ID-like values
    if (/\b(year|month|day|hour|minute|week)\b/.test(lowerCol)) {
      return String(val);
    }
    return Number.isInteger(val) ? val.toLocaleString() : val.toFixed(2);
  }
  // Truncate long strings (e.g. message bodies)
  const str = String(val);
  return str.length > 100 ? str.substring(0, 100) + '…' : str;
}
</script>
