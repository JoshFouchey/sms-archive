<template>
  <details class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden">
    <summary class="cursor-pointer px-4 py-2.5 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
      {{ label }}
    </summary>
    <div class="border-t border-gray-100 dark:border-gray-700">
      <div v-if="editing" class="p-3 space-y-2">
        <textarea
          :value="draft"
          @input="$emit('update:draft', ($event.target as HTMLTextAreaElement).value)"
          class="w-full min-h-40 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 p-3 text-xs font-mono text-gray-800 dark:text-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500/30"
        ></textarea>
        <div class="flex gap-2 justify-end">
          <button @click="$emit('cancel')" class="px-2.5 py-1.5 rounded-lg text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300">Cancel</button>
          <button
            @click="$emit('run')"
            :disabled="running || !draft.trim()"
            class="px-2.5 py-1.5 rounded-lg text-xs bg-blue-500 text-white hover:bg-blue-600 disabled:bg-gray-300 dark:disabled:bg-gray-600"
          >
            <i :class="running ? 'pi pi-spin pi-spinner' : 'pi pi-play'" class="mr-1"></i>Run SQL
          </button>
        </div>
      </div>
      <div v-else>
        <div class="flex justify-end gap-1 px-3 pt-2">
          <button @click="$emit('edit')" class="px-2.5 py-1.5 rounded-md text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600">
            <i class="pi pi-pencil mr-1"></i>Edit
          </button>
          <button
            @click="copy"
            :class="[
              'px-2.5 py-1.5 rounded-md text-xs transition-colors',
              copied
                ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300'
                : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'
            ]"
          >
            <i :class="copied ? 'pi pi-check' : 'pi pi-copy'" class="mr-1"></i>{{ copied ? 'Copied' : 'Copy' }}
          </button>
        </div>
        <pre class="p-4 pt-2 text-xs overflow-x-auto text-gray-700 dark:text-gray-300"><code>{{ sql }}</code></pre>
      </div>
    </div>
  </details>
</template>

<script setup lang="ts">
import { ref } from 'vue';

// Collapsible SQL panel with view/copy and an edit mode driven by the parent
// (the parent owns the draft text and the run action).
const props = defineProps<{
  sql: string;
  label: string;
  editing: boolean;
  draft: string;
  running: boolean;
}>();

defineEmits<{
  (e: 'edit'): void;
  (e: 'cancel'): void;
  (e: 'run'): void;
  (e: 'update:draft', value: string): void;
}>();

const copied = ref(false);
let copiedTimer: ReturnType<typeof setTimeout> | null = null;

async function copy() {
  try {
    await navigator.clipboard.writeText(props.sql);
    copied.value = true;
    if (copiedTimer) clearTimeout(copiedTimer);
    copiedTimer = setTimeout(() => { copied.value = false; }, 1500);
  } catch (e) {
    console.error('Failed to copy SQL', e);
  }
}
</script>
