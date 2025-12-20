<template>
  <div class="space-y-8">
    <!-- Header Section -->
    <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 rounded-2xl shadow-lg p-6 text-white">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 class="text-4xl font-bold mb-2 flex items-center gap-3">
            <i class="pi pi-chart-bar"></i>
            Dashboard
          </h1>
          <p class="text-blue-100 dark:text-blue-200">View your messaging analytics and insights</p>
        </div>
        <div class="flex items-center gap-3 flex-wrap">
          <Button
            label="Refresh"
            icon="pi pi-refresh"
            size="small"
            @click="refreshDashboard"
            :loading="loading"
            class="bg-white text-blue-600 hover:bg-blue-50 border-2 border-white font-semibold shadow-md hover:shadow-lg transition-all"
          />
          <RouterLink to="/import" class="inline-flex">
            <Button
              label="Import Messages"
              icon="pi pi-upload"
              size="small"
              class="bg-white text-blue-600 hover:bg-blue-50 border-2 border-white font-semibold shadow-md hover:shadow-lg transition-all"
            />
          </RouterLink>
        </div>
      </div>
      <div v-if="lastRefreshed" class="mt-4 text-sm text-blue-100 dark:text-blue-200 flex items-center gap-2">
        <i class="pi pi-clock text-xs"></i>
        <span>Last updated: {{ lastRefreshed }}</span>
      </div>
    </div>

    <!-- Summary Cards -->
    <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <!-- Total Contacts Card -->
      <Card class="shadow-lg hover:shadow-xl transition-all duration-300 border-t-4 border-t-blue-500 hover:scale-105">
        <template #content>
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2">Total Contacts</p>
              <p class="text-5xl font-bold text-gray-900 dark:text-gray-100">{{ summary?.totalContacts ?? '–' }}</p>
            </div>
            <div class="bg-blue-100 dark:bg-blue-900/30 p-4 rounded-2xl">
              <i class="pi pi-users text-3xl text-blue-600 dark:text-blue-400"></i>
            </div>
          </div>
        </template>
      </Card>

      <!-- Total Messages Card -->
      <Card class="shadow-lg hover:shadow-xl transition-all duration-300 border-t-4 border-t-green-500 hover:scale-105">
        <template #content>
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2">Total Messages</p>
              <p class="text-5xl font-bold text-gray-900 dark:text-gray-100">{{ summary?.totalMessages?.toLocaleString() ?? '–' }}</p>
            </div>
            <div class="bg-green-100 dark:bg-green-900/30 p-4 rounded-2xl">
              <i class="pi pi-comments text-3xl text-green-600 dark:text-green-400"></i>
            </div>
          </div>
        </template>
      </Card>

      <!-- Total Images Card -->
      <Card class="shadow-lg hover:shadow-xl transition-all duration-300 border-t-4 border-t-cyan-500 hover:scale-105">
        <template #content>
          <div class="flex items-start justify-between">
            <div>
              <p class="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2">Total Images</p>
              <p class="text-5xl font-bold text-gray-900 dark:text-gray-100">{{ summary?.totalImages?.toLocaleString() ?? '–' }}</p>
            </div>
            <div class="bg-cyan-100 dark:bg-cyan-900/30 p-4 rounded-2xl">
              <i class="pi pi-images text-3xl text-cyan-600 dark:text-cyan-400"></i>
            </div>
          </div>
        </template>
      </Card>
    </div>

    <!-- Dashboard Panels -->
    <Accordion v-if="dashboard" :value="['0', '1']" multiple class="shadow-lg rounded-xl overflow-hidden">
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
              class="bg-white dark:bg-gray-800 rounded-xl p-4 shadow-sm hover:shadow-md transition-all border border-gray-200 dark:border-gray-700 group hover:scale-[1.02]"
            >
              <!-- Avatar & Contact Info -->
              <div class="flex items-center gap-3">
                <!-- Avatar -->
                <div class="w-12 h-12 rounded-full bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center text-white text-lg font-bold shrink-0">
                  {{ contact.displayName.charAt(0).toUpperCase() }}
                </div>

                <!-- Name & Count -->
                <div class="flex-1 min-w-0">
                  <h3 class="font-semibold text-sm text-gray-900 dark:text-gray-100 truncate mb-1">
                    {{ contact.displayName }}
                  </h3>
                  <div class="flex items-baseline gap-1">
                    <span class="text-2xl font-bold text-blue-600 dark:text-blue-400">
                      {{ contact.messageCount.toLocaleString() }}
                    </span>
                    <span class="text-xs text-gray-500 dark:text-gray-400">total</span>
                  </div>
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
          <div class="flex flex-col gap-6">
            <!-- Controls Section with better styling -->
            <div class="bg-white dark:bg-gray-800 rounded-xl p-4 shadow-sm border border-gray-200 dark:border-gray-700">
              <div class="flex flex-wrap items-end gap-4">
                <div class="flex flex-col flex-1 min-w-[140px]">
                  <label class="text-xs font-semibold mb-2 text-gray-700 dark:text-gray-300 uppercase tracking-wide">Start Date</label>
                  <Calendar v-model="startDate" dateFormat="yy-mm-dd" :maxDate="endDate" @update:modelValue="onRangeChange" class="w-full" />
                </div>
                <div class="flex flex-col flex-1 min-w-[140px]">
                  <label class="text-xs font-semibold mb-2 text-gray-700 dark:text-gray-300 uppercase tracking-wide">End Date</label>
                  <Calendar v-model="endDate" dateFormat="yy-mm-dd" :minDate="startDate" @update:modelValue="onRangeChange" class="w-full" />
                </div>
                <div class="flex flex-col flex-1 min-w-[180px]">
                  <label class="text-xs font-semibold mb-2 text-gray-700 dark:text-gray-300 uppercase tracking-wide">Contact</label>
                  <Select
                    :options="contactOptions"
                    optionLabel="label"
                    optionValue="value"
                    v-model="selectedContactId"
                    placeholder="All Contacts"
                    filter
                    :filterFields="['label']"
                    showClear
                    class="w-full"
                    @change="onRangeChange"
                  />
                </div>
                <Button
                  label="Apply"
                  size="small"
                  icon="pi pi-check"
                  :disabled="loading"
                  @click="fetchDashboard"
                  severity="success"
                  class="shadow-sm"
                />
              </div>

              <!-- Stats Row -->
              <div class="flex flex-wrap gap-6 mt-4 pt-4 border-t border-gray-200 dark:border-gray-700" v-if="messagesPerDayChartData">
                <div class="flex items-center gap-2">
                  <div class="bg-blue-100 dark:bg-blue-900/30 p-2 rounded-lg">
                    <i class="pi pi-hashtag text-blue-600 dark:text-blue-400"></i>
                  </div>
                  <div>
                    <p class="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide">Total</p>
                    <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ messagesPerPeriodTotal }}</p>
                  </div>
                </div>
                <div class="flex items-center gap-2">
                  <div class="bg-green-100 dark:bg-green-900/30 p-2 rounded-lg">
                    <i class="pi pi-chart-bar text-green-600 dark:text-green-400"></i>
                  </div>
                  <div>
                    <p class="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide">Avg/Day</p>
                    <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ averageMessagesPerDay }}</p>
                  </div>
                </div>
                <div class="flex items-center gap-2">
                  <div class="bg-cyan-100 dark:bg-cyan-900/30 p-2 rounded-lg">
                    <i class="pi pi-calendar text-cyan-600 dark:text-cyan-400"></i>
                  </div>
                  <div>
                    <p class="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide">Days</p>
                    <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ dayCount }}</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- Chart Section -->
            <div class="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm border border-gray-200 dark:border-gray-700">
              <Chart type="bar" :data="messagesPerDayChartData" :options="messagesPerDayChartOptions" class="w-full h-80" />
            </div>
          </div>
        </AccordionContent>
      </AccordionPanel>
    </Accordion>

    <div v-if="loading && !dashboard" class="flex items-center justify-center p-12">
      <div class="text-center">
        <i class="pi pi-spin pi-spinner text-4xl text-blue-600 dark:text-blue-400 mb-4"></i>
        <p class="text-gray-600 dark:text-gray-400">Loading dashboard...</p>
      </div>
    </div>
    <PrimeMessage v-if="error" severity="error" class="shadow-lg">{{ error }}</PrimeMessage>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { getAnalyticsDashboard, getAllContactSummaries, type AnalyticsDashboardDto, type AnalyticsSummary, type MessageCountPerDayDto, type ContactSummary } from '@/services/api';
import Card from 'primevue/card';
import Accordion from 'primevue/accordion';
import AccordionPanel from 'primevue/accordionpanel';
import AccordionHeader from 'primevue/accordionheader';
import AccordionContent from 'primevue/accordioncontent';
import Chart from 'primevue/chart';
import PrimeMessage from 'primevue/message';
import Button from 'primevue/button';
import { RouterLink } from 'vue-router';
import Calendar from 'primevue/calendar';
import Select from 'primevue/select';

const dashboard = ref<AnalyticsDashboardDto | null>(null);
const summary = computed<AnalyticsSummary | null>(() => dashboard.value?.summary ?? null);
const loading = ref(true);
const error = ref<string | null>(null);
const lastRefreshed = ref<string | null>(null);

// Contact options
const contactOptions = ref<{ label: string; value: number | null }[]>([{ label: 'All Contacts', value: null }]);
const selectedContactId = ref<number | null>(null);

// Date range (default last 30 days inclusive)
function todayDate(): Date { const d = new Date(); d.setHours(0,0,0,0); return d; }
function addDays(base: Date, delta: number): Date { const d = new Date(base); d.setDate(d.getDate() + delta); return d; }
const endDate = ref<Date>(todayDate());
const startDate = ref<Date>(addDays(endDate.value, -29));

function formatDate(d: Date): string { return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }

async function loadContacts() {
  try {
    const contacts: ContactSummary[] = await getAllContactSummaries();
    contactOptions.value = [{ label: 'All Contacts', value: null }, ...contacts.map(c => ({ label: c.contactName, value: c.contactId }))];
  } catch { /* ignore */ }
}

async function fetchDashboard() {
  loading.value = true;
  error.value = null;
  try {
    // Local safeguard: if user reversed dates, swap before requesting
    const start = startDate.value.getTime() <= endDate.value.getTime() ? startDate.value : endDate.value;
    const end = endDate.value.getTime() >= startDate.value.getTime() ? endDate.value : startDate.value;
    dashboard.value = await getAnalyticsDashboard({
      startDate: formatDate(start),
      endDate: formatDate(end),
      contactId: selectedContactId.value ?? undefined,
      topContactDays: 30,
      topLimit: 10,
      perDayDays: dayCount.value
    });
    lastRefreshed.value = new Date().toLocaleString();
  } catch (e: any) {
    error.value = e.message || 'Failed to load dashboard';
  } finally {
    loading.value = false;
  }
}

function refreshDashboard() { fetchDashboard(); }

function onRangeChange() {
  // Optionally auto-refresh; keep manual Apply for reduced calls.
}

onMounted(async () => { await Promise.all([loadContacts(), fetchDashboard()]); });

function formatLocalDateYYYYMMDD(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

const dayCount = computed(() => {
  const ms = endDate.value.getTime() - startDate.value.getTime();
  return Math.floor(ms / 86400000) + 1;
});

const enumerateDays = () => {
  const days: string[] = [];
  const cur = new Date(startDate.value);
  while (cur <= endDate.value) {
    days.push(formatLocalDateYYYYMMDD(cur));
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
    datasets: [
      {
        label: selectedContactId.value ? 'Messages (Contact)' : 'Messages (All)',
        data,
        backgroundColor: 'rgba(59,130,246,0.6)',
        borderColor: 'rgba(59,130,246,1)',
        borderWidth: 1
      }
    ]
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
      legend: { display: true },
      tooltip: { callbacks: { label: (ctx: any) => `${ctx.parsed.y} messages` } }
    },
    scales: {
      x: {
        title: { display: true, text: `Day (${days}d)` },
        ticks: {
          maxRotation: 60,
          minRotation: 45,
          autoSkip: false,
          callback: function(_value: any, idx: number) {
            const labels = (this as any).chart.data.labels as string[];
            const label = labels?.[idx] || '';
            if (step === 1) return label.slice(5);
            return idx % step === 0 ? label.slice(5) : '';
          }
        }
      },
      y: { beginAtZero: true, title: { display: true, text: 'Messages' }, precision: 0 }
    }
  };
});
</script>

<style scoped>
/* Removed custom background override for .p-card to avoid unresolved variable error */
</style>
