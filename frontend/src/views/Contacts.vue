<template>
  <div class="space-y-6">
    <!-- Header Section -->
    <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 rounded-2xl shadow-lg p-6 text-white">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 class="text-4xl font-bold mb-2 flex items-center gap-3">
            <i class="pi pi-users"></i>
            Contacts
          </h1>
          <p class="text-blue-100 dark:text-blue-200">Manage and edit your contact names</p>
        </div>
        <div class="flex items-center gap-2">
          <div class="bg-white/10 backdrop-blur-sm rounded-lg px-4 py-2 border border-white/20">
            <div class="flex items-center gap-2">
              <i class="pi pi-user text-lg"></i>
              <div class="text-left">
                <p class="text-xs text-blue-100">Total Contacts</p>
                <p class="text-2xl font-bold">{{ contacts.length }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Filter Section -->
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 border border-gray-200 dark:border-gray-700">
      <div class="flex items-center gap-3">
        <div class="flex-1">
          <label class="text-xs font-semibold mb-2 text-gray-700 dark:text-gray-300 uppercase tracking-wide block">
            <i class="pi pi-search text-xs mr-1"></i>
            Filter Contacts
          </label>
          <input
            v-model="filter"
            type="text"
            placeholder="Search by name or number..."
            class="w-full px-4 py-2.5 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all"
          />
        </div>
        <div class="flex items-end">
          <button
            v-if="filter"
            @click="filter = ''"
            class="px-4 py-2.5 rounded-lg bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-sm font-semibold transition-all shadow-sm hover:shadow-md"
          >
            <i class="pi pi-times mr-1"></i>
            Clear
          </button>
        </div>
      </div>
      <div v-if="filter" class="mt-3 text-sm text-gray-600 dark:text-gray-400">
        <span class="font-medium">{{ filteredContacts.length }}</span> contact{{ filteredContacts.length === 1 ? '' : 's' }} found
      </div>
    </div>

    <section>
      <div v-if="loading" class="flex flex-col items-center justify-center py-12 bg-white dark:bg-gray-800 rounded-xl shadow-md border border-gray-200 dark:border-gray-700">
        <i class="pi pi-spin pi-spinner text-4xl text-blue-600 dark:text-blue-400 mb-3"></i>
        <span class="text-sm text-gray-600 dark:text-gray-400 font-medium">Loading contacts...</span>
      </div>
      <div v-else-if="error" class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl p-6 text-center shadow-md">
        <i class="pi pi-exclamation-circle text-3xl text-red-600 dark:text-red-400 mb-2"></i>
        <p class="text-sm text-red-600 dark:text-red-400">{{ error }}</p>
      </div>
      <div v-else-if="!filteredContacts.length" class="bg-white dark:bg-gray-800 rounded-xl shadow-md p-8 border border-gray-200 dark:border-gray-700 text-center">
        <div class="flex flex-col items-center gap-4">
          <div class="bg-blue-100 dark:bg-blue-900/30 p-4 rounded-full">
            <i class="pi pi-users text-4xl text-blue-600 dark:text-blue-400"></i>
          </div>
          <div>
            <h3 class="text-xl font-semibold text-gray-800 dark:text-gray-200 mb-2">No Contacts Found</h3>
            <p class="text-gray-600 dark:text-gray-400">{{ filter ? 'Try adjusting your search filter' : 'Import some messages to see contacts here' }}</p>
          </div>
        </div>
      </div>

      <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        <div
          v-for="c in filteredContacts"
          :key="c.id"
          class="group relative rounded-xl border border-gray-200 dark:border-slate-700 bg-white dark:bg-slate-800 backdrop-blur p-5 flex flex-col shadow-md hover:shadow-xl transition-all duration-300 hover:scale-[1.02]"
        >
          <!-- Avatar Circle -->
          <div class="flex items-start gap-3 mb-4">
            <div class="w-12 h-12 rounded-full bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center text-white text-xl font-bold shrink-0 shadow-md">
              {{ (c.name || c.number).charAt(0).toUpperCase() }}
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-xs uppercase tracking-wider text-gray-500 dark:text-gray-400 font-semibold mb-1">Contact Name</p>
              <div v-if="!isEditing(c.id)" class="flex items-center gap-2">
                <span class="font-semibold text-base truncate" :class="c.name ? 'text-gray-900 dark:text-gray-100' : 'italic text-gray-400 dark:text-gray-500'">
                  {{ c.name || 'Unnamed' }}
                </span>
                <button
                  @click="startEdit(c)"
                  class="opacity-0 group-hover:opacity-100 p-1.5 rounded-lg hover:bg-blue-100 dark:hover:bg-blue-900/30 text-blue-600 dark:text-blue-400 active:scale-95 transition-all"
                  aria-label="Edit name"
                  title="Edit name"
                >
                  <i class="pi pi-pencil text-sm"></i>
                </button>
              </div>
              <div v-else class="flex flex-col gap-2">
                <input
                  ref="editInputEl"
                  v-model="editName"
                  type="text"
                  class="px-3 py-2 text-sm rounded-lg border-2 border-blue-500 dark:border-blue-400 bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 shadow-sm"
                  :placeholder="originalName || 'Enter name'"
                  @keyup.enter="saveEdit(c)"
                  @keyup.esc="cancelEdit()"
                />
                <div class="flex gap-2">
                  <button
                    @click="saveEdit(c)"
                    :disabled="saving"
                    class="flex-1 px-3 py-1.5 rounded-lg bg-green-600 hover:bg-green-700 disabled:bg-green-400 text-white text-xs font-semibold flex items-center justify-center gap-1.5 shadow-sm active:scale-95 transition-all"
                    aria-label="Save"
                  >
                    <i v-if="!saving" class="pi pi-check"></i>
                    <i v-else class="pi pi-spin pi-spinner"></i>
                    <span>{{ saving ? 'Saving...' : 'Save' }}</span>
                  </button>
                  <button
                    @click="cancelEdit()"
                    :disabled="saving"
                    class="px-3 py-1.5 rounded-lg bg-gray-200 dark:bg-slate-700 hover:bg-gray-300 dark:hover:bg-slate-600 text-gray-700 dark:text-gray-100 text-xs font-semibold active:scale-95 transition-all"
                    aria-label="Cancel"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Phone Number Section -->
          <div class="mt-auto pt-4 border-t border-gray-200 dark:border-slate-700 space-y-2">
            <div>
              <p class="text-xs uppercase tracking-wider text-gray-500 dark:text-gray-400 font-semibold mb-1">
                <i class="pi pi-phone text-xs mr-1"></i>
                Phone Number
              </p>
              <p class="text-sm font-mono text-gray-800 dark:text-gray-200 break-all bg-gray-50 dark:bg-slate-900/50 px-3 py-2 rounded-lg">
                {{ c.number }}
              </p>
            </div>
            <div>
              <p class="text-[10px] uppercase tracking-wider text-gray-400 dark:text-gray-500 font-semibold">Normalized</p>
              <p class="text-xs font-mono text-gray-500 dark:text-gray-400">{{ c.normalizedNumber }}</p>
            </div>
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
