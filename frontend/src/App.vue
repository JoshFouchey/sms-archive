<template>
  <div class="flex h-screen bg-gray-50 dark:bg-gray-900 text-gray-900 dark:text-gray-100 overflow-hidden">
    <!-- Sidebar (hidden on mobile, visible md+) -->
    <div class="hidden md:flex flex-col w-16 bg-gradient-to-b from-blue-600 to-cyan-500 dark:from-blue-800 dark:to-cyan-700 shadow-xl">
      <!-- Logo/Brand -->
      <div class="flex items-center justify-center py-4 mb-2">
        <div class="p-2 bg-white/20 rounded-xl backdrop-blur-sm">
          <i class="pi pi-comments text-2xl text-white"></i>
        </div>
      </div>

      <!-- Navigation Links -->
      <nav class="flex-1 flex flex-col px-2 gap-2 overflow-y-auto">
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
              'flex items-center justify-center p-3 rounded-xl transition-all duration-200',
              isActive
                ? 'bg-white/30 shadow-lg scale-105'
                : 'hover:bg-white/10 hover:scale-105'
            ]"
            :title="item.label"
            :aria-label="item.label"
          >
            <i :class="[item.icon, 'text-xl text-white']"></i>
          </a>
        </router-link>
      </nav>

      <!-- Sidebar Footer -->
      <div class="px-2 pb-4 pt-2 border-t border-white/20 flex flex-col gap-2">
        <!-- User Avatar -->
        <div class="flex items-center justify-center">
          <div 
            class="flex items-center justify-center w-10 h-10 rounded-full bg-white/20 backdrop-blur-sm text-white font-semibold text-xs" 
            :title="username"
          >
            {{ initials }}
          </div>
        </div>
        
        <!-- Logout Button -->
        <button
          @click="handleLogout"
          class="flex items-center justify-center p-3 rounded-xl hover:bg-white/10 transition-all duration-200"
          title="Logout"
          aria-label="Logout"
        >
          <i class="pi pi-sign-out text-xl text-white"></i>
        </button>
      </div>
    </div>

    <!-- Bottom Navigation Bar (visible on mobile, hidden md+) -->
    <div class="flex md:hidden fixed bottom-0 left-0 right-0 z-50 bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-800 dark:to-cyan-700 shadow-[0_-2px_10px_rgba(0,0,0,0.15)] pb-[env(safe-area-inset-bottom)]">
      <nav class="flex flex-1 items-center justify-around px-1 py-1">
        <router-link
          v-for="item in items"
          :key="'bottom-' + item.route"
          :to="item.route"
          v-slot="{ isActive }"
          custom
        >
          <a
            :href="item.route"
            @click.prevent="navigateTo(item.route)"
            :class="[
              'flex flex-col items-center justify-center gap-0.5 px-2 py-1.5 rounded-xl transition-all duration-200 min-w-[3rem]',
              isActive
                ? 'bg-white/30 shadow-lg'
                : 'hover:bg-white/10'
            ]"
            :aria-label="item.label"
          >
            <i :class="[item.icon, 'text-lg text-white']"></i>
            <span class="text-[10px] leading-tight text-white/90 font-medium">{{ item.label }}</span>
          </a>
        </router-link>
        <button
          @click="handleLogout"
          class="flex flex-col items-center justify-center gap-0.5 px-2 py-1.5 rounded-xl hover:bg-white/10 transition-all duration-200 min-w-[3rem]"
          aria-label="Logout"
        >
          <i class="pi pi-sign-out text-lg text-white"></i>
          <span class="text-[10px] leading-tight text-white/90 font-medium">Logout</span>
        </button>
      </nav>
    </div>

    <!-- Main Content Area -->
    <div :class="[
      'flex-1 flex flex-col pb-16 md:pb-0',
      isFullHeightPage ? 'overflow-hidden' : 'overflow-y-auto'
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
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Toast from "primevue/toast";
import { useAuthStore } from './stores/authStore';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const username = computed(() => auth.user?.username || 'Guest');
const initials = computed(() => username.value.substring(0, 2).toUpperCase());

const items = [
  { label: "Ask", icon: "pi pi-sparkles", route: "/" },
  { label: "Messages", icon: "pi pi-comments", route: "/messages" },
  { label: "Explore", icon: "pi pi-sitemap", route: "/explore" },
  { label: "Gallery", icon: "pi pi-images", route: "/gallery" },
  { label: "Contacts", icon: "pi pi-user", route: "/contacts" },
  { label: "Admin", icon: "pi pi-cog", route: "/admin" },
];

function navigateTo(routePath: string) {
  router.push(routePath);
}

async function handleLogout() {
  auth.logout();
  router.push('/login');
}

// Check if we're on a full-height page (manages its own layout)
const isFullHeightPage = computed(() => {
  return route.path.startsWith('/messages') || route.path === '/explore' || route.path === '/';
});

// Compute main container classes based on route
const mainClasses = computed(() => {
  if (isFullHeightPage.value) {
    // Full width with light padding, no scroll (page manages its own layout)
    return 'flex-1 w-full p-4 overflow-hidden';
  }
  // Wider layout with more breathing room and normal scrolling for other pages
  return 'flex-1 p-4 sm:p-6 lg:p-8 w-full max-w-[1920px] mx-auto overflow-y-auto';
});
</script>
