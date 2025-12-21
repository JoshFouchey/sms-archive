<template>
  <div class="space-y-6">
    <!-- Header Section -->
    <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 rounded-2xl shadow-lg p-6 text-white">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 class="text-4xl font-bold mb-2 flex items-center gap-3">
            <i class="pi pi-upload"></i>
            Import Messages
          </h1>
          <p class="text-blue-100 dark:text-blue-200">Upload an XML export file for streaming import</p>
        </div>
        <div v-if="jobId && progress" class="flex items-center gap-2">
          <div class="bg-white/10 backdrop-blur-sm rounded-lg px-4 py-2 border border-white/20">
            <div class="flex items-center gap-2">
              <i :class="progress.status === 'COMPLETED' ? 'pi pi-check-circle' : progress.status === 'FAILED' ? 'pi pi-times-circle' : 'pi pi-spinner pi-spin'" class="text-lg"></i>
              <div class="text-left">
                <p class="text-xs text-blue-100">Status</p>
                <p class="text-2xl font-bold">{{ progress.status }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Import Card -->
    <div class="bg-white dark:bg-gray-800 shadow-lg rounded-xl p-6 border border-gray-200 dark:border-gray-700 space-y-6">
      <!-- Info Message -->
      <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-4">
        <div class="flex gap-3">
          <i class="pi pi-info-circle text-blue-600 dark:text-blue-400 text-lg shrink-0 mt-0.5"></i>
          <div>
            <h3 class="font-semibold text-blue-900 dark:text-blue-100 mb-1">About Streaming Import</h3>
            <p class="text-sm text-blue-800 dark:text-blue-200">
              Streaming import shows live progress and is suitable for all file sizes. Your messages will be processed in real-time with duplicate detection.
            </p>
          </div>
        </div>
      </div>

      <!-- File Upload Component -->
      <div>
        <label class="text-xs font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wide block mb-3">
          <i class="pi pi-file text-xs mr-1"></i>
          Select XML File
        </label>
        <FileUpload
          mode="basic"
          accept=".xml,text/xml,application/xml"
          :disabled="starting || !!jobId"
          @select="onFileSelect"
          :auto="false"
          chooseLabel="Choose XML File"
          :class="{ 'opacity-50 pointer-events-none': starting || !!jobId }"
        >
          <template #empty>
            <div class="flex items-center justify-center flex-col p-12 border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-xl bg-gray-50 dark:bg-gray-900/30 hover:border-blue-400 dark:hover:border-blue-600 transition-all">
              <i class="pi pi-cloud-upload text-5xl text-blue-600 dark:text-blue-400 mb-4"></i>
              <p class="text-base font-semibold text-gray-700 dark:text-gray-300 mb-2">
                Drag and drop your XML file here
              </p>
              <p class="text-sm text-gray-500 dark:text-gray-400">
                or click to browse
              </p>
            </div>
          </template>
        </FileUpload>
      </div>

      <!-- Selected File Display -->
      <div v-if="selectedFile && !jobId" class="p-4 bg-gradient-to-r from-blue-50 to-cyan-50 dark:from-blue-900/20 dark:to-cyan-900/20 border-2 border-blue-300 dark:border-blue-700 rounded-xl shadow-sm">
        <div class="flex items-center justify-between gap-3">
          <div class="flex items-center gap-3 min-w-0 flex-1">
            <div class="bg-blue-100 dark:bg-blue-900/50 p-3 rounded-xl">
              <i class="pi pi-file text-2xl text-blue-600 dark:text-blue-400"></i>
            </div>
            <div class="min-w-0 flex-1">
              <p class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">{{ selectedFile.name }}</p>
              <p class="text-xs text-gray-600 dark:text-gray-400 mt-1">{{ formatBytes(selectedFile.size) }}</p>
            </div>
          </div>
          <button
            @click="clearSelectedFile"
            class="p-2 rounded-lg hover:bg-red-100 dark:hover:bg-red-900/30 text-gray-500 hover:text-red-600 dark:hover:text-red-400 transition-all"
            aria-label="Clear file"
            title="Remove file"
          >
            <i class="pi pi-times text-lg"></i>
          </button>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="flex flex-col sm:flex-row gap-3">
        <button
          :disabled="disableStreaming"
          @click="start"
          class="flex-1 px-6 py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white rounded-xl disabled:opacity-50 transition-all font-semibold shadow-md hover:shadow-lg flex items-center justify-center gap-2"
        >
          <i :class="starting ? 'pi pi-spin pi-spinner' : 'pi pi-upload'"></i>
          <span>{{ starting ? 'Starting...' : 'Start Streaming Import' }}</span>
        </button>
        <button
          v-if="jobId && completed"
          @click="reset"
          class="flex-1 sm:flex-none px-6 py-3 bg-gray-600 hover:bg-gray-700 text-white rounded-xl transition-all font-semibold shadow-md hover:shadow-lg flex items-center justify-center gap-2"
        >
          <i class="pi pi-refresh"></i>
          <span>Reset</span>
        </button>
        <button
          v-if="jobId && !completed"
          @click="cancelPolling"
          class="flex-1 sm:flex-none px-6 py-3 bg-yellow-600 hover:bg-yellow-700 text-white rounded-xl transition-all font-semibold shadow-md hover:shadow-lg flex items-center justify-center gap-2"
        >
          <i class="pi pi-stop-circle"></i>
          <span>Stop Polling</span>
        </button>
      </div>

      <!-- Streaming Progress -->
      <div v-if="jobId" class="bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-900/50 dark:to-gray-800/50 rounded-xl p-6 border border-gray-200 dark:border-gray-700 space-y-5">
        <!-- Job ID -->
        <div class="flex items-center gap-2">
          <i class="pi pi-info-circle text-gray-500 dark:text-gray-400"></i>
          <span class="font-semibold text-gray-700 dark:text-gray-300 text-sm">Job ID:</span>
          <code class="text-xs bg-gray-200 dark:bg-gray-700 px-2 py-1 rounded font-mono">{{ jobId }}</code>
        </div>

        <!-- Progress Bar -->
        <div>
          <div class="flex justify-between items-center mb-2">
            <span class="text-sm font-semibold text-gray-700 dark:text-gray-300">Progress</span>
            <span class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ percentBytes }}%</span>
          </div>
          <div class="w-full bg-gray-300 dark:bg-gray-700 h-6 rounded-full overflow-hidden shadow-inner">
            <div
              class="h-full transition-all duration-500 flex items-center justify-end pr-2"
              :class="{
                'bg-gradient-to-r from-green-500 to-green-600': progress?.status === 'COMPLETED',
                'bg-gradient-to-r from-red-500 to-red-600': progress?.status === 'FAILED',
                'bg-gradient-to-r from-blue-500 to-cyan-500': progress?.status !== 'COMPLETED' && progress?.status !== 'FAILED'
              }"
              :style="{ width: percentBytes + '%' }"
            >
              <span v-if="percentBytes > 10" class="text-xs font-bold text-white">{{ percentBytes }}%</span>
            </div>
          </div>
        </div>

        <!-- Stats Grid -->
        <div class="grid grid-cols-2 lg:grid-cols-3 gap-4">
          <!-- Status -->
          <div class="bg-white dark:bg-gray-800 rounded-lg p-4 border border-gray-200 dark:border-gray-700 shadow-sm col-span-2 lg:col-span-1">
            <div class="flex items-center gap-2 mb-1">
              <i :class="{
                'pi pi-check-circle text-green-600 dark:text-green-400': progress?.status === 'COMPLETED',
                'pi pi-times-circle text-red-600 dark:text-red-400': progress?.status === 'FAILED',
                'pi pi-spin pi-spinner text-blue-600 dark:text-blue-400': progress?.status !== 'COMPLETED' && progress?.status !== 'FAILED'
              }"></i>
              <span class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">Status</span>
            </div>
            <p
              :class="{
                'text-green-600 dark:text-green-400': progress?.status === 'COMPLETED',
                'text-red-600 dark:text-red-400': progress?.status === 'FAILED',
                'text-blue-600 dark:text-blue-400': progress?.status !== 'COMPLETED' && progress?.status !== 'FAILED'
              }"
              class="text-xl font-bold"
            >
              {{ progress?.status }}
            </p>
          </div>

          <!-- Bytes -->
          <div class="bg-white dark:bg-gray-800 rounded-lg p-4 border border-gray-200 dark:border-gray-700 shadow-sm col-span-2">
            <div class="flex items-center gap-2 mb-1">
              <i class="pi pi-database text-purple-600 dark:text-purple-400"></i>
              <span class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">Bytes Processed</span>
            </div>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
              {{ formatBytes(progress?.bytesRead) }} <span class="text-sm text-gray-500">/ {{ formatBytes(progress?.totalBytes) }}</span>
            </p>
          </div>

          <!-- Imported -->
          <div class="bg-white dark:bg-gray-800 rounded-lg p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
            <div class="flex items-center gap-2 mb-1">
              <i class="pi pi-check text-green-600 dark:text-green-400"></i>
              <span class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">Imported</span>
            </div>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ progress?.importedMessages }}</p>
          </div>

          <!-- Processed -->
          <div class="bg-white dark:bg-gray-800 rounded-lg p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
            <div class="flex items-center gap-2 mb-1">
              <i class="pi pi-cog text-blue-600 dark:text-blue-400"></i>
              <span class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">Processed</span>
            </div>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ progress?.processedMessages }}</p>
          </div>

          <!-- Duplicates -->
          <div class="bg-white dark:bg-gray-800 rounded-lg p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
            <div class="flex items-center gap-2 mb-1">
              <i class="pi pi-copy text-yellow-600 dark:text-yellow-400"></i>
              <span class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">Duplicates</span>
            </div>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ progress?.duplicateMessages }}</p>
          </div>
        </div>

        <!-- Error Message -->
        <div v-if="progress?.error" class="bg-red-50 dark:bg-red-900/20 border-2 border-red-300 dark:border-red-800 rounded-xl p-4">
          <div class="flex gap-3">
            <i class="pi pi-exclamation-triangle text-red-600 dark:text-red-400 text-xl shrink-0 mt-0.5"></i>
            <div class="flex-1 min-w-0">
              <h4 class="font-semibold text-red-900 dark:text-red-100 mb-1">Error Occurred</h4>
              <p class="text-sm text-red-800 dark:text-red-200 break-words">{{ progress?.error }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Final Messages -->
      <div v-if="completed && !progress?.error" class="bg-gradient-to-r from-green-50 to-emerald-50 dark:from-green-900/20 dark:to-emerald-900/20 border-2 border-green-300 dark:border-green-700 rounded-xl p-5 shadow-md">
        <div class="flex items-start gap-3">
          <div class="bg-green-100 dark:bg-green-900/50 p-2 rounded-full">
            <i class="pi pi-check-circle text-2xl text-green-600 dark:text-green-400"></i>
          </div>
          <div class="flex-1">
            <h3 class="font-bold text-lg text-green-900 dark:text-green-100 mb-1">Import Completed Successfully!</h3>
            <p class="text-sm text-green-800 dark:text-green-200">
              Imported <span class="font-semibold">{{ progress?.importedMessages }}</span> messages
              (<span class="font-semibold">{{ progress?.duplicateMessagesFinal ?? progress?.duplicateMessages }}</span> duplicates skipped)
            </p>
          </div>
        </div>
      </div>
      <div v-if="progress?.error" class="bg-gradient-to-r from-red-50 to-rose-50 dark:from-red-900/20 dark:to-rose-900/20 border-2 border-red-300 dark:border-red-700 rounded-xl p-5 shadow-md">
        <div class="flex items-start gap-3">
          <div class="bg-red-100 dark:bg-red-900/50 p-2 rounded-full">
            <i class="pi pi-times-circle text-2xl text-red-600 dark:text-red-400"></i>
          </div>
          <div class="flex-1 min-w-0">
            <h3 class="font-bold text-lg text-red-900 dark:text-red-100 mb-1">Import Failed</h3>
            <p class="text-sm text-red-800 dark:text-red-200 break-words">{{ progress.error }}</p>
          </div>
        </div>
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

// Duplicate cleanup functions
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
