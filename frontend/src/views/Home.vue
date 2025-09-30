<template>
  <div class="p-6 max-w-2xl mx-auto">
    <h1 class="text-2xl font-bold mb-4">SMS Archive</h1>

    <!-- Import -->
    <div class="mb-6 border p-4 rounded shadow">
      <h2 class="text-xl font-semibold mb-2">Import SMS/MMS</h2>
      <FileUpload
          mode="basic"
          name="file"
          accept=".xml"
          chooseLabel="Choose File"
          auto
          customUpload
          @uploader="onFileUpload"
          class="mb-3"
      />
      <Message v-if="importMessage" severity="success" :closable="false">
        {{ importMessage }}
      </Message>
    </div>

    <!-- Search -->
    <div class="border p-4 rounded shadow">
      <h2 class="text-xl font-semibold mb-2">Search Messages</h2>

      <div class="flex space-x-2 mb-3">
        <InputText
            v-model="query"
            placeholder="Search messages..."
            class="flex-1"
        />
        <Button
            label="Search"
            icon="pi pi-search"
            severity="success"
            @click="searchMessages"
        />
      </div>

      <DataTable v-if="results.length" :value="results" responsiveLayout="scroll">
        <Column field="contactName" header="Contact"></Column>
        <Column field="address" header="Address"></Column>
        <Column field="body" header="Message"></Column>
        <Column
            field="date"
            header="Date"
            :body="(row) => new Date(row.date).toLocaleString()"
        ></Column>
      </DataTable>

      <Message
          v-else-if="searched"
          severity="warn"
          :closable="false"
          class="mt-3"
      >
        No results found.
      </Message>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { importXml, searchSms, Sms } from "../services/api";

// PrimeVue components
import FileUpload from "primevue/fileupload";
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import Message from "primevue/message";

const query = ref("");
const results = ref<Sms[]>([]);
const searched = ref(false);
const importMessage = ref("");

async function onFileUpload(event: any) {
  const file = event.files[0];
  if (!file) return;

  try {
    const res = await importXml(file);
    importMessage.value = res.ok ? "Import successful!" : "Import failed.";
  } catch (err) {
    console.error(err);
    importMessage.value = "Error during import.";
  }
}

async function searchMessages() {
  if (!query.value) return;

  try {
    results.value = await searchSms(query.value);
    searched.value = true;
  } catch (err) {
    console.error(err);
    results.value = [];
    searched.value = true;
  }
}
</script>
