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
          <p class="text-blue-100 dark:text-blue-200">Search across all conversations and messages</p>
        </div>
        <div v-if="filteredResults.length" class="flex items-center gap-2">
          <div class="bg-white/10 backdrop-blur-sm rounded-lg px-4 py-2 border border-white/20">
            <div class="flex items-center gap-2">
              <i class="pi pi-check-circle text-lg"></i>
              <div class="text-left">
                <p class="text-xs text-blue-100">Results Found</p>
                <p class="text-2xl font-bold">{{ totalResults.toLocaleString() }}</p>
                <p class="text-xs text-blue-100">Showing {{ filteredResults.length }}</p>
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
          <label for="searchText" class="text-xs font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wide">
            <i class="pi pi-search text-xs mr-1"></i>
            Search Text
          </label>
          <input
            id="searchText"
            v-model="searchText"
            type="text"
            placeholder="Enter text to search..."
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
          <p class="text-gray-600 dark:text-gray-400">Try adjusting your search terms</p>
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
          <p class="text-gray-600 dark:text-gray-400">Enter text above and click Search to find messages</p>
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
            {{ (m.contactName || m.contactNumber || 'U').charAt(0).toUpperCase() }}
          </div>
          <div class="flex-1 min-w-0">
            <h3 class="font-bold text-lg text-gray-900 dark:text-gray-100 truncate">
              {{ m.contactName || m.contactNumber || 'Unknown' }}
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
      
      <!-- Load More Button -->
      <div v-if="hasMore" class="flex justify-center">
        <button
          @click="loadMore"
          :disabled="loading"
          class="bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white rounded-lg px-6 py-3 disabled:opacity-50 transition-all font-semibold shadow-md hover:shadow-lg flex items-center justify-center gap-2"
        >
          <i v-if="!loading" class="pi pi-angle-down"></i>
          <i v-else class="pi pi-spin pi-spinner"></i>
          <span>{{ loading ? 'Loading...' : 'Load More Results' }}</span>
        </button>
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
              <div v-for="b in beforeChrono" :key="b.id" class="flex flex-col items-start">
                <div
                  :class="[
                    'relative max-w-[90%] rounded-2xl px-4 py-2.5 shadow-md text-sm leading-relaxed space-y-1 transition-all',
                    b.direction === 'OUTBOUND'
                      ? 'bg-gradient-to-br from-blue-600 to-cyan-500 text-white'
                      : 'bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 border border-gray-200 dark:border-slate-700'
                  ]"
                >
                  <div v-if="b.body">{{ b.body }}</div>
                </div>
                <span class="mt-1.5 text-[10px] tracking-wide uppercase px-1" :class="b.direction === 'OUTBOUND' ? 'text-blue-600 dark:text-blue-400 self-end' : 'text-gray-400 dark:text-gray-500'">
                  {{ formatDateTime(b.timestamp) }}
                </span>
              </div>
            </div>

            <!-- Center message (highlight) -->
            <div class="flex flex-col items-start">
              <div
                :class="[
                  'relative max-w-[92%] rounded-2xl px-4 py-2.5 shadow-md text-sm leading-relaxed space-y-1 transition-all border-2',
                  contextData.center.direction === 'OUTBOUND'
                    ? 'bg-gradient-to-br from-blue-600 to-cyan-500 text-white border-blue-300'
                    : 'bg-blue-50 dark:bg-blue-900/20 text-gray-900 dark:text-gray-100 border-blue-300'
                ]"
              >
                <div class="flex items-center gap-2 text-xs">
                  <span class="text-blue-700 dark:text-blue-300 font-semibold">Search hit</span>
                  <span class="flex items-center gap-1 text-blue-700 dark:text-blue-300"><i class="pi pi-clock text-[10px]"></i>{{ formatDateTime(contextData.center.timestamp) }}</span>
                </div>
                <div v-if="contextData.center.body" class="mt-1">{{ contextData.center.body }}</div>
              </div>
            </div>

            <!-- After messages (newer than center) -->
            <div v-if="contextData.after.length" class="space-y-2">
              <div class="text-[10px] uppercase tracking-wide text-gray-500 dark:text-gray-400">Later</div>
              <div v-for="a in contextData.after" :key="a.id" class="flex flex-col items-start">
                <div
                  :class="[
                    'relative max-w-[90%] rounded-2xl px-4 py-2.5 shadow-md text-sm leading-relaxed space-y-1 transition-all',
                    a.direction === 'OUTBOUND'
                      ? 'bg-gradient-to-br from-blue-600 to-cyan-500 text-white'
                      : 'bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 border border-gray-200 dark:border-slate-700'
                  ]"
                >
                  <div v-if="a.body">{{ a.body }}</div>
                </div>
                <span class="mt-1.5 text-[10px] tracking-wide uppercase px-1" :class="a.direction === 'OUTBOUND' ? 'text-blue-600 dark:text-blue-400 self-end' : 'text-gray-400 dark:text-gray-500'">
                  {{ formatDateTime(a.timestamp) }}
                </span>
              </div>
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
              :to="{ name: 'messages', params: { id: contextData.conversationId } }"
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
  searchByText,
  getMessageContext,
  type Message,
  type MessageContext,
} from '../services/api';

const searchText = ref('');

const results = ref<Message[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const touched = ref(false); // whether search attempted
const currentPage = ref(0);
const hasMore = ref(false);
const totalResults = ref(0);

// Context modal state
const contextOpen = ref(false);
const contextLoading = ref(false);
const contextError = ref<string | null>(null);
const contextData = ref<MessageContext | null>(null);

async function performSearch() {
  touched.value = true;
  error.value = null;
  if (!searchText.value.trim()) {
    results.value = [];
    return; // don't query empty
  }
  loading.value = true;
  currentPage.value = 0;
  results.value = []; // Clear previous results
  try {
    const response = await searchByText(searchText.value.trim(), null, 0, 50);
    results.value = response.content;
    hasMore.value = !response.last;
    totalResults.value = response.totalElements;
  } catch (e: any) {
    error.value = e?.message || 'Search failed';
  } finally {
    loading.value = false;
  }
}

async function loadMore() {
  if (!hasMore.value || loading.value) return;
  loading.value = true;
  try {
    const response = await searchByText(searchText.value.trim(), null, currentPage.value + 1, 50);
    results.value = [...results.value, ...response.content];
    currentPage.value++;
    hasMore.value = !response.last;
  } catch (e: any) {
    error.value = e?.message || 'Failed to load more results';
  } finally {
    loading.value = false;
  }
}

function clearSearch() {
  searchText.value = '';
  results.value = [];
  error.value = null;
  touched.value = false;
  currentPage.value = 0;
  hasMore.value = false;
  totalResults.value = 0;
}

// Filtering is now done on backend, so filteredResults just returns results
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
    second: '2-digit',
  });
}

const beforeChrono = computed(() => {
  // API returns "before" newest-first; reverse for chronological display
  if (!contextData.value) return [] as Message[];
  return [...contextData.value.before].reverse();
});

async function openContext(m: Message) {
  contextOpen.value = true;
  contextLoading.value = true;
  contextError.value = null;
  contextData.value = null;
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
