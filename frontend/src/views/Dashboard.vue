<template>
  <div class="space-y-6">
    <div class="flex items-center gap-4 flex-wrap">
      <h1 class="text-3xl font-bold text-gray-800 dark:text-gray-100">Dashboard</h1>
      <Button label="Refresh" icon="pi pi-refresh" size="small" @click="refreshDashboard" :loading="loading" />
      <span v-if="lastRefreshed" class="text-xs text-gray-500 dark:text-gray-400">Last updated: {{ lastRefreshed }}</span>
    </div>

    <!-- Import + Status -->
    <section class="bg-white dark:bg-gray-800 p-6 rounded-xl shadow space-y-4">
      <div class="flex items-center gap-3 flex-wrap">
        <h2 class="text-xl font-semibold m-0">Import Backup XML</h2>
        <Button label="Clear Status" size="small" severity="secondary" @click="importMessage = ''" v-if="importMessage" />
      </div>
      <FileUpload
        mode="basic"
        name="file"
        accept=".xml"
        chooseLabel="Choose File"
        :auto="true"
        :disabled="loadingImport"
        customUpload
        @uploader="onFileUpload"
      />
      <div class="flex items-center gap-3" v-if="loadingImport">
        <i class="pi pi-spin pi-spinner text-primary"></i>
        <span class="text-sm text-gray-600 dark:text-gray-300">Uploading & importing…</span>
      </div>
    </section>

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

    <!-- Top Contacts -->
    <Panel header="Top Contacts" toggleable collapsed v-if="dashboard">
      <DataTable :value="dashboard.topContacts" size="small" :rows="10" :paginator="dashboard.topContacts.length > 10">
        <Column field="displayName" header="Contact" />
        <Column field="messageCount" header="Messages" />
      </DataTable>
    </Panel>

    <!-- Messages Per Day -->
    <Panel header="Messages Per Day (Last Period)" toggleable collapsed v-if="messagesPerDayChartData">
      <Chart type="bar" :data="messagesPerDayChartData" :options="messagesPerDayChartOptions" class="w-full h-72" />
    </Panel>

    <div v-if="loading && !dashboard" class="text-sm text-gray-500">Loading dashboard...</div>
    <PrimeMessage v-if="error" severity="error">{{ error }}</PrimeMessage>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { getAnalyticsDashboard, importXml, type AnalyticsDashboardDto, type AnalyticsSummary, type MessageCountPerDayDto } from '@/services/api';
import Card from 'primevue/card';
import Panel from 'primevue/panel';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Chart from 'primevue/chart';
import PrimeMessage from 'primevue/message';
import FileUpload from 'primevue/fileupload';
import Button from 'primevue/button';
import { useToast } from 'primevue/usetoast';

const dashboard = ref<AnalyticsDashboardDto | null>(null);
const summary = computed<AnalyticsSummary | null>(() => dashboard.value?.summary ?? null);
const loading = ref(true);
const error = ref<string | null>(null);
const lastRefreshed = ref<string | null>(null);
const toast = useToast();

// Import state
const importMessage = ref("");
const importSuccess = ref<boolean>(false);
const loadingImport = ref(false);

async function fetchDashboard() {
  loading.value = true;
  try {
    dashboard.value = await getAnalyticsDashboard({ perDayDays: 30, topContactDays: 30, topLimit: 10 });
    lastRefreshed.value = new Date().toLocaleString();
  } catch (e: any) {
    error.value = e.message || 'Failed to load dashboard';
  } finally {
    loading.value = false;
  }
}

async function refreshDashboard() {
  await fetchDashboard();
}

onMounted(fetchDashboard);

function formatDateTime(iso: string) {
  if (!iso) return '';
  return new Date(iso).toLocaleString();
}

const messagesPerDayChartData = computed(() => {
  const rows: MessageCountPerDayDto[] = dashboard.value?.messagesPerDay || [];
  if (!rows.length) return null;
  return {
    labels: rows.map(r => r.day),
    datasets: [
      {
        label: 'Messages',
        data: rows.map(r => r.count),
        backgroundColor: 'rgba(59,130,246,0.6)',
        borderColor: 'rgba(59,130,246,1)',
        borderWidth: 1
      }
    ]
  };
});

const messagesPerDayChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: true } },
  scales: {
    x: { title: { display: true, text: 'Day' } },
    y: { beginAtZero: true, title: { display: true, text: 'Messages' } }
  }
};

async function onFileUpload(event: any) {
  const file = event.files?.[0];
  if (!file) return;
  loadingImport.value = true;
  try {
    const res = await importXml(file);
    if (res.ok) {
      toast.add({ severity: 'success', summary: 'Import Complete', detail: 'Messages imported successfully', life: 4000 });
      setTimeout(() => { fetchDashboard(); }, 750);
    } else {
      toast.add({ severity: 'warn', summary: 'Import Failed', detail: 'Import endpoint returned error', life: 5000 });
    }
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Import Error', detail: e?.message || 'Unexpected error', life: 6000 });
  } finally {
    loadingImport.value = false;
  }
}
</script>

<style scoped>
:deep(.p-card) { background: var(--surface-card); }
</style>
