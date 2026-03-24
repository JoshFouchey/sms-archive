<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="bg-gradient-to-r from-gray-700 to-gray-600 dark:from-gray-800 dark:to-gray-700 rounded-2xl shadow-lg p-6 text-white">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 class="text-3xl font-bold mb-1 flex items-center gap-3">
            <i class="pi pi-cog"></i>
            Admin
          </h1>
          <p class="text-gray-300">Import data, manage embeddings, KG extraction, and entity resolution</p>
        </div>
      </div>
    </div>

    <!-- Tabs -->
    <TabView v-model:activeIndex="activeTab" class="admin-tabs">
      <TabPanel value="0">
        <template #header>
          <div class="flex items-center gap-2">
            <i class="pi pi-upload"></i>
            <span>Import</span>
          </div>
        </template>
        <ImportView :hide-header="true" />
      </TabPanel>

      <TabPanel value="1">
        <template #header>
          <div class="flex items-center gap-2">
            <i class="pi pi-microchip"></i>
            <span>AI Settings</span>
          </div>
        </template>
        <AiSettingsView :hide-header="true" />
      </TabPanel>
    </TabView>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
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
