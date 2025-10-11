<template>
  <div class="min-h-screen flex flex-col bg-gray-50 dark:bg-gray-900 text-gray-900 dark:text-gray-100">
    <!-- Navbar -->
    <Menubar :model="items" class="shadow-sm bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700" />

    <!-- Toast Container -->
    <Toast position="top-right" />

    <!-- Main -->
    <main class="flex-1 p-6 w-full max-w-6xl mx-auto">
      <router-view />
    </main>

    <!-- Footer -->
    <footer class="text-center text-sm text-gray-500 py-4 border-t border-gray-200 dark:border-gray-700">
      SMS Archive © {{ new Date().getFullYear() }}
    </footer>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import Menubar from "primevue/menubar";
import Toast from "primevue/toast";

const router = useRouter();

const items = [
  { label: "Dashboard", icon: "pi pi-home", command: () => router.push("/") },
  { label: "Gallery", icon: "pi pi-images", command: () => router.push("/gallery") },
  { label: "Messages", icon: "pi pi-comments", command: () => router.push("/messages") },
  { label: "Search", icon: "pi pi-search", command: () => router.push("/search") }
];
</script>
