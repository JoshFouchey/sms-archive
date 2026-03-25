<template>
  <div class="space-y-6">
    <!-- Header -->
    <div v-if="!hideHeader" class="bg-gradient-to-r from-violet-600 to-purple-500 rounded-2xl p-6 text-white shadow-lg">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold flex items-center gap-3">
            <i class="pi pi-microchip text-3xl"></i>
            AI Settings
          </h1>
          <p class="text-white/80 mt-1">Manage embeddings, knowledge graph extraction, and entity resolution</p>
        </div>
        <div class="flex gap-3">
          <div class="bg-white/20 backdrop-blur-sm rounded-xl px-4 py-2 text-center">
            <div class="text-2xl font-bold">{{ embeddingStats?.embeddedMessages ?? '—' }}</div>
            <div class="text-xs text-white/70">Embedded</div>
          </div>
          <div class="bg-white/20 backdrop-blur-sm rounded-xl px-4 py-2 text-center">
            <div class="text-2xl font-bold">{{ kgStats?.entities ?? '—' }}</div>
            <div class="text-xs text-white/70">Entities</div>
          </div>
          <div class="bg-white/20 backdrop-blur-sm rounded-xl px-4 py-2 text-center">
            <div class="text-2xl font-bold">{{ kgStats?.triples ?? '—' }}</div>
            <div class="text-xs text-white/70">Facts</div>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- Embedding Section -->
      <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
        <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
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
          <div class="text-right text-xs text-gray-500 mt-1">
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
              class="text-xs text-red-600 hover:text-red-700 font-medium"
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
              <span class="text-xs text-gray-500">
                {{ job.processed.toLocaleString() }} msgs
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Knowledge Graph Extraction Section -->
      <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
        <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="p-2 bg-purple-100 dark:bg-purple-900/30 rounded-lg">
              <i class="pi pi-sitemap text-purple-600 dark:text-purple-400"></i>
            </div>
            <div>
              <h2 class="font-semibold text-lg">Knowledge Graph Extraction</h2>
              <p class="text-sm text-gray-500 dark:text-gray-400">
                Model: <span class="font-mono">{{ latestKgModel ?? '—' }}</span>
              </p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="handleResetKg"
              :disabled="activeExtractionJob !== null || resettingKg"
              class="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors text-sm font-medium"
            >
              <i class="pi pi-trash mr-1"></i>
              {{ resettingKg ? 'Resetting…' : 'Reset KG' }}
            </button>
            <button
              @click="handleStartExtraction"
              :disabled="activeExtractionJob !== null"
              class="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors text-sm font-medium"
            >
              <i class="pi pi-play mr-1"></i>
              {{ activeExtractionJob ? 'Running…' : 'Start Extraction' }}
            </button>
          </div>
        </div>

        <!-- KG Stats -->
        <div class="px-6 py-4 grid grid-cols-2 gap-4">
          <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3">
            <div class="text-2xl font-bold text-purple-600 dark:text-purple-400">{{ kgStats?.entities ?? 0 }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400">Entities Discovered</div>
          </div>
          <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3">
            <div class="text-2xl font-bold text-purple-600 dark:text-purple-400">{{ kgStats?.triples ?? 0 }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400">Facts Extracted</div>
          </div>
        </div>

        <!-- Active Extraction Job -->
        <div v-if="activeExtractionJob" class="px-6 py-4 bg-purple-50 dark:bg-purple-900/20 border-t border-gray-200 dark:border-gray-700">
          <div class="flex items-center justify-between mb-2">
            <div class="flex items-center gap-2">
              <i class="pi pi-spin pi-spinner text-purple-600"></i>
              <span class="text-sm font-medium">Extraction in progress</span>
            </div>
            <button
              @click="handleCancelExtraction"
              class="text-xs text-red-600 hover:text-red-700 font-medium"
            >
              Cancel
            </button>
          </div>
          <div class="w-full bg-purple-200 dark:bg-purple-800 rounded-full h-2">
            <div
              class="bg-purple-600 h-2 rounded-full transition-all duration-300"
              :style="{ width: `${activeExtractionJob.percentComplete}%` }"
            ></div>
          </div>
          <div class="flex justify-between text-xs text-gray-500 dark:text-gray-400 mt-1">
            <span>{{ activeExtractionJob.processed.toLocaleString() }} / {{ activeExtractionJob.totalMessages.toLocaleString() }}</span>
            <span>{{ activeExtractionJob.entitiesFound }} entities · {{ activeExtractionJob.triplesFound }} facts</span>
            <span>{{ activeExtractionJob.percentComplete.toFixed(1) }}%</span>
          </div>
        </div>

        <!-- Extraction Job History -->
        <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700">
          <h3 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-3">Job History</h3>
          <div v-if="extractionJobs.length === 0" class="text-sm text-gray-400 italic">No jobs yet</div>
          <div v-else class="space-y-2 max-h-48 overflow-y-auto">
            <div
              v-for="job in extractionJobs"
              :key="job.id"
              class="flex items-center justify-between text-sm p-2 rounded-lg bg-gray-50 dark:bg-gray-700/50"
            >
              <div class="flex items-center gap-2">
                <span :class="statusBadgeClass(job.status)">{{ job.status }}</span>
                <span class="text-gray-500 dark:text-gray-400 text-xs">
                  {{ formatDate(job.createdAt) }}
                </span>
              </div>
              <span class="text-xs text-gray-500">
                {{ job.entitiesFound }} entities · {{ job.triplesFound }} facts
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Entity Resolution Section -->
    <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
      <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="p-2 bg-amber-100 dark:bg-amber-900/30 rounded-lg">
            <i class="pi pi-link text-amber-600 dark:text-amber-400"></i>
          </div>
          <div>
            <h2 class="font-semibold text-lg">Entity Resolution</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400">
              Merge duplicate entities and link them to contacts
            </p>
          </div>
        </div>
        <button
          @click="handleRunResolution"
          :disabled="resolutionRunning"
          class="px-4 py-2 bg-amber-600 text-white rounded-lg hover:bg-amber-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors text-sm font-medium"
        >
          <i :class="resolutionRunning ? 'pi pi-spin pi-spinner' : 'pi pi-sync'" class="mr-1"></i>
          {{ resolutionRunning ? 'Running…' : 'Run Resolution' }}
        </button>
      </div>

      <!-- Last Resolution Result -->
      <div v-if="lastResolutionResult" class="px-6 py-4 grid grid-cols-3 gap-4">
        <div class="bg-green-50 dark:bg-green-900/20 rounded-lg p-3 text-center">
          <div class="text-2xl font-bold text-green-600">{{ lastResolutionResult.autoMerged }}</div>
          <div class="text-xs text-gray-500">Auto-Merged</div>
        </div>
        <div class="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-3 text-center">
          <div class="text-2xl font-bold text-blue-600">{{ lastResolutionResult.contactsLinked }}</div>
          <div class="text-xs text-gray-500">Contacts Linked</div>
        </div>
        <div class="bg-amber-50 dark:bg-amber-900/20 rounded-lg p-3 text-center">
          <div class="text-2xl font-bold text-amber-600">{{ mergeSuggestions.length }}</div>
          <div class="text-xs text-gray-500">Pending Suggestions</div>
        </div>
      </div>

      <!-- Merge Suggestions -->
      <div v-if="mergeSuggestions.length > 0" class="px-6 py-4 border-t border-gray-200 dark:border-gray-700">
        <h3 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-3">Merge Suggestions</h3>
        <div class="space-y-2">
          <div
            v-for="suggestion in mergeSuggestions"
            :key="`${suggestion.entityId1}-${suggestion.entityId2}`"
            class="flex items-center justify-between p-3 rounded-lg bg-gray-50 dark:bg-gray-700/50"
          >
            <div class="flex items-center gap-3 flex-1">
              <span :class="entityTypeBadge(suggestion.entityType)">{{ suggestion.entityType }}</span>
              <div class="flex items-center gap-2">
                <span class="font-medium">{{ suggestion.entityName1 }}</span>
                <i class="pi pi-arrows-h text-gray-400"></i>
                <span class="font-medium">{{ suggestion.entityName2 }}</span>
              </div>
              <span class="text-xs text-gray-500 ml-2">
                {{ (suggestion.similarity * 100).toFixed(0) }}% similar · {{ suggestion.reason }}
              </span>
            </div>
            <div class="flex gap-2">
              <button
                @click="handleMerge(suggestion.entityId1, suggestion.entityId2)"
                class="px-3 py-1 text-xs bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
                title="Merge (keep first)"
              >
                <i class="pi pi-check mr-1"></i>Merge
              </button>
              <button
                @click="dismissSuggestion(suggestion)"
                class="px-3 py-1 text-xs bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-lg hover:bg-gray-400 transition-colors"
                title="Dismiss"
              >
                <i class="pi pi-times"></i>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="lastResolutionResult" class="px-6 py-4 border-t border-gray-200 dark:border-gray-700">
        <p class="text-sm text-gray-400 italic text-center py-2">
          <i class="pi pi-check-circle text-green-500 mr-1"></i>
          No merge suggestions — all entities look clean
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import {
  getEmbeddingStats,
  getKgStats,
  startEmbeddingJob,
  startReembeddingJob,
  getEmbeddingJobStatus,
  cancelEmbeddingJob,
  getEmbeddingJobHistory,
  startKgExtraction,
  getKgExtractionJobs,
  getKgExtractionJobStatus,
  cancelKgExtraction,
  resetKnowledgeGraph,
  runEntityResolution,
  getMergeSuggestions,
  mergeKgEntities,
  type EmbeddingStats,
  type KgStats,
  type EmbeddingJob,
  type KgExtractionJob,
  type ResolutionResult,
  type MergeSuggestion,
} from '../services/api';

defineProps<{ hideHeader?: boolean }>();

const embeddingStats = ref<EmbeddingStats | null>(null);
const kgStats = ref<KgStats | null>(null);
const embeddingJobs = ref<EmbeddingJob[]>([]);
const extractionJobs = ref<KgExtractionJob[]>([]);
const activeEmbeddingJob = ref<EmbeddingJob | null>(null);
const activeExtractionJob = ref<KgExtractionJob | null>(null);
const resolutionRunning = ref(false);
const resettingKg = ref(false);
const lastResolutionResult = ref<ResolutionResult | null>(null);
const mergeSuggestions = ref<MergeSuggestion[]>([]);

let pollTimer: ReturnType<typeof setInterval> | null = null;

const latestKgModel = computed(() => {
  const latest = extractionJobs.value[0];
  return latest?.modelName ?? null;
});

async function loadAll() {
  try {
    const [stats, kg, embHistory, extJobs, suggestions] = await Promise.all([
      getEmbeddingStats().catch(() => null),
      getKgStats().catch(() => null),
      getEmbeddingJobHistory().catch(() => []),
      getKgExtractionJobs().catch(() => []),
      getMergeSuggestions().catch(() => []),
    ]);
    embeddingStats.value = stats;
    kgStats.value = kg;
    embeddingJobs.value = embHistory;
    extractionJobs.value = extJobs;
    mergeSuggestions.value = suggestions;

    // Detect active jobs
    const runningEmb = embHistory.find((j: EmbeddingJob) => j.status === 'RUNNING' || j.status === 'PENDING');
    if (runningEmb) {
      activeEmbeddingJob.value = runningEmb;
      startPolling();
    }
    const runningExt = extJobs.find((j: KgExtractionJob) => j.status === 'RUNNING' || j.status === 'PENDING');
    if (runningExt) {
      activeExtractionJob.value = runningExt;
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

  if (activeExtractionJob.value) {
    try {
      const job = await getKgExtractionJobStatus(activeExtractionJob.value.id);
      activeExtractionJob.value = job;
      if (job.status === 'COMPLETED' || job.status === 'FAILED') {
        activeExtractionJob.value = null;
        // Refresh stats, history, and suggestions
        const [kg, jobs, suggestions] = await Promise.all([
          getKgStats().catch(() => null),
          getKgExtractionJobs().catch(() => []),
          getMergeSuggestions().catch(() => []),
        ]);
        kgStats.value = kg;
        extractionJobs.value = jobs;
        mergeSuggestions.value = suggestions;
      } else {
        anyActive = true;
      }
    } catch {
      activeExtractionJob.value = null;
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
  }
}

async function handleReembed() {
  if (!confirm('This will delete ALL existing embeddings and re-embed every message with conversational context. This may take a long time. Continue?')) return;
  try {
    const job = await startReembeddingJob();
    activeEmbeddingJob.value = job;
    startPolling();
  } catch (e: any) {
    console.error('Failed to start re-embedding job', e);
  }
}

async function handleCancelEmbedding() {
  if (!activeEmbeddingJob.value) return;
  try {
    await cancelEmbeddingJob(activeEmbeddingJob.value.id);
    activeEmbeddingJob.value = null;
    embeddingJobs.value = await getEmbeddingJobHistory().catch(() => []);
  } catch (e) {
    console.error('Failed to cancel embedding job', e);
  }
}

async function handleStartExtraction() {
  try {
    const job = await startKgExtraction();
    activeExtractionJob.value = job;
    startPolling();
  } catch (e: any) {
    console.error('Failed to start extraction', e);
  }
}

async function handleCancelExtraction() {
  if (!activeExtractionJob.value) return;
  try {
    await cancelKgExtraction(activeExtractionJob.value.id);
    activeExtractionJob.value = null;
    extractionJobs.value = await getKgExtractionJobs().catch(() => []);
  } catch (e) {
    console.error('Failed to cancel extraction', e);
  }
}

async function handleResetKg() {
  if (!confirm('This will delete ALL Knowledge Graph data (entities, facts, job history) and allow a full re-extraction. Continue?')) return;
  resettingKg.value = true;
  try {
    const result = await resetKnowledgeGraph();
    console.log('KG reset result:', result);
    kgStats.value = { entities: 0, triples: 0 };
    extractionJobs.value = [];
    activeExtractionJob.value = null;
  } catch (e) {
    console.error('Failed to reset KG', e);
  } finally {
    resettingKg.value = false;
  }
}

async function handleRunResolution() {
  resolutionRunning.value = true;
  try {
    const result = await runEntityResolution();
    lastResolutionResult.value = result;
    mergeSuggestions.value = result.suggestions;
    // Refresh KG stats
    kgStats.value = await getKgStats().catch(() => null);
  } catch (e) {
    console.error('Failed to run entity resolution', e);
  } finally {
    resolutionRunning.value = false;
  }
}

async function handleMerge(primaryId: number, mergeFromId: number) {
  try {
    await mergeKgEntities(primaryId, mergeFromId);
    mergeSuggestions.value = mergeSuggestions.value.filter(
      s => !(s.entityId1 === primaryId && s.entityId2 === mergeFromId)
    );
    kgStats.value = await getKgStats().catch(() => null);
  } catch (e) {
    console.error('Failed to merge entities', e);
  }
}

function dismissSuggestion(suggestion: MergeSuggestion) {
  mergeSuggestions.value = mergeSuggestions.value.filter(s => s !== suggestion);
}

function statusBadgeClass(status: string): string {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium';
  switch (status) {
    case 'COMPLETED': return `${base} bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400`;
    case 'RUNNING': return `${base} bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400`;
    case 'PENDING': return `${base} bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400`;
    case 'FAILED': return `${base} bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400`;
    default: return `${base} bg-gray-100 text-gray-700`;
  }
}

function entityTypeBadge(type: string): string {
  const base = 'px-2 py-0.5 rounded-full text-xs font-medium';
  const colors: Record<string, string> = {
    PERSON: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    PLACE: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    ORGANIZATION: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400',
    VEHICLE: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
    PET: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    FOOD: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
    EVENT: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  };
  return `${base} ${colors[type] || 'bg-gray-100 text-gray-700'}`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
  });
}

onMounted(loadAll);
onUnmounted(stopPolling);
</script>
