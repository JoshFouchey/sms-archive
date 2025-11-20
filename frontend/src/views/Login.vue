<template>
  <div class="min-h-[80vh] flex items-center justify-center p-4">
    <div class="w-full max-w-md">
      <!-- Header Card -->
      <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 rounded-t-2xl shadow-lg p-8 text-white text-center">
        <div class="flex justify-center mb-4">
          <div class="bg-white/20 backdrop-blur-sm p-4 rounded-full">
            <i class="pi pi-lock text-4xl"></i>
          </div>
        </div>
        <h1 class="text-3xl font-bold mb-2">Welcome Back</h1>
        <p class="text-blue-100 dark:text-blue-200">Sign in to your SMS Archive account</p>
      </div>

      <!-- Form Card -->
      <div class="bg-white dark:bg-gray-800 rounded-b-2xl shadow-lg p-8">
        <form @submit.prevent="submit" class="space-y-6">
          <!-- Username Field -->
          <div>
            <label for="username" class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
              <i class="pi pi-user text-xs mr-1"></i>
              Username
            </label>
            <input
              id="username"
              v-model="username"
              type="text"
              required
              class="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent dark:bg-gray-700 dark:text-white transition-all"
              placeholder="Enter your username"
            />
          </div>

          <!-- Password Field -->
          <div>
            <label for="password" class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
              <i class="pi pi-lock text-xs mr-1"></i>
              Password
            </label>
            <input
              id="password"
              v-model="password"
              type="password"
              required
              class="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent dark:bg-gray-700 dark:text-white transition-all"
              placeholder="Enter your password"
            />
          </div>

          <!-- Error Message -->
          <div v-if="errorMessage" class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3 flex items-center gap-2 text-red-700 dark:text-red-400">
            <i class="pi pi-exclamation-circle"></i>
            <span class="text-sm">{{ errorMessage }}</span>
          </div>

          <!-- Login Button -->
          <button
            type="submit"
            :disabled="loading"
            class="w-full py-3 px-4 bg-gradient-to-r from-blue-600 to-cyan-500 hover:from-blue-700 hover:to-cyan-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            <i v-if="loading" class="pi pi-spin pi-spinner"></i>
            <i v-else class="pi pi-sign-in"></i>
            <span>{{ loading ? 'Signing in...' : 'Sign In' }}</span>
          </button>

          <!-- Register Link -->
          <div class="text-center pt-4 border-t border-gray-200 dark:border-gray-700">
            <p class="text-sm text-gray-600 dark:text-gray-400">
              Don't have an account?
              <router-link to="/register" class="text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-500 font-semibold transition-colors">
                Create one now
              </router-link>
            </p>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { useAuthStore } from '../stores/authStore';
import { useRouter } from 'vue-router';

const store = useAuthStore();
const router = useRouter();

const username = ref('');
const password = ref('');
const loading = ref(false);
const errorMessage = ref('');

async function submit() {
  if (!username.value || !password.value) {
    errorMessage.value = 'Please fill in all fields';
    return;
  }

  loading.value = true;
  errorMessage.value = '';

  try {
    await store.login(username.value, password.value);
    router.push('/');
  } catch (e: any) {
    errorMessage.value = e?.response?.data?.message || 'Login failed. Please check your credentials.';
  } finally {
    loading.value = false;
  }
}
</script>

