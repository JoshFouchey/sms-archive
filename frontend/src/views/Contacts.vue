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

          <!-- Merge Button -->
          <div class="mt-3">
            <button
              @click="openMergeDialog(c)"
              class="w-full px-3 py-2 rounded-lg bg-purple-100 dark:bg-purple-900/30 hover:bg-purple-200 dark:hover:bg-purple-800/40 text-purple-700 dark:text-purple-300 text-xs font-semibold flex items-center justify-center gap-2 transition-all active:scale-95"
            >
              <i class="pi pi-code-branch"></i>
              Merge Contact
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Merge Dialog -->
    <Dialog v-model:visible="mergeDialogOpen" modal header="Merge Contact" :style="{ width: '35rem' }" :breakpoints="{ '640px': '90vw' }">
      <div class="space-y-4">
        <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
          <p class="text-sm text-blue-800 dark:text-blue-200 mb-2">
            <i class="pi pi-info-circle mr-1"></i>
            <strong>Merging:</strong>
          </p>
          <div class="flex items-center gap-2 ml-5">
            <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center text-white text-sm font-bold">
              {{ (contactToMerge?.name || contactToMerge?.number || '?').charAt(0).toUpperCase() }}
            </div>
            <span class="font-semibold text-blue-900 dark:text-blue-100">
              {{ contactToMerge?.name || contactToMerge?.number }}
            </span>
          </div>
        </div>

        <div class="space-y-2">
          <label class="text-sm font-semibold text-gray-700 dark:text-gray-300">
            <i class="pi pi-arrow-right mr-1"></i>
            Select target contact to merge into:
          </label>
          <Select
            v-model="selectedMergeTarget"
            :options="mergeTargetOptions"
            optionLabel="label"
            filter
            :filterFields="['label']"
            placeholder="Search and select contact..."
            class="w-full"
            :showClear="true"
          >
            <template #value="slotProps">
              <div v-if="slotProps.value" class="flex items-center gap-2">
                <div class="w-6 h-6 rounded-full bg-gradient-to-br from-purple-500 to-pink-400 flex items-center justify-center text-white text-xs font-bold">
                  {{ slotProps.value.label.charAt(0).toUpperCase() }}
                </div>
                <span>{{ slotProps.value.label }}</span>
              </div>
              <span v-else>{{ slotProps.placeholder }}</span>
            </template>
            <template #option="slotProps">
              <div class="flex items-center gap-2">
                <div class="w-6 h-6 rounded-full bg-gradient-to-br from-purple-500 to-pink-400 flex items-center justify-center text-white text-xs font-bold">
                  {{ slotProps.option.label.charAt(0).toUpperCase() }}
                </div>
                <span>{{ slotProps.option.label }}</span>
              </div>
            </template>
          </Select>
        </div>

        <div class="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-3">
          <p class="text-xs text-yellow-800 dark:text-yellow-200">
            <i class="pi pi-exclamation-triangle mr-1"></i>
            <strong>Note:</strong> All messages from the first contact will be transferred to the target contact. Duplicates will be automatically skipped. The original contact will be archived.
          </p>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end gap-2">
          <button
            @click="closeMergeDialog"
            :disabled="merging"
            class="px-4 py-2 rounded-lg bg-gray-200 dark:bg-slate-700 hover:bg-gray-300 dark:hover:bg-slate-600 text-gray-900 dark:text-gray-100 text-sm font-semibold transition-all"
          >
            Cancel
          </button>
          <button
            @click="performMerge"
            :disabled="!selectedMergeTarget || merging"
            class="px-4 py-2 rounded-lg bg-purple-600 hover:bg-purple-700 disabled:bg-purple-400 text-white text-sm font-semibold transition-all flex items-center gap-2"
          >
            <i v-if="!merging" class="pi pi-code-branch"></i>
            <i v-else class="pi pi-spin pi-spinner"></i>
            <span>{{ merging ? 'Merging...' : 'Merge Contacts' }}</span>
          </button>
        </div>
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue';
import { getDistinctContacts, updateContactName, mergeContacts, type Contact } from '../services/api';
import { useToast } from 'primevue/usetoast';
import Dialog from 'primevue/dialog';
import Select from 'primevue/select';

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

// Merge state
const mergeDialogOpen = ref(false);
const contactToMerge = ref<Contact | null>(null);
const selectedMergeTarget = ref<{ value: number; label: string } | null>(null);
const merging = ref(false);

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

const mergeTargetOptions = computed(() => {
  if (!contactToMerge.value) return [];
  return contacts.value
    .filter(c => c.id !== contactToMerge.value!.id)
    .map(c => ({
      value: c.id,
      label: c.name || c.number
    }));
});

function isEditing(id: number) { return editingId.value === id; }

function openMergeDialog(contact: Contact) {
  contactToMerge.value = contact;
  selectedMergeTarget.value = null;
  mergeDialogOpen.value = true;
}

function closeMergeDialog() {
  mergeDialogOpen.value = false;
  contactToMerge.value = null;
  selectedMergeTarget.value = null;
}

async function performMerge() {
  if (!contactToMerge.value || !selectedMergeTarget.value) return;

  merging.value = true;
  try {
    const result = await mergeContacts(selectedMergeTarget.value.value, contactToMerge.value.id);

    if (result.success) {
      toast.add({
        severity: 'success',
        summary: 'Contacts Merged',
        detail: `Transferred ${result.messagesTransferred} messages${result.duplicatesSkipped > 0 ? `, skipped ${result.duplicatesSkipped} duplicates` : ''}.`,
        life: 5000
      });

      // Remove merged contact from list
      contacts.value = contacts.value.filter(c => c.id !== contactToMerge.value!.id);
      closeMergeDialog();
    } else {
      toast.add({
        severity: 'error',
        summary: 'Merge Failed',
        detail: result.message || 'An error occurred',
        life: 5000
      });
    }
  } catch (e: any) {
    toast.add({
      severity: 'error',
      summary: 'Merge Failed',
      detail: e?.response?.data?.message || e?.message || 'Server error',
      life: 5000
    });
  } finally {
    merging.value = false;
  }
}

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
