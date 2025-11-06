<template>
  <div class="flex flex-col gap-6">
    <header class="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
      <h1 class="text-2xl font-semibold tracking-tight text-gray-800 dark:text-gray-100">Contacts</h1>
      <div class="flex items-center gap-2 w-full md:w-auto">
        <input
          v-model="filter"
          type="text"
          placeholder="Filter by name or number..."
          class="flex-1 md:flex-none px-3 py-2 rounded-lg border border-gray-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm focus:outline-none focus:ring-2 focus:ring-accent/60"
        />
        <button
          v-if="filter"
          @click="filter = ''"
          class="px-3 py-2 rounded-lg bg-gray-200 dark:bg-slate-700 hover:bg-gray-300 dark:hover:bg-slate-600 text-sm font-medium"
        >Clear</button>
      </div>
    </header>

    <section>
      <div v-if="loading" class="text-sm text-gray-500 dark:text-gray-400">Loading contacts...</div>
      <div v-else-if="error" class="text-sm text-red-600 dark:text-red-400">{{ error }}</div>
      <div v-else-if="!filteredContacts.length" class="text-sm text-gray-500 dark:text-gray-400">No contacts found.</div>

      <div v-else class="grid gap-4 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
        <div
          v-for="c in filteredContacts"
          :key="c.id"
          class="group relative rounded-xl border border-gray-200 dark:border-slate-700 bg-white/90 dark:bg-slate-800/80 backdrop-blur p-4 flex flex-col shadow-sm hover:shadow-md transition"
        >
          <div class="flex items-start justify-between gap-2 mb-2">
            <div class="flex-1 min-w-0">
              <p class="text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">Name</p>
              <div v-if="!isEditing(c.id)" class="flex items-center gap-2">
                <span class="font-medium text-sm truncate" :class="c.name ? 'text-gray-800 dark:text-gray-100' : 'italic text-gray-400 dark:text-gray-500'">
                  {{ c.name || 'Unnamed' }}
                </span>
                <button
                  @click="startEdit(c)"
                  class="opacity-60 group-hover:opacity-100 p-1 rounded hover:bg-gray-100 dark:hover:bg-slate-700 active:scale-95"
                  aria-label="Edit name"
                  title="Edit name"
                >
                  <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16.862 4.487l2.651 2.651M7.5 17.25l9.362-9.362M5.25 19.5h13.5" />
                  </svg>
                </button>
              </div>
              <div v-else class="flex items-center gap-2">
                <input
                  ref="editInputEl"
                  v-model="editName"
                  type="text"
                  class="flex-1 px-2 py-1 text-sm rounded border border-gray-300 dark:border-slate-600 bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-accent/60"
                  :placeholder="originalName || 'Enter name'"
                  @keyup.enter="saveEdit(c)"
                  @keyup.esc="cancelEdit()"
                />
                <button
                  @click="saveEdit(c)"
                  :disabled="saving"
                  class="px-2 py-1 rounded bg-green-600 hover:bg-green-700 disabled:bg-green-400 text-white text-xs font-medium flex items-center gap-1"
                  aria-label="Save"
                >
                  <span v-if="!saving">Save</span>
                  <span v-else class="flex items-center gap-1">
                    <svg class="animate-spin w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <circle cx="12" cy="12" r="10" stroke-width="4" class="opacity-25"></circle>
                      <path d="M4 12a8 8 0 0 1 8-8" stroke-width="4" class="opacity-75" stroke-linecap="round"></path>
                    </svg>
                    ...
                  </span>
                </button>
                <button
                  @click="cancelEdit()"
                  :disabled="saving"
                  class="px-2 py-1 rounded bg-gray-200 dark:bg-slate-700 hover:bg-gray-300 dark:hover:bg-slate-600 text-gray-700 dark:text-gray-100 text-xs font-medium"
                  aria-label="Cancel"
                >Cancel</button>
              </div>
            </div>
          </div>

          <div class="mt-auto space-y-1">
            <p class="text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">Number</p>
            <p class="text-sm font-mono text-gray-700 dark:text-gray-200 break-all">{{ c.number }}</p>
            <p class="text-[10px] text-gray-400 dark:text-gray-500">Normalized: {{ c.normalizedNumber }}</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue';
import { getDistinctContacts, updateContactName, type Contact } from '../services/api';
import { useToast } from 'primevue/usetoast';

const contacts = ref<Contact[]>([]);
const loading = ref(true);
const error = ref('');
const filter = ref('');

// Editing state
const editingId = ref<number | null>(null);
const editName = ref('');
const originalName = ref<string | null>(null);
const saving = ref(false);
const toast = useToast();
const editInputEl = ref<HTMLInputElement | null>(null);

onMounted(async () => {
  try {
    contacts.value = await getDistinctContacts();
  } catch (e: any) {
    error.value = e?.message || 'Failed to load contacts';
  } finally {
    loading.value = false;
  }
});

const filteredContacts = computed(() => {
  const f = filter.value.trim().toLowerCase();
  if (!f) return contacts.value;
  return contacts.value.filter(c => {
    return (c.name && c.name.toLowerCase().includes(f)) || c.number.toLowerCase().includes(f) || c.normalizedNumber.toLowerCase().includes(f);
  });
});

function isEditing(id: number) { return editingId.value === id; }

function startEdit(c: Contact) {
  editingId.value = c.id;
  originalName.value = c.name;
  editName.value = c.name ?? '';
  nextTick(() => { editInputEl.value?.focus(); editInputEl.value?.select(); });
}
function cancelEdit() {
  editingId.value = null;
  editName.value = '';
  originalName.value = null;
}
async function saveEdit(c: Contact) {
  if (!isEditing(c.id)) return;
  const trimmed = editName.value.trim();
  // If unchanged
  if ((trimmed || null) === (originalName.value || null)) {
    cancelEdit();
    return;
  }
  saving.value = true;
  try {
    const updated = await updateContactName(c.id, trimmed.length ? trimmed : null);
    // Optimistically update local list
    contacts.value = contacts.value.map(ct => ct.id === c.id ? { ...ct, name: updated.name } : ct);
    toast.add({ severity: 'success', summary: 'Updated', detail: 'Contact name saved.', life: 2500 });
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Update Failed', detail: e?.message || 'Server error', life: 3500 });
  } finally {
    saving.value = false;
    cancelEdit();
  }
}
</script>

<style scoped>
/* Responsive tweaks if needed */
</style>
