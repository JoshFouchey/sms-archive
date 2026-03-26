<template>
  <div class="flex flex-col h-[calc(100vh-4rem)]">
    <!-- Header -->
    <div class="bg-gradient-to-r from-purple-600 to-indigo-500 dark:from-purple-700 dark:to-indigo-600 rounded-2xl shadow-lg p-6 text-white mb-4 shrink-0">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 class="text-4xl font-bold mb-2 flex items-center gap-3">
            <i class="pi pi-sitemap"></i>
            Explore
          </h1>
          <p class="text-purple-100">Knowledge graph, entities, and messaging insights</p>
        </div>
        <div class="flex items-center gap-3">
          <!-- Tab Toggle -->
          <div class="bg-white/10 backdrop-blur-sm rounded-lg p-1 flex gap-1 border border-white/20">
            <button
              @click="activeTab = 'graph'"
              :class="[
                'px-4 py-2 rounded-md text-sm font-semibold transition-all',
                activeTab === 'graph' ? 'bg-white text-purple-700 shadow-md' : 'text-white/80 hover:text-white hover:bg-white/10'
              ]"
            >
              <i class="pi pi-sitemap mr-1.5"></i>Graph
            </button>
            <button
              @click="activeTab = 'insights'; loadInsightsIfNeeded()"
              :class="[
                'px-4 py-2 rounded-md text-sm font-semibold transition-all',
                activeTab === 'insights' ? 'bg-white text-purple-700 shadow-md' : 'text-white/80 hover:text-white hover:bg-white/10'
              ]"
            >
              <i class="pi pi-chart-bar mr-1.5"></i>Insights
            </button>
          </div>
          <div class="bg-white/10 backdrop-blur-sm rounded-lg px-4 py-2 border border-white/20">
            <p class="text-xs text-purple-200">Entities</p>
            <p class="text-2xl font-bold">{{ stats.entities }}</p>
          </div>
          <div class="bg-white/10 backdrop-blur-sm rounded-lg px-4 py-2 border border-white/20">
            <p class="text-xs text-purple-200">Triples</p>
            <p class="text-2xl font-bold">{{ stats.triples }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== GRAPH TAB ==================== -->
    <template v-if="activeTab === 'graph'">
    <!-- Controls -->
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 border border-gray-200 dark:border-gray-700 mb-4 shrink-0">
      <div class="flex items-center gap-4 flex-wrap">
        <div class="flex-1 min-w-[200px]">
          <input
            v-model="entitySearch"
            type="text"
            placeholder="Search entities..."
            class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
            @keyup.enter="loadGraph()"
          />
        </div>
        <select
          v-model="typeFilter"
          class="px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
        >
          <option value="">All Types</option>
          <option v-for="t in entityTypes" :key="t" :value="t">{{ t }}</option>
        </select>
        <button
          @click="loadGraph()"
          :disabled="loading"
          class="bg-purple-600 hover:bg-purple-700 disabled:bg-purple-400 text-white rounded-lg px-4 py-2 text-sm font-semibold shadow-sm flex items-center gap-2"
        >
          <i :class="loading ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i>
          Refresh
        </button>
        <button
          @click="fitGraph"
          class="bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-800 dark:text-gray-100 rounded-lg px-4 py-2 text-sm font-semibold"
          title="Fit to screen"
        >
          <i class="pi pi-arrows-alt"></i>
        </button>
      </div>

      <!-- Type Legend -->
      <div class="flex items-center gap-3 mt-3 flex-wrap">
        <span class="text-xs text-gray-500 dark:text-gray-400 font-semibold">Types:</span>
        <span
          v-for="(color, type) in typeColors"
          :key="type"
          class="flex items-center gap-1 text-xs"
        >
          <span class="w-3 h-3 rounded-full inline-block" :style="{ backgroundColor: color }"></span>
          <span class="text-gray-600 dark:text-gray-300">{{ type }}</span>
        </span>
      </div>
    </div>

    <!-- Graph + Detail Panel -->
    <div class="flex gap-4 flex-1 min-h-0">
      <!-- Graph Container -->
      <div class="flex-1 bg-white dark:bg-gray-800 rounded-xl shadow-md border border-gray-200 dark:border-gray-700 overflow-hidden relative">
        <div v-if="loading" class="absolute inset-0 flex items-center justify-center bg-white/50 dark:bg-gray-800/50 z-10">
          <i class="pi pi-spin pi-spinner text-4xl text-purple-600"></i>
        </div>
        <div v-if="!loading && !graphData.nodes.length" class="absolute inset-0 flex items-center justify-center">
          <div class="text-center">
            <i class="pi pi-sitemap text-5xl text-gray-300 dark:text-gray-600 mb-3"></i>
            <p class="text-gray-500 dark:text-gray-400 font-medium">No graph data yet</p>
            <p class="text-gray-400 dark:text-gray-500 text-sm">Run KG extraction to populate the graph</p>
          </div>
        </div>
        <div ref="cyContainer" class="w-full h-full"></div>
      </div>

      <!-- Detail Sidebar -->
      <div
        v-if="selectedEntity"
        class="w-80 bg-white dark:bg-gray-800 rounded-xl shadow-md border border-gray-200 dark:border-gray-700 p-4 overflow-y-auto shrink-0"
      >
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100 truncate">{{ selectedEntity.label }}</h3>
          <button @click="selectedEntity = null" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
            <i class="pi pi-times"></i>
          </button>
        </div>

        <span
          class="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold mb-4"
          :style="{ backgroundColor: typeColors[selectedEntity.type] + '22', color: typeColors[selectedEntity.type] }"
        >
          {{ selectedEntity.type }}
        </span>

        <!-- Facts -->
        <div v-if="entityFacts.length" class="space-y-3 mt-4">
          <h4 class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">Facts</h4>
          <div
            v-for="fact in entityFacts"
            :key="fact.id"
            class="bg-gray-50 dark:bg-gray-900/50 rounded-lg p-3 border border-gray-200 dark:border-gray-700 text-sm"
          >
            <div class="flex items-center gap-1 flex-wrap">
              <span class="font-semibold text-gray-900 dark:text-gray-100">{{ fact.subjectName }}</span>
              <span class="text-purple-600 dark:text-purple-400 font-mono text-xs">{{ fact.predicate }}</span>
              <span class="font-semibold text-gray-900 dark:text-gray-100">{{ fact.objectName || fact.objectValue }}</span>
            </div>
            <div class="flex items-center gap-2 mt-1">
              <span class="text-[10px] text-gray-400">{{ (fact.confidence * 100).toFixed(0) }}% confidence</span>
              <span v-if="fact.isVerified" class="text-[10px] text-green-600">✓ Verified</span>
            </div>
          </div>
        </div>
        <div v-else-if="factsLoading" class="flex justify-center py-4">
          <i class="pi pi-spin pi-spinner text-purple-600"></i>
        </div>
        <div v-else class="text-sm text-gray-400 dark:text-gray-500 mt-4">No facts found for this entity</div>
      </div>
    </div>
    </template>

    <!-- ==================== INSIGHTS TAB ==================== -->
    <div v-if="activeTab === 'insights'" class="flex-1 overflow-y-auto space-y-6">
      <!-- Stats Row -->
      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
          <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ insightsSummary?.totalMessages?.toLocaleString() ?? '—' }}</div>
          <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-comments text-green-500"></i> Messages</div>
        </div>
        <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
          <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ insightsSummary?.totalContacts ?? '—' }}</div>
          <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-users text-blue-500"></i> Contacts</div>
        </div>
        <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
          <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ insightsSummary?.totalImages?.toLocaleString() ?? '—' }}</div>
          <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-images text-cyan-500"></i> Images</div>
        </div>
        <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
          <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ embeddingPct }}%</div>
          <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-database text-violet-500"></i> Embedded</div>
        </div>
        <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
          <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ stats.entities }}</div>
          <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-sitemap text-purple-500"></i> Entities</div>
        </div>
        <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
          <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ stats.triples }}</div>
          <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-link text-amber-500"></i> Facts</div>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- Recent Discoveries -->
        <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center gap-3">
            <div class="p-2 bg-amber-100 dark:bg-amber-900/30 rounded-lg"><i class="pi pi-bolt text-amber-600 dark:text-amber-400"></i></div>
            <div><h2 class="font-semibold text-lg">Recent Discoveries</h2><p class="text-xs text-gray-500 dark:text-gray-400">Latest facts extracted from your messages</p></div>
          </div>
          <div class="divide-y divide-gray-100 dark:divide-gray-700 max-h-[400px] overflow-y-auto">
            <div v-if="recentTriples.length === 0" class="px-6 py-8 text-center text-gray-400 italic text-sm">
              <i class="pi pi-inbox text-2xl mb-2 block"></i>No facts extracted yet
            </div>
            <div v-for="triple in recentTriples" :key="triple.id" class="px-6 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
              <div class="flex items-start gap-2">
                <span class="font-medium text-sm text-blue-700 dark:text-blue-300">{{ triple.subjectName }}</span>
                <span :class="['text-xs px-1.5 py-0.5 rounded font-mono', triple.isCanonical ? 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400' : 'bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800']" :title="triple.isCanonical ? '' : 'Raw predicate — not yet normalized'">{{ triple.predicate.replace(/_/g, ' ') }}</span>
                <span class="font-medium text-sm text-purple-700 dark:text-purple-300">{{ triple.objectName || triple.objectValue || '—' }}</span>
              </div>
              <div class="flex items-center gap-2 mt-1">
                <span class="text-[10px] text-gray-400">{{ formatTimeAgo(triple.createdAt) }}</span>
                <span v-if="triple.confidence >= 0.8" class="text-[10px] text-green-500"><i class="pi pi-check-circle"></i> High confidence</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Contact Explorer -->
        <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center gap-3">
            <div class="p-2 bg-blue-100 dark:bg-blue-900/30 rounded-lg"><i class="pi pi-user text-blue-600 dark:text-blue-400"></i></div>
            <div><h2 class="font-semibold text-lg">What You Know About…</h2><p class="text-xs text-gray-500 dark:text-gray-400">Select a contact to see their KG profile</p></div>
          </div>
          <div class="px-6 py-3 border-b border-gray-100 dark:border-gray-700">
            <select v-model="selectedContactId" @change="loadContactFacts" class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none">
              <option :value="null">Choose a contact…</option>
              <option v-for="co in contactOptions" :key="co.value" :value="co.value">{{ co.label }}</option>
            </select>
          </div>
          <div class="max-h-[340px] overflow-y-auto">
            <div v-if="!selectedContactId" class="px-6 py-8 text-center text-gray-400 text-sm">
              <i class="pi pi-arrow-up text-2xl mb-2 block"></i>Pick a contact above
            </div>
            <div v-else-if="contactFactsLoading" class="px-6 py-8 text-center"><i class="pi pi-spin pi-spinner text-2xl text-blue-500"></i></div>
            <div v-else-if="contactFacts.length === 0" class="px-6 py-8 text-center text-gray-400 text-sm italic">No facts found for this contact</div>
            <div v-else class="divide-y divide-gray-100 dark:divide-gray-700">
              <div v-for="fact in contactFacts" :key="fact.id" class="px-6 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                <div class="flex items-start gap-2">
                  <span class="font-medium text-sm text-blue-700 dark:text-blue-300">{{ fact.subjectName }}</span>
                  <span :class="['text-xs px-1.5 py-0.5 rounded font-mono', fact.isCanonical ? 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400' : 'bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800']" :title="fact.isCanonical ? '' : 'Raw predicate — not yet normalized'">{{ fact.predicate.replace(/_/g, ' ') }}</span>
                  <span class="font-medium text-sm text-purple-700 dark:text-purple-300">{{ fact.objectName || fact.objectValue || '—' }}</span>
                </div>
                <span :class="['text-[10px] px-1.5 py-0.5 rounded-full font-medium', fact.confidence >= 0.8 ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400']">
                  {{ (fact.confidence * 100).toFixed(0) }}% conf
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch, computed } from 'vue';
import cytoscape from 'cytoscape';
import {
  getKnowledgeGraph,
  getKgStats,
  getKgEntityFacts,
  getAnalyticsDashboard,
  getAllContactSummaries,
  getEmbeddingStats,
  getRecentTriples,
  getContactFacts,
  type KnowledgeGraph,
  type GraphNode,
  type KgTriple,
  type AnalyticsSummary,
  type ContactSummary,
  type EmbeddingStats,
} from '../services/api';

// Tab state
const activeTab = ref<'graph' | 'insights'>('graph');

const typeColors: Record<string, string> = {
  PERSON: '#3B82F6',
  PLACE: '#10B981',
  ORGANIZATION: '#F59E0B',
  OBJECT: '#8B5CF6',
  EVENT: '#EF4444',
  CONCEPT: '#6B7280',
  FOOD: '#F97316',
  VEHICLE: '#06B6D4',
  PET: '#EC4899',
  MEDICAL: '#DC2626',
  DATE: '#84CC16',
};

const entityTypes = Object.keys(typeColors);

const cyContainer = ref<HTMLDivElement | null>(null);
let cy: cytoscape.Core | null = null;

const graphData = ref<KnowledgeGraph>({ nodes: [], edges: [] });
const stats = ref({ entities: 0, triples: 0 });
const loading = ref(false);
const entitySearch = ref('');
const typeFilter = ref('');
const selectedEntity = ref<GraphNode | null>(null);
const entityFacts = ref<KgTriple[]>([]);
const factsLoading = ref(false);

onMounted(async () => {
  await loadStats();
  await loadGraph();
});

async function loadStats() {
  try {
    stats.value = await getKgStats();
  } catch { /* ignore */ }
}

async function loadGraph(centeredEntityId?: number) {
  loading.value = true;
  try {
    graphData.value = await getKnowledgeGraph(centeredEntityId, 2, 100);
    await nextTick();
    renderGraph();
  } catch (e: any) {
    console.error('Failed to load graph:', e);
  } finally {
    loading.value = false;
  }
}

function renderGraph() {
  if (!cyContainer.value) return;

  const filtered = typeFilter.value
    ? graphData.value.nodes.filter(n => n.type === typeFilter.value)
    : graphData.value.nodes;

  const nodeIds = new Set(filtered.map(n => n.id));

  const elements: cytoscape.ElementDefinition[] = [
    ...filtered.map(n => ({
      data: {
        id: n.id,
        label: n.label,
        type: n.type,
        linkedContactId: n.linkedContactId,
      },
    })),
    ...graphData.value.edges
      .filter(e => nodeIds.has(e.source) && nodeIds.has(e.target))
      .map(e => ({
        data: {
          id: `${e.source}-${e.label}-${e.target}`,
          source: e.source,
          target: e.target,
          label: e.label,
          confidence: e.confidence,
        },
      })),
  ];

  if (cy) cy.destroy();

  cy = cytoscape({
    container: cyContainer.value,
    elements,
    style: [
      {
        selector: 'node',
        style: {
          'label': 'data(label)',
          'background-color': (ele: any) => typeColors[ele.data('type')] || '#6B7280',
          'color': '#374151',
          'text-valign': 'bottom',
          'text-halign': 'center',
          'font-size': '11px',
          'text-margin-y': 6,
          'width': 35,
          'height': 35,
          'border-width': 2,
          'border-color': '#fff',
          'text-max-width': '80px',
          'text-wrap': 'ellipsis',
        } as any,
      },
      {
        selector: 'node:selected',
        style: {
          'border-width': 4,
          'border-color': '#7C3AED',
          'width': 45,
          'height': 45,
        },
      },
      {
        selector: 'edge',
        style: {
          'label': 'data(label)',
          'width': 2,
          'line-color': '#D1D5DB',
          'target-arrow-color': '#9CA3AF',
          'target-arrow-shape': 'triangle',
          'curve-style': 'bezier',
          'font-size': '9px',
          'color': '#9CA3AF',
          'text-rotation': 'autorotate',
          'text-margin-y': -8,
        },
      },
    ],
    layout: {
      name: 'cose',
      animate: true,
      animationDuration: 500,
      nodeRepulsion: () => 8000,
      idealEdgeLength: () => 120,
      gravity: 0.3,
      numIter: 300,
    } as any,
  });

  cy.on('tap', 'node', async (evt) => {
    const node = evt.target;
    selectedEntity.value = {
      id: node.data('id'),
      label: node.data('label'),
      type: node.data('type'),
      linkedContactId: node.data('linkedContactId'),
    };
    await loadEntityFacts(parseInt(node.data('id')));
  });

  cy.on('tap', (evt) => {
    if (evt.target === cy) {
      selectedEntity.value = null;
    }
  });
}

async function loadEntityFacts(entityId: number) {
  factsLoading.value = true;
  entityFacts.value = [];
  try {
    entityFacts.value = await getKgEntityFacts(entityId);
  } catch { /* ignore */ }
  finally { factsLoading.value = false; }
}

function fitGraph() {
  if (cy) cy.fit(undefined, 30);
}

watch(typeFilter, () => {
  if (graphData.value.nodes.length) renderGraph();
});

// ==================== INSIGHTS TAB ====================
const insightsSummary = ref<AnalyticsSummary | null>(null);
const insightsEmbedding = ref<EmbeddingStats | null>(null);
const recentTriples = ref<KgTriple[]>([]);
const contactOptions = ref<{ label: string; value: number }[]>([]);
const selectedContactId = ref<number | null>(null);
const contactFacts = ref<KgTriple[]>([]);
const contactFactsLoading = ref(false);
let insightsLoaded = false;

const embeddingPct = computed(() => {
  if (!insightsEmbedding.value) return '—';
  return insightsEmbedding.value.percentComplete.toFixed(0);
});

function formatTimeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(iso).toLocaleDateString();
}

async function loadInsightsIfNeeded() {
  if (insightsLoaded) return;
  insightsLoaded = true;
  await Promise.all([
    loadInsightsSummary(),
    loadInsightsAiStats(),
    loadRecentTriples(),
    loadInsightsContacts(),
  ]);
}

async function loadInsightsSummary() {
  try {
    const dash = await getAnalyticsDashboard({});
    insightsSummary.value = dash.summary;
  } catch { /* ignore */ }
}

async function loadInsightsAiStats() {
  insightsEmbedding.value = await getEmbeddingStats().catch(() => null);
}

async function loadRecentTriples() {
  try { recentTriples.value = await getRecentTriples(20); } catch { /* ignore */ }
}

async function loadInsightsContacts() {
  try {
    const contacts: ContactSummary[] = await getAllContactSummaries();
    contactOptions.value = contacts.map(c => ({ label: c.contactName, value: c.contactId }));
  } catch { /* ignore */ }
}

async function loadContactFacts() {
  if (!selectedContactId.value) { contactFacts.value = []; return; }
  contactFactsLoading.value = true;
  try { contactFacts.value = await getContactFacts(selectedContactId.value); }
  catch { contactFacts.value = []; }
  finally { contactFactsLoading.value = false; }
}
</script>
