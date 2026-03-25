<template>
  <div class="h-full flex flex-col">
    <!-- Header with search bar -->
    <div class="shrink-0 px-6 pt-6 pb-4">
      <div class="max-w-3xl mx-auto text-center mb-6">
        <h1 class="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-1 flex items-center justify-center gap-2">
          <i class="pi pi-sparkles text-amber-500"></i>
          Ask
        </h1>
        <p class="text-sm text-gray-500 dark:text-gray-400">
          Ask anything about your messages — facts, search, or stats
        </p>
      </div>

      <!-- Search Bar -->
      <div class="max-w-3xl mx-auto">
        <div class="relative">
          <i class="pi pi-search absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 text-lg"></i>
          <input
            ref="searchInput"
            v-model="query"
            type="text"
            placeholder="What car does John drive? · Who do I text the most? · camping trip..."
            class="w-full pl-12 pr-14 py-4 text-lg rounded-2xl border-2 border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:border-amber-500 dark:focus:border-amber-400 focus:ring-4 focus:ring-amber-500/10 shadow-lg transition-all"
            @keyup.enter="submitQuestion"
          />
          <button
            @click="submitQuestion"
            :disabled="!query.trim() || loading"
            class="absolute right-2 top-1/2 -translate-y-1/2 p-2.5 rounded-xl bg-amber-500 hover:bg-amber-600 disabled:bg-gray-300 dark:disabled:bg-gray-600 text-white transition-all active:scale-95"
          >
            <i :class="loading ? 'pi pi-spin pi-spinner' : 'pi pi-arrow-right'" class="text-lg"></i>
          </button>
        </div>

        <!-- Suggestion Chips -->
        <div v-if="!response && !loading" class="flex flex-wrap gap-2 mt-3 justify-center">
          <button
            v-for="chip in suggestions"
            :key="chip"
            @click="query = chip; submitQuestion()"
            class="px-3 py-1.5 rounded-full bg-gray-100 dark:bg-gray-700 hover:bg-amber-100 dark:hover:bg-amber-900/30 text-sm text-gray-600 dark:text-gray-300 hover:text-amber-700 dark:hover:text-amber-300 transition-all border border-gray-200 dark:border-gray-600 hover:border-amber-300"
          >
            {{ chip }}
          </button>
        </div>
      </div>
    </div>

    <!-- Results Area -->
    <div class="flex-1 overflow-y-auto px-6 pb-6">
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
              <div class="prose prose-sm dark:prose-invert max-w-none text-gray-800 dark:text-gray-200 whitespace-pre-line">{{ response.answer }}</div>
              <div class="flex items-center gap-3 mt-3 text-xs text-gray-400">
                <span class="flex items-center gap-1">
                  <i class="pi pi-tag"></i>
                  {{ response.intent }}
                </span>
                <span class="flex items-center gap-1">
                  <i class="pi pi-clock"></i>
                  {{ (response.processingTimeMs / 1000).toFixed(1) }}s
                </span>
                <span v-if="response.kgFacts.length" class="flex items-center gap-1">
                  <i class="pi pi-link"></i>
                  {{ response.kgFacts.length }} facts used
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- KG Facts Used (collapsible) -->
        <div v-if="response && response.kgFacts.length > 0">
          <button @click="showFacts = !showFacts" class="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400 hover:text-amber-600 transition-colors">
            <i :class="showFacts ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" class="text-xs"></i>
            <i class="pi pi-sitemap text-xs"></i>
            {{ response.kgFacts.length }} Knowledge Graph facts
          </button>
          <div v-if="showFacts" class="mt-2 space-y-1.5 ml-4">
            <div v-for="fact in response.kgFacts" :key="fact.id"
              class="text-sm p-2 rounded-lg bg-gray-50 dark:bg-gray-700/50 flex items-center gap-2">
              <span class="font-medium text-blue-600 dark:text-blue-400">{{ fact.subjectName }}</span>
              <span class="text-gray-400 font-mono text-xs">{{ fact.predicate.replace(/_/g, ' ') }}</span>
              <span class="font-medium text-purple-600 dark:text-purple-400">{{ fact.objectName || fact.objectValue || '—' }}</span>
              <span class="ml-auto text-[10px] text-gray-400">{{ (fact.confidence * 100).toFixed(0) }}%</span>
            </div>
          </div>
        </div>

        <!-- Source Messages (FACTUAL) -->
        <div v-if="response && response.sources.length > 0">
          <h3 class="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-2 flex items-center gap-2">
            <i class="pi pi-comments text-xs"></i>
            Source Messages
          </h3>
          <div class="space-y-2">
            <div v-for="src in response.sources" :key="src.messageId"
              class="p-3 rounded-xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 hover:border-amber-300 dark:hover:border-amber-700 transition-colors cursor-pointer"
              @click="goToMessage(src.messageId)">
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
              <table class="w-full text-sm">
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
                      {{ formatCell(row[col]) }}
                    </td>
                  </tr>
                </tbody>
              </table>
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
              class="p-3 rounded-xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 hover:border-blue-300 dark:hover:border-blue-700 transition-colors cursor-pointer"
              @click="goToMessage(hit.message.id)"
            >
              <div class="flex items-center gap-2 mb-1">
                <span class="text-xs font-semibold text-blue-600 dark:text-blue-400">{{ hit.message.contactName || hit.message.contactNumber }}</span>
                <span :class="[
                  'text-[10px] px-1.5 py-0.5 rounded-full font-medium',
                  hit.source === 'BOTH' ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300' :
                  hit.source === 'SEMANTIC' ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300' :
                  'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                ]">{{ hit.source }}</span>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { askQuestion, type QaResponse } from '../services/api';

const router = useRouter();
const searchInput = ref<HTMLInputElement | null>(null);
const query = ref('');
const loading = ref(false);
const error = ref('');
const response = ref<QaResponse | null>(null);
const showFacts = ref(false);

const suggestions = [
  'Who do I text the most?',
  'How many messages do I have?',
  'When did I first text John?',
  'How many photos did I send in 2024?',
  'Which month had the most messages?',
];

onMounted(() => {
  searchInput.value?.focus();
});

async function submitQuestion() {
  const q = query.value.trim();
  if (!q || loading.value) return;

  loading.value = true;
  error.value = '';
  response.value = null;
  showFacts.value = false;

  try {
    response.value = await askQuestion({ question: q });
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || 'Failed to get answer';
  } finally {
    loading.value = false;
  }
}

function goToMessage(messageId: number) {
  router.push({ path: '/messages', query: { messageId: String(messageId) } });
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function formatScore(score: number): string {
  return score >= 1 ? score.toFixed(1) : (score * 100).toFixed(0) + '%';
}

function formatCell(val: any): string {
  if (val == null) return '—';
  if (typeof val === 'number') {
    return Number.isInteger(val) ? val.toLocaleString() : val.toFixed(2);
  }
  // Truncate long strings (e.g. message bodies)
  const str = String(val);
  return str.length > 100 ? str.substring(0, 100) + '…' : str;
}
</script>
