<template>
  <div class="space-y-6">
    <!-- Header -->
    <PageHeader title="Admin" subtitle="Import data and manage AI embeddings" icon="pi pi-cog" />

    <!-- Tabs -->
    <TabView v-model:activeIndex="activeTab" class="admin-tabs">
      <TabPanel value="0">
        <template #header>
          <div class="flex items-center gap-2">
            <i class="pi pi-upload"></i>
            <span class="hidden sm:inline">Import</span>
          </div>
        </template>
        <ImportView :hide-header="true" />
      </TabPanel>

      <TabPanel value="1">
        <template #header>
          <div class="flex items-center gap-2">
            <i class="pi pi-microchip"></i>
            <span class="hidden sm:inline">AI Settings</span>
          </div>
        </template>
        <AiSettingsView :hide-header="true" />
      </TabPanel>
    </TabView>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import TabView from 'primevue/tabview';
import TabPanel from 'primevue/tabpanel';
import ImportView from './Import.vue';
import AiSettingsView from './AiSettings.vue';

const activeTab = ref(0);
</script>

<style>
.admin-tabs .p-tabview-panels {
  padding: 0 !important;
  border: none !important;
  background: transparent !important;
}
.admin-tabs .p-tabview-panel {
  padding: 0 !important;
}
</style>
