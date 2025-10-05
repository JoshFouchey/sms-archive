<template>
  <div class="min-h-screen flex flex-col bg-gray-50">
    <!-- Top Navigation -->
    <Menubar :model="items" class="shadow-md bg-white" />

    <!-- Main Content -->
    <main class="flex-1 p-6 max-w-5xl mx-auto w-full">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import Menubar from "primevue/menubar";

const router = useRouter();

const items = [
  {
    label: "Home",
    icon: "pi pi-home",
    command: () => router.push("/"),
  },
  {
    label: "Gallery",
    icon: "pi pi-images",
    command: () => router.push("/gallery"),
  },
  {
    label: "Analytics",
    icon: "pi pi-chart-bar",
    command: () => router.push("/analytics"),
  },
];
</script>

<style>
.p-menubar {
  border-radius: 0;
}
</style>
