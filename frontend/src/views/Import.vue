<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">Import Messages</h1>

    <div class="bg-white dark:bg-gray-800 shadow rounded p-4 space-y-4">
      <p class="text-sm text-gray-600 dark:text-gray-300">Upload an XML export file for streaming import. Streaming shows live progress and is suitable for all file sizes.</p>

      <div class="flex flex-wrap items-center gap-3">
        <input type="file" accept=".xml,text/xml,application/xml" @change="onFile" class="block text-sm" :disabled="starting || !!jobId" />
        <button :disabled="disableStreaming" @click="start" class="px-4 py-2 bg-blue-600 text-white rounded disabled:opacity-50">Start Streaming Import</button>
        <button v-if="jobId && completed" @click="reset" class="px-3 py-2 bg-gray-600 text-white rounded">Reset</button>
        <button v-if="jobId && !completed" @click="cancelPolling" class="px-3 py-2 bg-yellow-600 text-white rounded">Stop Polling</button>
      </div>

      <!-- Streaming Progress -->
      <div v-if="jobId" class="space-y-3">
        <div class="text-sm">Job ID: <code class="text-xs">{{ jobId }}</code></div>
        <div class="w-full bg-gray-200 dark:bg-gray-700 h-4 rounded overflow-hidden">
          <div
            class="h-full transition-all"
            :class="{
              'bg-green-600': progress?.status === 'COMPLETED',
              'bg-red-600': progress?.status === 'FAILED',
              'bg-blue-600': progress?.status !== 'COMPLETED' && progress?.status !== 'FAILED'
            }"
            :style="{ width: percentBytes + '%' }"
          ></div>
        </div>
        <div class="grid grid-cols-2 md:grid-cols-3 gap-2 text-xs">
          <div>
            <span class="font-semibold">Status:</span>
            <span
              :class="{
                'text-green-600': progress?.status === 'COMPLETED',
                'text-red-600': progress?.status === 'FAILED',
                'text-gray-800': progress?.status !== 'COMPLETED' && progress?.status !== 'FAILED'
              }"
            >
              {{ progress?.status }}
            </span>
          </div>
          <div><span class="font-semibold">Bytes:</span> {{ formatBytes(progress?.bytesRead) }} / {{ formatBytes(progress?.totalBytes) }}</div>
          <div><span class="font-semibold">Imported:</span> {{ progress?.importedMessages }}</div>
          <div><span class="font-semibold">Processed:</span> {{ progress?.processedMessages }}</div>
          <div><span class="font-semibold">Duplicates:</span> {{ progress?.duplicateMessages }}</div>
          <div v-if="progress?.error" class="text-red-600 col-span-full">Error: {{ progress?.error }}</div>
        </div>
      </div>

      <!-- Final Messages -->
      <div v-if="completed && !progress?.error" class="p-3 bg-green-100 dark:bg-green-900/40 text-green-800 dark:text-green-200 rounded text-sm">
        Streaming import finished. Imported {{ progress?.importedMessages }} messages ({{ progress?.duplicateMessagesFinal ?? progress?.duplicateMessages }} duplicates).
      </div>
      <div v-if="progress?.error" class="p-3 bg-red-100 dark:bg-red-900/40 text-red-800 dark:text-red-200 rounded text-sm">
        Streaming import failed: {{ progress.error }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue';
import { startStreamingImport, getImportProgress, type ImportProgress } from '../services/api';

const selectedFile = ref<File | null>(null);
const starting = ref(false);
const jobId = ref<string | null>(null);
const progress = ref<ImportProgress | null>(null);
const pollHandle = ref<number | null>(null);

const completed = computed(() => progress.value?.status === 'COMPLETED' || progress.value?.status === 'FAILED');
const percentBytes = computed(() => progress.value?.percentBytes ?? 0);
const disableStreaming = computed(() => !selectedFile.value || starting.value || !!jobId.value);

function onFile(e: Event) {
  const input = e.target as HTMLInputElement;
  const files = input.files;
  if (files && files.length > 0) {
    const file = files.item(0);
    if (file) selectedFile.value = file;
  } else {
    selectedFile.value = null;
  }
}

async function start() {
  if (!selectedFile.value) return;
  starting.value = true;
  try {
    const res = await startStreamingImport(selectedFile.value);
    jobId.value = res.jobId;
    startPolling();
  } catch (err: any) {
    console.error(err);
    alert('Failed to start import: ' + err.message);
  } finally {
    starting.value = false;
  }
}

function startPolling() {
  stopPolling();
  pollHandle.value = window.setInterval(async () => {
    if (!jobId.value) return;
    try {
      const p = await getImportProgress(jobId.value);
      progress.value = p;
      if (!p) return;
      if (p.status === 'COMPLETED' || p.status === 'FAILED') {
        stopPolling();
      }
    } catch (e) {
      console.error(e);
    }
  }, 1500);
}

function stopPolling() {
  if (pollHandle.value) {
    clearInterval(pollHandle.value);
    pollHandle.value = null;
  }
}
function cancelPolling() { stopPolling(); }

function reset() {
  stopPolling();
  jobId.value = null;
  progress.value = null;
  selectedFile.value = null;
}

function formatBytes(b?: number) {
  if (!b && b !== 0) return '-';
  const units = ['B','KB','MB','GB'];
  let v = b; let i = 0;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return v.toFixed(1) + ' ' + units[i];
}

onUnmounted(() => stopPolling());
</script>

<style scoped>
code { background: rgba(0,0,0,0.05); padding: 2px 4px; border-radius: 4px; }
</style>
