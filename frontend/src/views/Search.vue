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
          <p class="text-blue-100 dark:text-blue-200">Search across all messages by text and filter by contact</p>
        </div>
        <div v-if="filteredResults.length" class="flex items-center gap-2">
          <div class="bg-white/10 backdrop-blur-sm rounded-lg px-4 py-2 border border-white/20">
            <div class="flex items-center gap-2">
              <i class="pi pi-check-circle text-lg"></i>
              <div class="text-left">
                <p class="text-xs text-blue-100">Results Found</p>
                <p class="text-2xl font-bold">{{ filteredResults.length }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Search Form -->
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-md p-6 border border-gray-200 dark:border-gray-700">
      <form
        class="grid gap-4 md:grid-cols-5 items-end"
        @submit.prevent="performSearch"
      >
        <div class="md:col-span-2 flex flex-col gap-2">
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
        <div class="flex flex-col gap-2">
          <label for="contactFilter" class="text-xs font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wide">
            <i class="pi pi-user text-xs mr-1"></i>
            Filter by Contact
          </label>
          <Select
            id="contactFilter"
            v-model="selectedContact"
            :options="contacts"
            filter
            :filterFields="['name', 'number']"
            placeholder="All contacts"
            class="w-full"
            :showClear="true"
          >
            <template #value="slotProps">
              <div v-if="slotProps.value">
                {{ slotProps.value.name || slotProps.value.number }}
              </div>
              <span v-else>
                {{ slotProps.placeholder }}
              </span>
            </template>
            <template #option="slotProps">
              <div>
                {{ slotProps.option.name || slotProps.option.number }}
              </div>
            </template>
          </Select>
        </div>
        <div class="flex gap-2 md:col-span-2">
          <button
            type="submit"
            :disabled="loading"
            class="flex-1 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white rounded-lg px-4 py-2.5 disabled:opacity-50 transition-all font-semibold shadow-sm hover:shadow-md flex items-center justify-center gap-2"
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
          <p class="text-gray-600 dark:text-gray-400">Try adjusting your search terms or contact filter</p>
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
        </div>

        <!-- Message body -->
        <div class="bg-gray-50 dark:bg-gray-900/50 rounded-lg p-4 border border-gray-200 dark:border-gray-700">
          <p class="text-sm text-gray-800 dark:text-gray-200 leading-relaxed break-words whitespace-pre-wrap">{{ m.body }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import Select from 'primevue/select';
import {
  getDistinctContacts,
  searchByText,
  type Contact,
  type Message,
} from '../services/api';

const contacts = ref<Contact[]>([]);
const searchText = ref('');
const selectedContact = ref<Contact | null>(null);

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
  selectedContact.value = null;
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
  if (!selectedContact.value) return results.value;
  const c = selectedContact.value;
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


