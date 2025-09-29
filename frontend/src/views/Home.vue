<!-- frontend/src/views/Home.vue -->
<template>
  <div class="p-6 max-w-2xl mx-auto">
    <h1 class="text-2xl font-bold mb-4">SMS Archive</h1>

    <!-- Import -->
    <div class="mb-6 border p-4 rounded shadow">
      <h2 class="text-xl font-semibold mb-2">Import SMS/MMS</h2>
      <input type="file" ref="fileInput" class="mb-2" />
      <button
          class="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
          @click="uploadFile"
      >
        Upload & Import
      </button>
      <p v-if="importMessage" class="mt-2 text-green-600">{{ importMessage }}</p>
    </div>

    <!-- Search -->
    <div class="border p-4 rounded shadow">
      <h2 class="text-xl font-semibold mb-2">Search Messages</h2>
      <input
          v-model="query"
          type="text"
          placeholder="Search messages..."
          class="border px-3 py-2 w-full mb-2 rounded"
      />
      <button
          class="bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700"
          @click="searchMessages"
      >
        Search
      </button>

      <div v-if="results.length" class="mt-4">
        <h3 class="text-lg font-semibold">Results:</h3>
        <ul class="space-y-2 mt-2">
          <li
              v-for="msg in results"
              :key="msg.id"
              class="border p-2 rounded bg-gray-50"
          >
            <p><strong>{{ msg.contactName }}</strong> ({{ msg.address }})</p>
            <p class="text-sm text-gray-700">{{ msg.body }}</p>
            <p class="text-xs text-gray-500">
              {{ new Date(msg.date).toLocaleString() }}
            </p>
          </li>
        </ul>
      </div>

      <p v-else-if="searched" class="mt-4 text-gray-500">No results found.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { importXml, searchSms, Sms } from "../services/api";

const query = ref("");
const results = ref<Sms[]>([]);
const searched = ref(false);
const importMessage = ref("");
const fileInput = ref<HTMLInputElement | null>(null);

async function uploadFile() {
  if (!fileInput.value?.files?.length) {
    alert("Please choose a file first.");
    return;
  }

  try {
    const res = await importXml(fileInput.value.files[0]);
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
