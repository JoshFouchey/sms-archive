<template>
  <div class="space-y-6">
    <!-- Header -->
    <PageHeader title="Admin" subtitle="Import data and manage AI embeddings" icon="pi pi-cog" />

    <!-- Tabs -->
    <Tabs v-model:value="activeTab" class="admin-tabs">
      <TabList>
        <Tab value="0">
          <div class="flex items-center gap-2">
            <i class="pi pi-upload"></i>
            <span class="hidden sm:inline">Import</span>
          </div>
        </Tab>
        <Tab value="1">
          <div class="flex items-center gap-2">
            <i class="pi pi-microchip"></i>
            <span class="hidden sm:inline">AI Settings</span>
          </div>
        </Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="0">
          <ImportView :hide-header="true" />
        </TabPanel>
        <TabPanel value="1">
          <AiSettingsView :hide-header="true" />
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import Tabs from 'primevue/tabs';
import TabList from 'primevue/tablist';
import Tab from 'primevue/tab';
import TabPanels from 'primevue/tabpanels';
import TabPanel from 'primevue/tabpanel';
import ImportView from './Import.vue';
import AiSettingsView from './AiSettings.vue';

const activeTab = ref('0');
</script>

<style>
.admin-tabs .p-tabpanels {
  padding: 0 !important;
  border: none !important;
  background: transparent !important;
}
.admin-tabs .p-tabpanel {
  padding: 0 !important;
}
</style>
