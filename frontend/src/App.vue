<template>
  <div class="flex h-screen bg-gray-50 dark:bg-gray-900 text-gray-900 dark:text-gray-100 overflow-hidden">
    <!-- Persistent Sidebar (Desktop) - Icon Only -->
    <div class="hidden lg:flex lg:flex-col lg:w-20 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 shadow-lg">
      <!-- Navigation Links -->
      <nav class="flex-1 flex flex-col p-3 gap-2 overflow-y-auto pt-4">
        <router-link
          v-for="item in items"
          :key="item.route"
          :to="item.route"
          v-slot="{ isActive }"
          custom
        >
          <a
            :href="item.route"
            @click.prevent="navigateTo(item.route)"
            :class="[
              'flex items-center justify-center p-4 rounded-xl transition-all duration-200',
              isActive
                ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 shadow-sm'
                : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700/50'
            ]"
            :title="item.label"
          >
            <i :class="[item.icon, 'text-2xl', isActive ? 'text-blue-600 dark:text-blue-400' : '']"></i>
          </a>
        </router-link>
      </nav>

      <!-- Sidebar Footer (Icon Only) -->
      <div class="p-3 border-t border-gray-200 dark:border-gray-700 flex flex-col gap-2">
        <!-- User Avatar -->
        <div class="flex items-center justify-center p-2">
          <div class="flex items-center justify-center w-10 h-10 rounded-full bg-indigo-500 text-white font-semibold text-sm" :title="username">
            {{ initials }}
          </div>
        </div>
        
        <!-- Logout Button -->
        <button
          @click="handleLogout"
          class="flex items-center justify-center p-3 rounded-xl text-gray-700 dark:text-gray-300 hover:bg-red-50 dark:hover:bg-red-900/20 hover:text-red-600 dark:hover:text-red-400 transition-all duration-200"
          title="Logout"
        >
          <i class="pi pi-sign-out text-xl"></i>
        </button>
      </div>
    </div>

    <!-- Mobile Hamburger Button (only visible on mobile) -->
    <button
      @click="sidebarOpen = true"
      class="lg:hidden fixed top-4 left-4 z-40 p-3 rounded-xl bg-white dark:bg-gray-800 shadow-lg hover:shadow-xl border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700 transition-all"
      aria-label="Open menu"
    >
      <i class="pi pi-bars text-xl text-gray-700 dark:text-gray-300"></i>
    </button>

    <!-- Mobile Sidebar Overlay (same as before, only shows on mobile) -->
    <div
      v-if="sidebarOpen"
      class="lg:hidden fixed inset-0 bg-black/50 z-50 transition-opacity"
      @click="sidebarOpen = false"
    ></div>

    <!-- Mobile Sidebar (slide-out, only on mobile) -->
    <div
      :class="[
        'lg:hidden fixed top-0 left-0 h-full w-80 bg-white dark:bg-gray-800 shadow-2xl z-50 transform transition-transform duration-300 ease-in-out flex flex-col',
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

      <!-- Sidebar Footer (User Info + Logout) -->
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

    <!-- Main Content Area -->
    <div :class="[
      'flex-1 flex flex-col',
      isMessagesPage ? 'overflow-hidden' : 'overflow-y-auto'
    ]">
      <!-- Toast Container -->
      <Toast position="top-right" />

      <!-- Main -->
      <main :class="mainClasses">
        <router-view />
      </main>
    </div>
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

const username = computed(() => auth.user?.username || 'Guest');
const initials = computed(() => username.value.substring(0, 2).toUpperCase());

const items = [
  { label: "Dashboard", icon: "pi pi-home", route: "/" },
  { label: "Gallery", icon: "pi pi-images", route: "/gallery" },
  { label: "Messages", icon: "pi pi-comments", route: "/messages" },
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

// Check if we're on the messages page (full-height layout)
const isMessagesPage = computed(() => {
  return route.path.startsWith('/messages');
});

// Compute main container classes based on route
const mainClasses = computed(() => {
  if (isMessagesPage.value) {
    // Full width, no padding, no scroll for messages (it manages its own layout)
    return 'flex-1 w-full overflow-hidden';
  }
  // Wider layout with more breathing room and normal scrolling for other pages
  return 'flex-1 p-4 sm:p-6 lg:p-8 w-full max-w-[1920px] mx-auto overflow-y-auto';
});
</script>
