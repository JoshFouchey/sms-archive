<template>
  <div class="space-y-4 md:space-y-6 p-4 md:p-0">
    <h1 class="text-xl md:text-2xl font-semibold">Import Messages</h1>

    <div class="bg-white dark:bg-gray-800 shadow rounded p-4 md:p-6 space-y-4">
      <p class="text-sm md:text-base text-gray-600 dark:text-gray-300">Upload an XML export file for streaming import. Streaming shows live progress and is suitable for all file sizes.</p>

      <!-- File Upload Component -->
      <FileUpload
        mode="basic"
        accept=".xml,text/xml,application/xml"
        :disabled="starting || !!jobId"
        @select="onFileSelect"
        :auto="false"
        chooseLabel="Choose XML File"
        class="mb-3"
        :class="{ 'opacity-50 pointer-events-none': starting || !!jobId }"
      >
        <template #empty>
          <div class="flex items-center justify-center flex-col p-8 border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-lg bg-gray-50 dark:bg-gray-900/30">
            <i class="pi pi-cloud-upload text-4xl text-gray-400 dark:text-gray-500 mb-3"></i>
            <p class="text-sm text-gray-600 dark:text-gray-400 mb-1">
              Drag and drop your XML file here
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-500">
              or click to browse
            </p>
          </div>
        </template>
      </FileUpload>

      <!-- Selected File Display -->
      <div v-if="selectedFile && !jobId" class="p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg flex items-center justify-between">
        <div class="flex items-center gap-2 min-w-0 flex-1">
          <i class="pi pi-file text-blue-600 dark:text-blue-400"></i>
          <span class="text-sm text-gray-700 dark:text-gray-300 truncate">{{ selectedFile.name }}</span>
          <span class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">({{ formatBytes(selectedFile.size) }})</span>
        </div>
        <button
          @click="clearSelectedFile"
          class="ml-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition-colors"
          aria-label="Clear file"
        >
          <i class="pi pi-times"></i>
        </button>
      </div>

      <!-- Action Buttons -->
      <div class="flex flex-col md:flex-row gap-3">
        <button
          :disabled="disableStreaming"
          @click="start"
          class="w-full md:w-auto px-4 py-3 md:py-2 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white rounded disabled:opacity-50 transition-colors font-medium flex items-center justify-center gap-2"
        >
          <i class="pi pi-upload"></i>
          <span>{{ starting ? 'Starting...' : 'Start Streaming Import' }}</span>
        </button>
        <button
          v-if="jobId && completed"
          @click="reset"
          class="w-full md:w-auto px-4 py-3 md:py-2 bg-gray-600 hover:bg-gray-700 active:bg-gray-800 text-white rounded transition-colors font-medium flex items-center justify-center gap-2"
        >
          <i class="pi pi-refresh"></i>
          <span>Reset</span>
        </button>
        <button
          v-if="jobId && !completed"
          @click="cancelPolling"
          class="w-full md:w-auto px-4 py-3 md:py-2 bg-yellow-600 hover:bg-yellow-700 active:bg-yellow-800 text-white rounded transition-colors font-medium flex items-center justify-center gap-2"
        >
          <i class="pi pi-stop-circle"></i>
          <span>Stop Polling</span>
        </button>
      </div>

      <!-- Streaming Progress -->
      <div v-if="jobId" class="space-y-3">
        <div class="text-xs md:text-sm break-all">
          <span class="font-medium">Job ID:</span>
          <code class="text-xs">{{ jobId }}</code>
        </div>
        <div class="w-full bg-gray-200 dark:bg-gray-700 h-5 md:h-4 rounded overflow-hidden">
          <div
            class="h-full transition-all duration-300"
            :class="{
              'bg-green-600': progress?.status === 'COMPLETED',
              'bg-red-600': progress?.status === 'FAILED',
              'bg-blue-600': progress?.status !== 'COMPLETED' && progress?.status !== 'FAILED'
            }"
            :style="{ width: percentBytes + '%' }"
          ></div>
        </div>
        <div class="grid grid-cols-2 md:grid-cols-3 gap-2 md:gap-3 text-xs md:text-sm">
          <div class="col-span-2 md:col-span-1">
            <span class="font-semibold">Status:</span>
            <span
              :class="{
                'text-green-600 dark:text-green-400': progress?.status === 'COMPLETED',
                'text-red-600 dark:text-red-400': progress?.status === 'FAILED',
                'text-blue-600 dark:text-blue-400': progress?.status !== 'COMPLETED' && progress?.status !== 'FAILED'
              }"
              class="ml-1 font-medium"
            >
              {{ progress?.status }}
            </span>
          </div>
          <div class="col-span-2 md:col-span-2">
            <span class="font-semibold">Bytes:</span>
            <span class="ml-1">{{ formatBytes(progress?.bytesRead) }} / {{ formatBytes(progress?.totalBytes) }}</span>
          </div>
          <div>
            <span class="font-semibold">Imported:</span>
            <span class="ml-1">{{ progress?.importedMessages }}</span>
          </div>
          <div>
            <span class="font-semibold">Processed:</span>
            <span class="ml-1">{{ progress?.processedMessages }}</span>
          </div>
          <div>
            <span class="font-semibold">Duplicates:</span>
            <span class="ml-1">{{ progress?.duplicateMessages }}</span>
          </div>
          <div v-if="progress?.error" class="text-red-600 dark:text-red-400 col-span-full break-words">
            <span class="font-semibold">Error:</span> <span class="ml-1">{{ progress?.error }}</span>
          </div>
        </div>
      </div>

      <!-- Final Messages -->
      <div v-if="completed && !progress?.error" class="p-3 md:p-4 bg-green-100 dark:bg-green-900/40 text-green-800 dark:text-green-200 rounded text-sm md:text-base">
        ✓ Streaming import finished. Imported {{ progress?.importedMessages }} messages ({{ progress?.duplicateMessagesFinal ?? progress?.duplicateMessages }} duplicates).
      </div>
      <div v-if="progress?.error" class="p-3 md:p-4 bg-red-100 dark:bg-red-900/40 text-red-800 dark:text-red-200 rounded text-sm md:text-base break-words">
        ✗ Streaming import failed: {{ progress.error }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue';
import { startStreamingImport, getImportProgress, type ImportProgress } from '../services/api';
import FileUpload, { type FileUploadSelectEvent } from 'primevue/fileupload';

const selectedFile = ref<File | null>(null);
const starting = ref(false);
const jobId = ref<string | null>(null);
const progress = ref<ImportProgress | null>(null);
const pollHandle = ref<number | null>(null);

const completed = computed(() => progress.value?.status === 'COMPLETED' || progress.value?.status === 'FAILED');
const percentBytes = computed(() => progress.value?.percentBytes ?? 0);
const disableStreaming = computed(() => !selectedFile.value || starting.value || !!jobId.value);

function onFileSelect(event: FileUploadSelectEvent) {
  if (event.files && event.files.length > 0) {
    selectedFile.value = event.files[0];
  }
}

function clearSelectedFile() {
  selectedFile.value = null;
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
