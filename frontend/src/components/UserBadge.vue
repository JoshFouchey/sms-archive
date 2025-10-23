<template>
  <div class="flex items-center gap-3" v-if="ready">
    <div class="flex items-center gap-2 px-3 py-1 rounded-full bg-gray-100 dark:bg-gray-700">
      <div class="flex items-center justify-center w-8 h-8 rounded-full bg-indigo-500 text-white font-semibold" :title="username">
        {{ initials }}
      </div>
      <div class="text-sm leading-tight">
        <div class="font-medium" data-testid="user-badge-username">{{ username }}</div>
        <div class="text-xs" :class="statusClass">{{ statusLabel }}</div>
      </div>
    </div>
    <button v-if="isAuthenticated" @click="logout" class="text-xs px-3 py-1 rounded bg-red-500 hover:bg-red-600 text-white" data-testid="logout-btn">
      Logout
    </button>
    <button v-else @click="goLogin" class="text-xs px-3 py-1 rounded bg-indigo-500 hover:bg-indigo-600 text-white" data-testid="login-btn">
      Login
    </button>
  </div>
  <div v-else class="flex items-center gap-2 px-3 py-1 text-xs text-gray-500">Loading...</div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useAuthStore } from '../stores/authStore';
import { useRouter } from 'vue-router';

const auth = useAuthStore();
const router = useRouter();
const ready = ref(false);

onMounted(() => { // ensure me fetched if token present but store not hydrated yet
  if (auth.accessToken && !auth.user) { auth.fetchMe().finally(() => ready.value = true); } else ready.value = true;
});

const username = computed(() => auth.user?.username || 'Guest');
const isAuthenticated = computed(() => !!auth.user);
const initials = computed(() => username.value.substring(0,2).toUpperCase());
const statusLabel = computed(() => {
  switch (auth.status) {
    case 'authenticating': return 'Authenticating';
    case 'authenticated': return 'Online';
    case 'error': return 'Error';
    default: return isAuthenticated.value ? 'Online' : 'Offline';
  }
});
const statusClass = computed(() => {
  return {
    'text-green-600 dark:text-green-400': statusLabel.value === 'Online',
    'text-yellow-600 dark:text-yellow-400': statusLabel.value === 'Authenticating',
    'text-red-600 dark:text-red-400': statusLabel.value === 'Error',
    'text-gray-500': statusLabel.value === 'Offline'
  };
});

function logout() { auth.logout(); router.push('/login'); }
function goLogin() { router.push('/login'); }
</script>

<style scoped>
</style>

