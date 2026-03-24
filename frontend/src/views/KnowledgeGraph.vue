<template>
  <div class="flex flex-col h-[calc(100vh-4rem)]">
    <!-- Header -->
    <div class="bg-gradient-to-r from-purple-600 to-indigo-500 dark:from-purple-700 dark:to-indigo-600 rounded-2xl shadow-lg p-6 text-white mb-4 shrink-0">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 class="text-4xl font-bold mb-2 flex items-center gap-3">
            <i class="pi pi-sitemap"></i>
            Knowledge Graph
          </h1>
          <p class="text-purple-100">Explore entities and relationships extracted from your messages</p>
        </div>
        <div class="flex items-center gap-3">
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue';
import cytoscape from 'cytoscape';
import {
  getKnowledgeGraph,
  getKgStats,
  getKgEntityFacts,
  type KnowledgeGraph,
  type GraphNode,
  type KgTriple,
} from '../services/api';

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
</script>
