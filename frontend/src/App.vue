<template>
  <div class="flex h-screen bg-gray-50 dark:bg-gray-900 text-gray-900 dark:text-gray-100 overflow-hidden">
    <!-- Permanent Thin Sidebar (All Screen Sizes) - Icon Only -->
    <div class="flex flex-col w-16 bg-gradient-to-b from-blue-600 to-cyan-500 dark:from-blue-800 dark:to-cyan-700 shadow-xl">
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
  { label: "Dashboard", icon: "pi pi-home", route: "/" },
  { label: "Gallery", icon: "pi pi-images", route: "/gallery" },
  { label: "Messages", icon: "pi pi-comments", route: "/messages" },
  { label: "Contacts", icon: "pi pi-user", route: "/contacts" },
  { label: "Search", icon: "pi pi-search", route: "/search" },
  { label: "Import", icon: "pi pi-upload", route: "/import" }
];

function navigateTo(routePath: string) {
  router.push(routePath);
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
