<template>
  <div class="space-y-6">
    <!-- Header -->
    <PageHeader v-if="!hideHeader" title="AI Settings" subtitle="Manage semantic embeddings and search configuration" icon="pi pi-microchip">
      <PageHeaderStat class="hidden sm:block" label="Embedded" :value="embeddingStats?.embeddedMessages?.toLocaleString() ?? '—'" icon="pi pi-database" />
    </PageHeader>

    <div class="max-w-3xl">
      <!-- Embedding Section -->
      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-md border border-gray-200 dark:border-gray-700 overflow-hidden">
        <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between flex-wrap gap-3">
          <div class="flex items-center gap-3">
            <div class="p-2 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
              <i class="pi pi-database text-blue-600 dark:text-blue-400"></i>
            </div>
            <div>
              <h2 class="font-semibold text-lg">Semantic Embeddings</h2>
              <p class="text-sm text-gray-500 dark:text-gray-400">
                Model: <span class="font-mono">{{ embeddingStats?.modelName ?? '—' }}</span>
              </p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="handleStartEmbedding"
              :disabled="activeEmbeddingJob !== null"
              class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors text-sm font-medium"
            >
              <i class="pi pi-play mr-1"></i>
              {{ activeEmbeddingJob ? 'Running…' : 'Start Embedding' }}
            </button>
            <button
              @click="handleReembed"
              :disabled="activeEmbeddingJob !== null"
              class="px-4 py-2 bg-amber-600 text-white rounded-lg hover:bg-amber-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors text-sm font-medium"
            >
              <i class="pi pi-refresh mr-1"></i>
              Re-embed All
            </button>
          </div>
        </div>

        <!-- Embedding Coverage -->
        <div class="px-6 py-4">
          <div class="flex items-center justify-between text-sm mb-2">
            <span class="text-gray-600 dark:text-gray-400">Coverage</span>
            <span class="font-semibold">
              {{ embeddingStats ? `${embeddingStats.embeddedMessages.toLocaleString()} / ${embeddingStats.totalMessages.toLocaleString()}` : '—' }}
            </span>
          </div>
          <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-3">
            <div
              class="bg-blue-600 h-3 rounded-full transition-all duration-500"
              :style="{ width: `${embeddingStats?.percentComplete ?? 0}%` }"
            ></div>
          </div>
          <div class="text-right text-xs text-gray-500 dark:text-gray-400 mt-1">
            {{ (embeddingStats?.percentComplete ?? 0).toFixed(1) }}%
          </div>
        </div>

        <!-- Active Job Progress -->
        <div v-if="activeEmbeddingJob" class="px-6 py-4 bg-blue-50 dark:bg-blue-900/20 border-t border-gray-200 dark:border-gray-700">
          <div class="flex items-center justify-between mb-2">
            <div class="flex items-center gap-2">
              <i class="pi pi-spin pi-spinner text-blue-600"></i>
              <span class="text-sm font-medium">Embedding in progress</span>
            </div>
            <button
              @click="handleCancelEmbedding"
              class="text-xs text-red-600 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 font-medium"
            >
              Cancel
            </button>
          </div>
          <div class="w-full bg-blue-200 dark:bg-blue-800 rounded-full h-2">
            <div
              class="bg-blue-600 h-2 rounded-full transition-all duration-300"
              :style="{ width: `${activeEmbeddingJob.percentComplete}%` }"
            ></div>
          </div>
          <div class="flex justify-between text-xs text-gray-500 dark:text-gray-400 mt-1">
            <span>{{ activeEmbeddingJob.processed.toLocaleString() }} / {{ activeEmbeddingJob.totalMessages.toLocaleString() }}</span>
            <span v-if="activeEmbeddingJob.failed > 0" class="text-red-500">{{ activeEmbeddingJob.failed }} failed</span>
            <span>{{ activeEmbeddingJob.percentComplete.toFixed(1) }}%</span>
          </div>
        </div>

        <!-- Job History -->
        <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700">
          <h3 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-3">Job History</h3>
          <div v-if="embeddingJobs.length === 0" class="text-sm text-gray-400 italic">No jobs yet</div>
          <div v-else class="space-y-2 max-h-48 overflow-y-auto">
            <div
              v-for="job in embeddingJobs"
              :key="job.id"
              class="flex items-center justify-between text-sm p-2 rounded-lg bg-gray-50 dark:bg-gray-700/50"
            >
              <div class="flex items-center gap-2">
                <span :class="statusBadgeClass(job.status)">{{ job.status }}</span>
                <span class="text-gray-500 dark:text-gray-400 text-xs">
                  {{ formatDate(job.createdAt) }}
                </span>
              </div>
              <span class="text-xs text-gray-500 dark:text-gray-400">
                {{ job.processed.toLocaleString() }} msgs
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import PageHeaderStat from '@/components/PageHeaderStat.vue';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import {
  getEmbeddingStats,
  startEmbeddingJob,
  startReembeddingJob,
  getEmbeddingJobStatus,
  cancelEmbeddingJob,
  getEmbeddingJobHistory,
  type EmbeddingStats,
  type EmbeddingJob,
} from '../services/api';

defineProps<{ hideHeader?: boolean }>();

const toast = useToast();
const confirmDialog = useConfirm();

const embeddingStats = ref<EmbeddingStats | null>(null);
const embeddingJobs = ref<EmbeddingJob[]>([]);
const activeEmbeddingJob = ref<EmbeddingJob | null>(null);

let pollTimer: ReturnType<typeof setInterval> | null = null;

async function loadAll() {
  try {
    const [stats, embHistory] = await Promise.all([
      getEmbeddingStats().catch(() => null),
      getEmbeddingJobHistory().catch(() => []),
    ]);
    embeddingStats.value = stats;
    embeddingJobs.value = embHistory;

    // Detect active jobs
    const runningEmb = embHistory.find((j: EmbeddingJob) => j.status === 'RUNNING' || j.status === 'PENDING');
    if (runningEmb) {
      activeEmbeddingJob.value = runningEmb;
      startPolling();
    }
  } catch (e) {
    console.error('Failed to load AI settings data', e);
  }
}

function startPolling() {
  if (pollTimer) return;
  pollTimer = setInterval(pollActiveJobs, 3000);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

async function pollActiveJobs() {
  let anyActive = false;

  if (activeEmbeddingJob.value) {
    try {
      const job = await getEmbeddingJobStatus(activeEmbeddingJob.value.id);
      activeEmbeddingJob.value = job;
      if (job.status === 'COMPLETED' || job.status === 'FAILED') {
        if (job.status === 'FAILED') {
          toast.add({
            severity: 'error',
            summary: 'Embedding Job Failed',
            detail: job.errorMessage || 'The job failed without an error message.',
            life: 8000,
          });
        } else {
          toast.add({
            severity: 'success',
            summary: 'Embedding Complete',
            detail: `Processed ${job.processed.toLocaleString()} messages${job.failed > 0 ? ` (${job.failed} failed)` : ''}.`,
            life: 5000,
          });
        }
        activeEmbeddingJob.value = null;
        // Refresh stats and history
        const [stats, history] = await Promise.all([
          getEmbeddingStats().catch(() => null),
          getEmbeddingJobHistory().catch(() => []),
        ]);
        embeddingStats.value = stats;
        embeddingJobs.value = history;
      } else {
        anyActive = true;
      }
    } catch {
      activeEmbeddingJob.value = null;
    }
  }

  if (!anyActive) stopPolling();
}

async function handleStartEmbedding() {
  try {
    const job = await startEmbeddingJob();
    activeEmbeddingJob.value = job;
    startPolling();
  } catch (e: any) {
    console.error('Failed to start embedding job', e);
    toast.add({
      severity: 'error',
      summary: 'Failed to Start Embedding',
      detail: e?.response?.data?.message || e?.message || 'Server error',
      life: 6000,
    });
  }
}

function handleReembed() {
  confirmDialog.require({
    header: 'Re-embed All Messages',
    message: 'This will delete ALL existing embeddings and re-embed every message with conversational context. This may take a long time.',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    acceptProps: { label: 'Re-embed All', severity: 'warn' },
    accept: async () => {
      try {
        const job = await startReembeddingJob();
        activeEmbeddingJob.value = job;
        startPolling();
      } catch (e: any) {
        console.error('Failed to start re-embedding job', e);
        toast.add({
          severity: 'error',
          summary: 'Failed to Start Re-embedding',
          detail: e?.response?.data?.message || e?.message || 'Server error',
          life: 6000,
        });
      }
    },
  });
}

async function handleCancelEmbedding() {
  if (!activeEmbeddingJob.value) return;
  try {
    await cancelEmbeddingJob(activeEmbeddingJob.value.id);
    activeEmbeddingJob.value = null;
    embeddingJobs.value = await getEmbeddingJobHistory().catch(() => []);
    toast.add({ severity: 'info', summary: 'Job Cancelled', detail: 'The embedding job was cancelled.', life: 4000 });
  } catch (e: any) {
    console.error('Failed to cancel embedding job', e);
    toast.add({
      severity: 'error',
      summary: 'Cancel Failed',
      detail: e?.response?.data?.message || e?.message || 'Server error',
      life: 6000,
    });
  }
}

function statusBadgeClass(status: string): string {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium';
  switch (status) {
    case 'COMPLETED': return `${base} bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400`;
    case 'RUNNING': return `${base} bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400`;
    case 'PENDING': return `${base} bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400`;
    case 'FAILED': return `${base} bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400`;
    default: return `${base} bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300`;
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
  });
}

onMounted(loadAll);
onUnmounted(stopPolling);
</script>
