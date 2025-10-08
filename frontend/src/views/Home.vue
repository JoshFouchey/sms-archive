<template>
  <div class="space-y-6">
    <h1 class="text-3xl font-bold text-gray-800 dark:text-gray-100">Message Archive</h1>

    <!-- Import Section -->
    <section class="bg-white dark:bg-gray-800 p-6 rounded-xl shadow">
      <h2 class="text-xl font-semibold mb-4">Import Messages</h2>

      <FileUpload
          mode="basic"
          name="file"
          accept=".xml"
          chooseLabel="Choose File"
          auto
          customUpload
          @uploader="onFileUpload"
          class="mb-4"
      />

      <PrimeMessage
          v-if="importMessage"
          severity="success"
          :closable="false"
      >
        {{ importMessage }}
      </PrimeMessage>
    </section>

    <!-- Search Section -->
    <section class="bg-white dark:bg-gray-800 p-6 rounded-xl shadow">
      <h2 class="text-xl font-semibold mb-4">Search Messages</h2>

      <div class="flex flex-col sm:flex-row gap-3 mb-4">
        <InputText v-model="query" placeholder="Search text..." class="flex-1" />
        <Button label="Search" icon="pi pi-search" severity="success" @click="searchMessages" />
      </div>

      <DataTable v-if="results.length" :value="results" responsiveLayout="scroll">
        <Column field="protocol" header="Type" />
        <Column field="sender" header="Sender" />
        <Column field="recipient" header="Recipient" />
        <Column field="body" header="Message" />
        <Column field="contactName" header="Contact" />
        <Column field="timestamp" header="Date">
          <template #body="{ data }">
            {{ formatTimestamp(data) }}
          </template>
        </Column>
      </DataTable>

      <PrimeMessage
          v-else-if="searched"
          severity="warn"
          :closable="false"
          class="mt-3"
      >
        No results found.
      </PrimeMessage>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { importXml, searchByText } from "../services/api";
import type { Message as Msg } from "../services/api";

import FileUpload from "primevue/fileupload";
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import PrimeMessage from "primevue/message";

const query = ref("");
const results = ref<Msg[]>([]);
const searched = ref(false);
const importMessage = ref("");

async function onFileUpload(event: any) {
  const file = event.files?.[0];
  if (!file) return;
  try {
    const res = await importXml(file);
    importMessage.value = res.ok ? "Messages imported successfully!" : "Import failed.";
  } catch {
    importMessage.value = "Error during import.";
  }
}

async function searchMessages() {
  const q = query.value.trim();
  if (!q) {
    results.value = [];
    searched.value = false;
    return;
  }
  try {
    const data = await searchByText(q);
    results.value = Array.isArray(data) ? data : [];
  } catch {
    results.value = [];
  } finally {
    searched.value = true;
  }
}

function formatTimestamp(row: Msg) {
  return row?.timestamp ? new Date(row.timestamp).toLocaleString() : "";
}
</script>