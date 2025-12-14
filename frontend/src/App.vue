<template>
  <div :class="[
    'flex flex-col bg-gray-50 dark:bg-gray-900 text-gray-900 dark:text-gray-100',
    isHistoricalPage ? 'h-screen overflow-hidden' : 'min-h-screen'
  ]">
    <!-- Navbar -->
    <Menubar :model="items" class="shadow-md bg-white dark:bg-gray-800 border-b-2 border-blue-500 dark:border-blue-600 sticky top-0 z-50">
      <template #start>
        <div class="flex items-center gap-2 px-3">
          <i class="pi pi-inbox text-2xl text-blue-600 dark:text-blue-400"></i>
          <span class="text-xl font-bold bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-400 dark:to-cyan-400 bg-clip-text text-transparent">
            SMS Archive
          </span>
        </div>
      </template>
      <template #item="{ item, props }">
        <router-link
          v-if="item.route"
          v-slot="{ href, navigate, isActive }"
          :to="item.route"
          custom
        >
          <a
            :href="href"
            v-bind="props.action"
            @click="navigate"
            :class="[
              'flex items-center px-4 py-2.5 rounded-lg transition-all duration-200',
              isActive
                ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 font-semibold shadow-sm'
                : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700/50'
            ]"
          >
            <span :class="[item.icon, 'text-lg', isActive ? 'text-blue-600 dark:text-blue-400' : '']" />
            <span class="ml-2">{{ item.label }}</span>
          </a>
        </router-link>
      </template>
      <template #end>
        <div class="px-3">
          <UserBadge />
        </div>
      </template>
    </Menubar>

    <!-- Toast Container -->
    <Toast position="top-right" />

    <!-- Main -->
    <main :class="mainClasses">
      <router-view />
    </main>

    <!-- Footer (hidden on historical messages page) -->
    <footer v-if="!hideFooter" class="text-center text-sm text-gray-500 dark:text-gray-400 py-6 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
      <div class="flex items-center justify-center gap-2">
        <i class="pi pi-inbox text-blue-600 dark:text-blue-400"></i>
        <span class="font-medium">SMS Archive</span>
        <span class="text-gray-400 dark:text-gray-500">©</span>
        <span>{{ new Date().getFullYear() }}</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import Menubar from "primevue/menubar";
import Toast from "primevue/toast";
import UserBadge from './components/UserBadge.vue';

const route = useRoute();

const items = [
  { label: "Dashboard", icon: "pi pi-home", route: "/" },
  { label: "Gallery", icon: "pi pi-images", route: "/gallery" },
  { label: "Messages", icon: "pi pi-comments", route: "/messages-historical" },
  { label: "Contacts", icon: "pi pi-user", route: "/contacts" },
  { label: "Search", icon: "pi pi-search", route: "/search" },
  { label: "Import", icon: "pi pi-upload", route: "/import" }
];

// Check if we're on the historical messages page
const isHistoricalPage = computed(() => {
  return route.path.startsWith('/messages-historical');
});

// Compute main container classes based on route
const mainClasses = computed(() => {
  if (isHistoricalPage.value) {
    // Full width, no padding, no scroll for historical messages (it manages its own layout)
    return 'flex-1 w-full overflow-hidden';
  }
  // Wider layout with more breathing room and normal scrolling for other pages
  return 'flex-1 p-4 sm:p-6 lg:p-8 w-full max-w-[1920px] mx-auto overflow-y-auto';
});

// Check if footer should be hidden
const hideFooter = computed(() => {
  return isHistoricalPage.value;
});
</script>
