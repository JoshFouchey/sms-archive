<template>
  <div :class="[
    'flex flex-col bg-gray-50 dark:bg-gray-900 text-gray-900 dark:text-gray-100',
    isHistoricalPage ? 'h-screen overflow-hidden' : 'min-h-screen'
  ]">
    <!-- Minimal Top Bar (just hamburger) -->
    <div class="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 sticky top-0 z-40 shadow-sm">
      <div class="px-4 py-3">
        <!-- Hamburger Menu Button -->
        <button
          @click="sidebarOpen = true"
          class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          aria-label="Open menu"
        >
          <i class="pi pi-bars text-2xl text-gray-700 dark:text-gray-300"></i>
        </button>
      </div>
    </div>

    <!-- Sidebar Overlay -->
    <div
      v-if="sidebarOpen"
      class="fixed inset-0 bg-black/50 z-50 transition-opacity"
      @click="sidebarOpen = false"
    ></div>

    <!-- Sidebar -->
    <div
      :class="[
        'fixed top-0 left-0 h-full w-80 bg-white dark:bg-gray-800 shadow-2xl z-50 transform transition-transform duration-300 ease-in-out flex flex-col',
        sidebarOpen ? 'translate-x-0' : '-translate-x-full'
      ]"
    >
      <!-- Sidebar Header -->
      <div class="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
        <div class="flex items-center gap-2">
          <i class="pi pi-inbox text-2xl text-blue-600 dark:text-blue-400"></i>
          <span class="text-xl font-bold bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-400 dark:to-cyan-400 bg-clip-text text-transparent">
            SMS Archive
          </span>
        </div>
        <button
          @click="sidebarOpen = false"
          class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          aria-label="Close menu"
        >
          <i class="pi pi-times text-xl text-gray-700 dark:text-gray-300"></i>
        </button>
      </div>

      <!-- Navigation Links -->
      <nav class="flex-1 flex flex-col p-4 gap-2 overflow-y-auto">
        <router-link
          v-for="item in items"
          :key="item.route"
          :to="item.route"
          @click="sidebarOpen = false"
          v-slot="{ isActive }"
          custom
        >
          <a
            :href="item.route"
            @click.prevent="navigateTo(item.route)"
            :class="[
              'flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-200',
              isActive
                ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 font-semibold shadow-sm'
                : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700/50'
            ]"
          >
            <i :class="[item.icon, 'text-xl', isActive ? 'text-blue-600 dark:text-blue-400' : '']"></i>
            <span class="text-base">{{ item.label }}</span>
          </a>
        </router-link>
      </nav>

      <!-- Sidebar Footer (User Info + Logout side by side) -->
      <div class="p-4 border-t border-gray-200 dark:border-gray-700">
        <div class="flex items-center gap-3">
          <!-- User Info -->
          <div class="flex-1">
            <UserBadge />
          </div>
          
          <!-- Logout Button -->
          <button
            @click="handleLogout"
            class="flex items-center gap-2 px-4 py-3 rounded-xl text-gray-700 dark:text-gray-300 hover:bg-red-50 dark:hover:bg-red-900/20 hover:text-red-600 dark:hover:text-red-400 transition-all duration-200"
            title="Logout"
          >
            <i class="pi pi-sign-out text-xl"></i>
            <span class="text-sm font-medium">Logout</span>
          </button>
        </div>
      </div>
    </div>

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
import { ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Toast from "primevue/toast";
import UserBadge from './components/UserBadge.vue';
import { useAuthStore } from './stores/authStore';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const sidebarOpen = ref(false);

const items = [
  { label: "Dashboard", icon: "pi pi-home", route: "/" },
  { label: "Gallery", icon: "pi pi-images", route: "/gallery" },
  { label: "Messages", icon: "pi pi-comments", route: "/messages-historical" },
  { label: "Contacts", icon: "pi pi-user", route: "/contacts" },
  { label: "Search", icon: "pi pi-search", route: "/search" },
  { label: "Import", icon: "pi pi-upload", route: "/import" }
];

function navigateTo(routePath: string) {
  router.push(routePath);
  sidebarOpen.value = false;
}

async function handleLogout() {
  auth.logout();
  router.push('/login');
}

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
