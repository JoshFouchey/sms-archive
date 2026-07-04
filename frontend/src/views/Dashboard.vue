<template>
  <div class="h-full overflow-y-auto bg-gray-50 dark:bg-gray-900 p-4 sm:p-6">
    <div class="max-w-5xl mx-auto">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-1">Dashboard</h1>
      <p class="text-sm text-gray-500 dark:text-gray-400 mb-6">An overview of your message archive.</p>

      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center py-24 text-gray-400">
        <i class="pi pi-spin pi-spinner text-3xl"></i>
      </div>

      <!-- Error -->
      <div v-else-if="error" class="rounded-xl border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20 p-4 text-sm text-red-700 dark:text-red-300">
        Could not load analytics. <button class="underline" @click="load">Retry</button>
      </div>

      <template v-else>
        <!-- Headline stat cards -->
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4 mb-6">
          <div v-for="card in cards" :key="card.label"
               class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
            <div class="flex items-center gap-2 text-gray-500 dark:text-gray-400 text-xs font-medium uppercase tracking-wide">
              <i :class="[card.icon, 'text-base']"></i>{{ card.label }}
            </div>
            <div class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">{{ card.value.toLocaleString() }}</div>
          </div>
        </div>

        <p v-if="dateSpan" class="text-sm text-gray-500 dark:text-gray-400 mb-6">
          <i class="pi pi-calendar mr-1"></i>{{ dateSpan }}
        </p>

        <!-- Activity over time -->
        <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4 mb-6">
          <h2 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">Messages per month</h2>
          <div v-if="activityData" class="h-64">
            <SqlChart type="line" :data="activityData" :options="chartOptions" />
          </div>
          <div v-else class="text-sm text-gray-400 py-8 text-center">No activity data.</div>
        </div>

        <!-- Top contacts -->
        <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
          <h2 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">Top contacts</h2>
          <ul v-if="dashboard?.topContacts?.length" class="space-y-2">
            <li v-for="(c, i) in dashboard.topContacts" :key="c.contactId" class="flex items-center gap-3">
              <span class="w-5 text-right text-xs text-gray-400">{{ i + 1 }}</span>
              <span class="flex-1 truncate text-sm text-gray-800 dark:text-gray-200">{{ c.displayName }}</span>
              <div class="hidden sm:block w-40 h-2 rounded-full bg-gray-100 dark:bg-gray-700 overflow-hidden">
                <div class="h-full bg-gradient-to-r from-blue-500 to-cyan-400"
                     :style="{ width: barWidth(c.messageCount) }"></div>
              </div>
              <span class="w-16 text-right text-sm font-medium text-gray-700 dark:text-gray-300">{{ c.messageCount.toLocaleString() }}</span>
            </li>
          </ul>
          <div v-else class="text-sm text-gray-400 py-4 text-center">No contacts yet.</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, defineAsyncComponent } from 'vue';
import { getAnalyticsDashboard, getAllConversations, type AnalyticsDashboardDto } from '../services/api';
import { useDarkMode } from '../composables/useDarkMode';

// Lazy chunk — keeps chart.js out of the dashboard's initial load.
const SqlChart = defineAsyncComponent(() => import('../components/SqlChart.vue'));

const loading = ref(true);
const error = ref(false);
const dashboard = ref<AnalyticsDashboardDto | null>(null);
const conversationCount = ref(0);

const cards = computed(() => {
  const s = dashboard.value?.summary;
  return [
    { label: 'Messages', icon: 'pi pi-comment', value: s?.totalMessages ?? 0 },
    { label: 'Conversations', icon: 'pi pi-comments', value: conversationCount.value },
    { label: 'Contacts', icon: 'pi pi-users', value: s?.totalContacts ?? 0 },
    { label: 'Media', icon: 'pi pi-image', value: s?.totalImages ?? 0 },
  ];
});

// Aggregate per-day counts into per-month buckets for a readable long-range chart.
const monthly = computed(() => {
  const rows = dashboard.value?.messagesPerDay ?? [];
  const buckets = new Map<string, number>();
  for (const r of rows) {
    const month = (r.day || '').slice(0, 7); // yyyy-MM
    if (!month) continue;
    buckets.set(month, (buckets.get(month) ?? 0) + r.count);
  }
  return [...buckets.entries()].sort((a, b) => a[0].localeCompare(b[0]));
});

const dateSpan = computed(() => {
  const rows = dashboard.value?.messagesPerDay ?? [];
  if (!rows.length) return '';
  const days = rows.map(r => r.day).filter(Boolean).sort();
  const fmt = (d: string) => new Date(d + 'T00:00:00').toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
  return `${fmt(days[0])} – ${fmt(days[days.length - 1])}`;
});

// Reactive theme ref so the chart recolors when the user toggles dark mode.
const { isDark } = useDarkMode();

const activityData = computed(() => {
  const m = monthly.value;
  if (!m.length) return null;
  return {
    labels: m.map(([month]) => month),
    datasets: [{
      label: 'Messages',
      data: m.map(([, count]) => count),
      backgroundColor: isDark.value ? 'rgba(96, 165, 250, 0.5)' : 'rgba(59, 130, 246, 0.5)',
      borderColor: isDark.value ? 'rgb(96, 165, 250)' : 'rgb(59, 130, 246)',
      borderWidth: 2,
      tension: 0.3,
      pointRadius: 0,
    }],
  };
});

// Chart.js defaults assume a light background; set tick/grid/tooltip colors per theme.
const chartOptions = computed(() => {
  const dark = isDark.value;
  const tickColor = dark ? 'rgba(255, 255, 255, 0.65)' : 'rgba(0, 0, 0, 0.6)';
  const gridColor = dark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.08)';
  return {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: dark ? 'rgba(30, 41, 59, 0.95)' : 'rgba(0, 0, 0, 0.8)',
        titleColor: '#fff',
        bodyColor: dark ? 'rgba(255, 255, 255, 0.85)' : 'rgba(255, 255, 255, 0.9)',
      },
    },
    scales: {
      x: { ticks: { color: tickColor }, grid: { color: gridColor } },
      y: { beginAtZero: true, ticks: { color: tickColor }, grid: { color: gridColor } },
    },
  };
});

function barWidth(count: number): string {
  const max = dashboard.value?.topContacts?.[0]?.messageCount ?? 0;
  if (!max) return '0%';
  return `${Math.max(4, Math.round((count / max) * 100))}%`;
}

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const [dash, convos] = await Promise.all([
      getAnalyticsDashboard({ topLimit: 10 }),
      getAllConversations(),
    ]);
    dashboard.value = dash;
    conversationCount.value = convos.length;
  } catch (e) {
    console.error('Failed to load dashboard', e);
    error.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
