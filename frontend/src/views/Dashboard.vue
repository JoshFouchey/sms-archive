<template>
  <div class="space-y-6">
    <div class="flex items-center gap-4 flex-wrap">
      <h1 class="text-3xl font-bold text-gray-800 dark:text-gray-100">Dashboard</h1>
      <Button label="Refresh" icon="pi pi-refresh" size="small" @click="refreshDashboard" :loading="loading" />
      <RouterLink to="/import" class="inline-flex">
        <Button label="Import Messages" icon="pi pi-upload" size="small" severity="help" />
      </RouterLink>
      <span v-if="lastRefreshed" class="text-xs text-gray-500 dark:text-gray-400">Last updated: {{ lastRefreshed }}</span>
    </div>

    <!-- Summary Cards -->
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <Card class="shadow">
        <template #title>Total Contacts</template>
        <template #content>
          <p class="text-4xl font-semibold">{{ summary?.totalContacts ?? '–' }}</p>
        </template>
      </Card>
      <Card class="shadow">
        <template #title>Total Messages</template>
        <template #content>
          <p class="text-4xl font-semibold">{{ summary?.totalMessages ?? '–' }}</p>
        </template>
      </Card>
      <Card class="shadow">
        <template #title>Total Images</template>
        <template #content>
          <p class="text-4xl font-semibold">{{ summary?.totalImages ?? '–' }}</p>
        </template>
      </Card>
    </div>

    <!-- Dashboard Panels -->
    <Accordion v-if="dashboard" :value="[]" multiple>
      <!-- Top Contacts -->
      <AccordionPanel value="0">
        <AccordionHeader>Top Contacts</AccordionHeader>
        <AccordionContent>
          <DataTable :value="dashboard.topContacts" size="small" :rows="10" :paginator="dashboard.topContacts.length > 10">
            <Column field="displayName" header="Contact" />
            <Column field="messageCount" header="Messages" />
          </DataTable>
        </AccordionContent>
      </AccordionPanel>

      <!-- Messages Per Day -->
      <AccordionPanel value="1">
        <AccordionHeader>Messages Per Day</AccordionHeader>
        <AccordionContent>
          <div class="flex flex-col gap-4">
            <div class="flex flex-wrap items-end gap-4 justify-between">
              <div class="flex flex-wrap items-end gap-4">
                <div class="flex flex-col">
                  <label class="text-xs font-medium mb-1">Start Date</label>
                  <Calendar v-model="startDate" dateFormat="yy-mm-dd" :maxDate="endDate" @update:modelValue="onRangeChange" />
                </div>
                <div class="flex flex-col">
                  <label class="text-xs font-medium mb-1">End Date</label>
                  <Calendar v-model="endDate" dateFormat="yy-mm-dd" :minDate="startDate" @update:modelValue="onRangeChange" />
                </div>
                <div class="flex flex-col min-w-48">
                  <label class="text-xs font-medium mb-1">Contact</label>
                  <Dropdown :options="contactOptions" optionLabel="label" optionValue="value" v-model="selectedContactId" placeholder="All Contacts" class="min-w-48" @change="onRangeChange" />
                </div>
                <div class="flex flex-col">
                  <label class="text-xs font-medium mb-1 invisible">Refresh</label>
                  <Button label="Apply" size="small" icon="pi pi-check" :disabled="loading" @click="fetchDashboard" />
                </div>
              </div>
              <div class="text-sm text-gray-600 dark:text-gray-300 flex gap-4" v-if="messagesPerDayChartData">
                <span>Total: <strong>{{ messagesPerPeriodTotal }}</strong></span>
                <span>Avg/Day: <strong>{{ averageMessagesPerDay }}</strong></span>
                <span>Days: <strong>{{ dayCount }}</strong></span>
              </div>
            </div>
            <Chart type="bar" :data="messagesPerDayChartData" :options="messagesPerDayChartOptions" class="w-full h-72" />
          </div>
        </AccordionContent>
      </AccordionPanel>
    </Accordion>

    <div v-if="loading && !dashboard" class="text-sm text-gray-500">Loading dashboard...</div>
    <PrimeMessage v-if="error" severity="error">{{ error }}</PrimeMessage>
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
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Chart from 'primevue/chart';
import PrimeMessage from 'primevue/message';
import Button from 'primevue/button';
import { RouterLink } from 'vue-router';
import Calendar from 'primevue/calendar';
import Dropdown from 'primevue/dropdown';

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
