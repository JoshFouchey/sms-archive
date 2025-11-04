<template>
  <div class="p-4 md:p-6 max-w-5xl mx-auto bg-white dark:bg-gray-800 rounded-xl shadow space-y-4 md:space-y-6">
    <h1 class="text-xl md:text-2xl font-bold">Search Messages</h1>
    <p class="text-sm md:text-base text-gray-600 dark:text-gray-400">
      Search across all messages by text and optionally filter by a contact.
    </p>

    <!-- Search Form -->
    <form
      class="grid gap-3 md:gap-4 md:grid-cols-4 items-end"
      @submit.prevent="performSearch"
    >
      <div class="md:col-span-2 flex flex-col gap-1">
        <label for="searchText" class="text-sm font-medium">Text contains</label>
        <input
          id="searchText"
          v-model="searchText"
          type="text"
          placeholder="Enter text..."
          class="rounded border px-3 py-2.5 md:py-2 text-base dark:bg-gray-700 dark:border-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
          @keyup.enter="performSearch"
        />
      </div>
      <div class="flex flex-col gap-1">
        <label for="contactFilter" class="text-sm font-medium">Filter by contact</label>
        <select
          id="contactFilter"
          v-model.number="selectedContactId"
          class="rounded border px-3 py-2.5 md:py-2 text-base dark:bg-gray-700 dark:border-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option :value="null">All contacts</option>
          <option v-for="c in contacts" :key="c.id" :value="c.id">
            {{ c.name ? c.name : c.number }}
          </option>
        </select>
      </div>
      <div class="flex gap-2">
        <button
          type="submit"
          :disabled="loading"
          class="flex-1 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white rounded px-4 py-2.5 md:py-2 disabled:opacity-50 transition-colors font-medium"
        >
          {{ loading ? 'Searching...' : 'Search' }}
        </button>
        <button
          type="button"
          @click="clearSearch"
          class="bg-gray-200 dark:bg-gray-600 hover:bg-gray-300 dark:hover:bg-gray-500 active:bg-gray-400 dark:active:bg-gray-400 text-gray-800 dark:text-gray-100 rounded px-4 py-2.5 md:py-2 transition-colors font-medium"
        >
          Clear
        </button>
      </div>
    </form>

    <!-- States -->
    <div v-if="error" class="text-sm text-red-600 dark:text-red-400">
      {{ error }}
    </div>
    <div
      v-else-if="touched && !loading && !filteredResults.length"
      class="text-sm text-gray-600 dark:text-gray-400"
    >
      No results found.
    </div>

    <!-- Results Count -->
    <div v-if="filteredResults.length" class="text-sm text-gray-600 dark:text-gray-400">
      Found {{ filteredResults.length }} result{{ filteredResults.length === 1 ? '' : 's' }}
    </div>

    <!-- Results Cards -->
    <div
      v-if="filteredResults.length"
      class="space-y-3"
    >
      <div
        v-for="m in filteredResults"
        :key="m.id"
        class="border dark:border-gray-700 rounded-lg p-4 bg-white dark:bg-gray-800 hover:shadow-md hover:border-gray-300 dark:hover:border-gray-600 transition-all"
      >
        <!-- Header row: Contact name and timestamp -->
        <div class="flex justify-between items-start gap-3 mb-2">
          <div class="flex-1 min-w-0">
            <h3 class="font-semibold text-base text-gray-900 dark:text-gray-100 truncate">
              {{ m.contactName || m.contactNumber || 'Unknown' }}
            </h3>
          </div>
          <div class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">
            {{ formatDateTime(m.timestamp) }}
          </div>
        </div>

        <!-- Direction badge -->
        <div class="mb-3">
          <span
            :class="{
              'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300': m.direction === 'INBOUND',
              'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300': m.direction === 'OUTBOUND'
            }"
            class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium"
          >
            <span>{{ m.direction === 'INBOUND' ? '←' : '→' }}</span>
            <span>{{ m.direction === 'INBOUND' ? 'Received' : 'Sent' }}</span>
          </span>
        </div>

        <!-- Message body -->
        <div class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed break-words">
          {{ m.body }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import {
  getDistinctContacts,
  searchByText,
  type Contact,
  type Message,
} from '../services/api';

const contacts = ref<Contact[]>([]);
const searchText = ref('');
const selectedContactId = ref<number | null>(null);

const results = ref<Message[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const touched = ref(false); // whether search attempted

onMounted(async () => {
  try {
    contacts.value = await getDistinctContacts();
  } catch (e: any) {
    // Non-blocking; show console only
    console.error('Failed to load contacts', e);
  }
});

async function performSearch() {
  touched.value = true;
  error.value = null;
  if (!searchText.value.trim()) {
    results.value = [];
    return; // don't query empty
  }
  loading.value = true;
  try {
    results.value = await searchByText(searchText.value.trim());
  } catch (e: any) {
    error.value = e?.message || 'Search failed';
  } finally {
    loading.value = false;
  }
}

function clearSearch() {
  searchText.value = '';
  selectedContactId.value = null;
  results.value = [];
  error.value = null;
  touched.value = false;
}

function matchContact(msg: Message, c: Contact): boolean {
  // Try name match if available
  if (c.name && msg.contactName && msg.contactName.toLowerCase() === c.name.toLowerCase())
    return true;
  // Fallback to number match
  if (msg.contactNumber && msg.contactNumber === c.number) return true;
  if (msg.contactNormalizedNumber && msg.contactNormalizedNumber === c.normalizedNumber)
    return true;
  return false;
}

const filteredResults = computed(() => {
  if (!selectedContactId.value) return results.value;
  const c = contacts.value.find((c) => c.id === selectedContactId.value);
  if (!c) return [];
  return results.value.filter((m) => matchContact(m, c));
});

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
</script>


