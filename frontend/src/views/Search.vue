<template>
  <div class="p-6 max-w-5xl mx-auto bg-white dark:bg-gray-800 rounded-xl shadow space-y-6">
    <h1 class="text-2xl font-bold">Search Messages</h1>
    <p class="text-gray-600 dark:text-gray-400">
      Search across all messages by text and optionally filter by a contact.
    </p>

    <!-- Search Form -->
    <form
      class="grid gap-4 md:grid-cols-4 items-end"
      @submit.prevent="performSearch"
    >
      <div class="md:col-span-2 flex flex-col gap-1">
        <label for="searchText" class="text-sm font-medium">Text contains</label>
        <input
          id="searchText"
          v-model="searchText"
          type="text"
          placeholder="Enter text..."
          class="rounded border px-3 py-2 dark:bg-gray-700 dark:border-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
          @keyup.enter="performSearch"
        />
      </div>
      <div class="flex flex-col gap-1">
        <label for="contactFilter" class="text-sm font-medium">Filter by contact</label>
        <select
          id="contactFilter"
          v-model.number="selectedContactId"
          class="rounded border px-3 py-2 dark:bg-gray-700 dark:border-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
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
          class="flex-1 bg-blue-600 hover:bg-blue-700 text-white rounded px-4 py-2 disabled:opacity-50"
        >
          {{ loading ? 'Searching...' : 'Search' }}
        </button>
        <button
          type="button"
          @click="clearSearch"
          class="bg-gray-200 dark:bg-gray-600 hover:bg-gray-300 dark:hover:bg-gray-500 text-gray-800 dark:text-gray-100 rounded px-4 py-2"
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

    <!-- Results Table -->
    <div
      v-if="filteredResults.length"
      class="overflow-auto border rounded dark:border-gray-700"
    >
      <table class="min-w-full text-sm">
        <thead class="bg-gray-100 dark:bg-gray-700 text-left">
          <tr>
            <th class="px-3 py-2 whitespace-nowrap">Timestamp</th>
            <th class="px-3 py-2 whitespace-nowrap">Direction</th>
            <th class="px-3 py-2 whitespace-nowrap">Contact</th>
            <th class="px-3 py-2">Body</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="m in filteredResults"
            :key="m.id"
            class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700"
          >
            <td class="px-3 py-2 align-top whitespace-nowrap">
              {{ formatDateTime(m.timestamp) }}
            </td>
            <td class="px-3 py-2 align-top">
              <span
                :class="{
                  'text-blue-600 dark:text-blue-400': m.direction === 'INBOUND',
                  'text-green-600 dark:text-green-400': m.direction === 'OUTBOUND'
                }"
              >
                {{ m.direction === 'INBOUND' ? '← Received' : '→ Sent' }}
              </span>
            </td>
            <td class="px-3 py-2 align-top">
              {{ m.contactName || m.contactNumber || '—' }}
            </td>
            <td class="px-3 py-2 align-top max-w-xs">
              <div class="truncate" :title="m.body">{{ m.body }}</div>
            </td>
          </tr>
        </tbody>
      </table>
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

<style scoped>
/* Basic table adjustments */
table {
  border-collapse: collapse;
}
th {
  font-weight: 600;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
tbody tr:nth-child(even) {
  background-color: rgba(0, 0, 0, 0.02);
}
</style>
