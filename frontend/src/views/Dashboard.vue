<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="bg-gradient-to-r from-indigo-600 to-purple-500 dark:from-indigo-700 dark:to-purple-600 rounded-2xl shadow-lg p-6 text-white">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 class="text-3xl font-bold flex items-center gap-3">
            <i class="pi pi-sparkles"></i>
            Insights
          </h1>
          <p class="text-indigo-100 mt-1">Your messaging intelligence at a glance</p>
        </div>
        <div class="flex items-center gap-3">
          <button
            @click="refreshAll"
            :disabled="loading"
            class="px-4 py-2 bg-white/20 hover:bg-white/30 backdrop-blur-sm rounded-lg text-sm font-semibold transition-all flex items-center gap-2"
          >
            <i :class="loading ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i>
            Refresh
          </button>
        </div>
      </div>
    </div>

    <!-- Stats Row -->
    <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
      <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
        <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ summary?.totalMessages?.toLocaleString() ?? '—' }}</div>
        <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-comments text-green-500"></i> Messages</div>
      </div>
      <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
        <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ summary?.totalContacts ?? '—' }}</div>
        <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-users text-blue-500"></i> Contacts</div>
      </div>
      <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
        <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ summary?.totalImages?.toLocaleString() ?? '—' }}</div>
        <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-images text-cyan-500"></i> Images</div>
      </div>
      <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
        <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ embeddingPct }}%</div>
        <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-database text-violet-500"></i> Embedded</div>
      </div>
      <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
        <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ kgStats?.entities ?? '—' }}</div>
        <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-sitemap text-purple-500"></i> Entities</div>
      </div>
      <div class="bg-white dark:bg-gray-800 rounded-xl p-4 border border-gray-200 dark:border-gray-700 shadow-sm">
        <div class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ kgStats?.triples ?? '—' }}</div>
        <div class="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 mt-1"><i class="pi pi-link text-amber-500"></i> Facts</div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- Recent Discoveries -->
      <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
        <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center gap-3">
          <div class="p-2 bg-amber-100 dark:bg-amber-900/30 rounded-lg">
            <i class="pi pi-bolt text-amber-600 dark:text-amber-400"></i>
          </div>
          <div>
            <h2 class="font-semibold text-lg">Recent Discoveries</h2>
            <p class="text-xs text-gray-500 dark:text-gray-400">Latest facts extracted from your messages</p>
          </div>
        </div>
        <div class="divide-y divide-gray-100 dark:divide-gray-700 max-h-[400px] overflow-y-auto">
          <div v-if="recentTriples.length === 0" class="px-6 py-8 text-center text-gray-400 italic text-sm">
            <i class="pi pi-inbox text-2xl mb-2 block"></i>
            No facts extracted yet — run KG extraction from AI Settings
          </div>
          <div
            v-for="triple in recentTriples"
            :key="triple.id"
            class="px-6 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <div class="flex items-start gap-2">
              <span class="font-medium text-sm text-blue-700 dark:text-blue-300">{{ triple.subjectName }}</span>
              <span class="text-xs px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 rounded text-gray-600 dark:text-gray-400 font-mono">{{ formatPredicate(triple.predicate) }}</span>
              <span class="font-medium text-sm text-purple-700 dark:text-purple-300">{{ triple.objectName || triple.objectValue || '—' }}</span>
            </div>
            <div class="flex items-center gap-2 mt-1">
              <span class="text-[10px] text-gray-400">{{ formatTimeAgo(triple.createdAt) }}</span>
              <span v-if="triple.confidence >= 0.8" class="text-[10px] text-green-500">
                <i class="pi pi-check-circle"></i> High confidence
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Contact Explorer -->
      <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
        <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center gap-3">
          <div class="p-2 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
            <i class="pi pi-user text-blue-600 dark:text-blue-400"></i>
          </div>
          <div class="flex-1">
            <h2 class="font-semibold text-lg">What You Know About…</h2>
            <p class="text-xs text-gray-500 dark:text-gray-400">Select a contact to see their KG profile</p>
          </div>
        </div>
        <div class="px-6 py-3 border-b border-gray-100 dark:border-gray-700">
          <select
            v-model="selectedContactId"
            @change="loadContactFacts"
            class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
          >
            <option :value="null">Choose a contact…</option>
            <option
              v-for="contact in contactOptions"
              :key="contact.value"
              :value="contact.value"
            >
              {{ contact.label }}
            </option>
          </select>
        </div>
        <div class="max-h-[340px] overflow-y-auto">
          <div v-if="!selectedContactId" class="px-6 py-8 text-center text-gray-400 text-sm">
            <i class="pi pi-arrow-up text-2xl mb-2 block"></i>
            Pick a contact above to see what your messages reveal about them
          </div>
          <div v-else-if="contactFactsLoading" class="px-6 py-8 text-center">
            <i class="pi pi-spin pi-spinner text-2xl text-blue-500"></i>
          </div>
          <div v-else-if="contactFacts.length === 0" class="px-6 py-8 text-center text-gray-400 text-sm italic">
            <i class="pi pi-question-circle text-2xl mb-2 block"></i>
            No facts found for this contact yet
          </div>
          <div v-else class="divide-y divide-gray-100 dark:divide-gray-700">
            <div
              v-for="fact in contactFacts"
              :key="fact.id"
              class="px-6 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
            >
              <div class="flex items-start gap-2">
                <span class="font-medium text-sm text-blue-700 dark:text-blue-300">{{ fact.subjectName }}</span>
                <span class="text-xs px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 rounded text-gray-600 dark:text-gray-400 font-mono">{{ formatPredicate(fact.predicate) }}</span>
                <span class="font-medium text-sm text-purple-700 dark:text-purple-300">{{ fact.objectName || fact.objectValue || '—' }}</span>
              </div>
              <div class="flex items-center gap-2 mt-1">
                <span :class="[
                  'text-[10px] px-1.5 py-0.5 rounded-full font-medium',
                  fact.confidence >= 0.8 ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' :
                  fact.confidence >= 0.5 ? 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400' :
                  'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
                ]">
                  {{ (fact.confidence * 100).toFixed(0) }}% conf
                </span>
                <span v-if="fact.isVerified" class="text-[10px] text-green-500"><i class="pi pi-verified"></i> Verified</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Top Contacts & Messages Per Day -->
    <Accordion v-if="dashboard" :value="[]" multiple class="shadow-sm rounded-xl overflow-hidden">
      <!-- Top Contacts -->
      <AccordionPanel value="0">
        <AccordionHeader class="bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
          <div class="flex items-center gap-2">
            <i class="pi pi-star-fill text-yellow-500"></i>
            <span class="font-semibold">Top Contacts</span>
          </div>
        </AccordionHeader>
        <AccordionContent class="bg-gray-50 dark:bg-gray-900/50">
          <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 p-4">
            <div
              v-for="contact in dashboard.topContacts.slice(0, 12)"
              :key="contact.contactId"
              class="bg-white dark:bg-gray-800 rounded-xl p-4 shadow-sm hover:shadow-md transition-all border border-gray-200 dark:border-gray-700 hover:scale-[1.02]"
            >
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center text-white text-sm font-bold shrink-0">
                  {{ contact.displayName.charAt(0).toUpperCase() }}
                </div>
                <div class="flex-1 min-w-0">
                  <h3 class="font-semibold text-sm text-gray-900 dark:text-gray-100 truncate">{{ contact.displayName }}</h3>
                  <span class="text-lg font-bold text-blue-600 dark:text-blue-400">{{ contact.messageCount.toLocaleString() }}</span>
                  <span class="text-xs text-gray-500 ml-1">msgs</span>
                </div>
              </div>
            </div>
          </div>
        </AccordionContent>
      </AccordionPanel>

      <!-- Messages Per Day -->
      <AccordionPanel value="1">
        <AccordionHeader class="bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
          <div class="flex items-center gap-2">
            <i class="pi pi-chart-line text-green-500"></i>
            <span class="font-semibold">Messages Per Day</span>
          </div>
        </AccordionHeader>
        <AccordionContent class="bg-gray-50 dark:bg-gray-900/50">
          <div class="flex flex-col gap-4 p-4">
            <!-- Controls -->
            <div class="bg-white dark:bg-gray-800 rounded-xl p-4 shadow-sm border border-gray-200 dark:border-gray-700">
              <div class="flex flex-wrap items-end gap-4">
                <div class="flex flex-col flex-1 min-w-[140px]">
                  <label class="text-xs font-semibold mb-1 text-gray-700 dark:text-gray-300 uppercase tracking-wide">Start</label>
                  <Calendar v-model="startDate" dateFormat="yy-mm-dd" :maxDate="endDate" class="w-full" />
                </div>
                <div class="flex flex-col flex-1 min-w-[140px]">
                  <label class="text-xs font-semibold mb-1 text-gray-700 dark:text-gray-300 uppercase tracking-wide">End</label>
                  <Calendar v-model="endDate" dateFormat="yy-mm-dd" :minDate="startDate" class="w-full" />
                </div>
                <Button label="Apply" size="small" icon="pi pi-check" :disabled="loading" @click="fetchDashboard" severity="success" />
              </div>
              <div class="flex gap-6 mt-3 pt-3 border-t border-gray-200 dark:border-gray-700 text-sm">
                <span><strong>{{ messagesPerPeriodTotal }}</strong> total</span>
                <span><strong>{{ averageMessagesPerDay }}</strong> avg/day</span>
                <span><strong>{{ dayCount }}</strong> days</span>
              </div>
            </div>
            <!-- Chart -->
            <div class="bg-white dark:bg-gray-800 rounded-xl p-4 shadow-sm border border-gray-200 dark:border-gray-700">
              <Chart type="bar" :data="messagesPerDayChartData" :options="messagesPerDayChartOptions" class="w-full h-64" />
            </div>
          </div>
        </AccordionContent>
      </AccordionPanel>
    </Accordion>

    <div v-if="loading && !dashboard" class="flex items-center justify-center p-12">
      <i class="pi pi-spin pi-spinner text-4xl text-indigo-500 mb-3"></i>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import {
  getAnalyticsDashboard,
  getAllContactSummaries,
  getEmbeddingStats,
  getKgStats,
  getRecentTriples,
  getContactFacts,
  type AnalyticsDashboardDto,
  type AnalyticsSummary,
  type MessageCountPerDayDto,
  type ContactSummary,
  type EmbeddingStats,
  type KgStats,
  type KgTriple,
} from '@/services/api';
import Accordion from 'primevue/accordion';
import AccordionPanel from 'primevue/accordionpanel';
import AccordionHeader from 'primevue/accordionheader';
import AccordionContent from 'primevue/accordioncontent';
import Chart from 'primevue/chart';
import Button from 'primevue/button';
import Calendar from 'primevue/calendar';

// Core state
const dashboard = ref<AnalyticsDashboardDto | null>(null);
const summary = computed<AnalyticsSummary | null>(() => dashboard.value?.summary ?? null);
const embeddingStats = ref<EmbeddingStats | null>(null);
const kgStats = ref<KgStats | null>(null);
const recentTriples = ref<KgTriple[]>([]);
const loading = ref(true);

// Contact explorer
const contactOptions = ref<{ label: string; value: number }[]>([]);
const selectedContactId = ref<number | null>(null);
const contactFacts = ref<KgTriple[]>([]);
const contactFactsLoading = ref(false);

// Chart date range
function todayDate(): Date { const d = new Date(); d.setHours(0, 0, 0, 0); return d; }
function addDays(base: Date, delta: number): Date { const d = new Date(base); d.setDate(d.getDate() + delta); return d; }
const endDate = ref<Date>(todayDate());
const startDate = ref<Date>(addDays(endDate.value, -29));

const embeddingPct = computed(() => {
  if (!embeddingStats.value) return '—';
  return embeddingStats.value.percentComplete.toFixed(0);
});

function formatDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function formatPredicate(pred: string): string {
  return pred.replace(/_/g, ' ');
}

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

async function refreshAll() {
  loading.value = true;
  await Promise.all([
    fetchDashboard(),
    loadAiStats(),
    loadRecentTriples(),
  ]);
  loading.value = false;
}

async function fetchDashboard() {
  try {
    const start = startDate.value.getTime() <= endDate.value.getTime() ? startDate.value : endDate.value;
    const end = endDate.value.getTime() >= startDate.value.getTime() ? endDate.value : startDate.value;
    dashboard.value = await getAnalyticsDashboard({
      startDate: formatDate(start),
      endDate: formatDate(end),
      topContactDays: 30,
      topLimit: 10,
      perDayDays: dayCount.value,
    });
  } catch { /* ignore */ }
}

async function loadAiStats() {
  const [emb, kg] = await Promise.all([
    getEmbeddingStats().catch(() => null),
    getKgStats().catch(() => null),
  ]);
  embeddingStats.value = emb;
  kgStats.value = kg;
}

async function loadRecentTriples() {
  try {
    recentTriples.value = await getRecentTriples(20);
  } catch { /* AI may be disabled */ }
}

async function loadContacts() {
  try {
    const contacts: ContactSummary[] = await getAllContactSummaries();
    contactOptions.value = contacts.map(c => ({ label: c.contactName, value: c.contactId }));
  } catch { /* ignore */ }
}

async function loadContactFacts() {
  if (!selectedContactId.value) {
    contactFacts.value = [];
    return;
  }
  contactFactsLoading.value = true;
  try {
    contactFacts.value = await getContactFacts(selectedContactId.value);
  } catch {
    contactFacts.value = [];
  } finally {
    contactFactsLoading.value = false;
  }
}

onMounted(async () => {
  await Promise.all([
    fetchDashboard(),
    loadAiStats(),
    loadRecentTriples(),
    loadContacts(),
  ]);
  loading.value = false;
});

// Chart computations
const dayCount = computed(() => {
  const ms = endDate.value.getTime() - startDate.value.getTime();
  return Math.floor(ms / 86400000) + 1;
});

function formatLocalDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const enumerateDays = () => {
  const days: string[] = [];
  const cur = new Date(startDate.value);
  while (cur <= endDate.value) {
    days.push(formatLocalDate(cur));
    cur.setDate(cur.getDate() + 1);
  }
  return days;
};

const messagesPerDayChartData = computed(() => {
  const rows: MessageCountPerDayDto[] = dashboard.value?.messagesPerDay || [];
  const counts = new Map<string, number>();
  for (const r of rows) counts.set(r.day, r.count as number);
  const orderedDays = enumerateDays();
  const data = orderedDays.map(day => counts.get(day) ?? 0);
  return {
    labels: orderedDays,
    datasets: [{
      label: 'Messages',
      data,
      backgroundColor: 'rgba(99,102,241,0.5)',
      borderColor: 'rgba(99,102,241,1)',
      borderWidth: 1,
    }],
  };
});

const messagesPerPeriodTotal = computed(() => {
  const data = messagesPerDayChartData.value?.datasets?.[0]?.data as number[] | undefined;
  return data?.reduce((a, b) => a + b, 0) ?? 0;
});

const averageMessagesPerDay = computed(() => {
  const dc = dayCount.value;
  if (!dc) return 0;
  const avg = messagesPerPeriodTotal.value / dc;
  return avg < 10 ? avg.toFixed(1) : Math.round(avg);
});

const messagesPerDayChartOptions = computed(() => {
  const days = dayCount.value;
  const step = days <= 14 ? 1 : days <= 45 ? 2 : days <= 90 ? 3 : 5;
  return {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: { callbacks: { label: (ctx: any) => `${ctx.parsed.y} messages` } },
    },
    scales: {
      x: {
        ticks: {
          maxRotation: 60, minRotation: 45, autoSkip: false,
          callback: function(_value: any, idx: number) {
            const labels = (this as any).chart.data.labels as string[];
            return idx % step === 0 ? (labels?.[idx] || '').slice(5) : '';
          },
        },
      },
      y: { beginAtZero: true, precision: 0 },
    },
  };
});
</script>
