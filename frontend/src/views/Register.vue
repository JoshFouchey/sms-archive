<template>
  <div class="min-h-[80vh] flex items-center justify-center p-4">
    <div class="w-full max-w-md">
      <!-- Header Card -->
      <div class="bg-gradient-to-r from-green-600 to-emerald-500 dark:from-green-700 dark:to-emerald-600 rounded-t-2xl shadow-lg p-8 text-white text-center">
        <div class="flex justify-center mb-4">
          <div class="bg-white/20 backdrop-blur-sm p-4 rounded-full">
            <i class="pi pi-user-plus text-4xl"></i>
          </div>
        </div>
        <h1 class="text-3xl font-bold mb-2">Create Account</h1>
        <p class="text-green-100 dark:text-green-200">Join SMS Archive and start organizing your messages</p>
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
              class="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent dark:bg-gray-700 dark:text-white transition-all"
              placeholder="Choose a username"
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
              class="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent dark:bg-gray-700 dark:text-white transition-all"
              placeholder="Create a password"
            />
          </div>

          <!-- Confirm Password Field -->
          <div>
            <label for="confirmPassword" class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
              <i class="pi pi-lock text-xs mr-1"></i>
              Confirm Password
            </label>
            <input
              id="confirmPassword"
              v-model="confirmPassword"
              type="password"
              required
              class="w-full px-4 py-3 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent dark:bg-gray-700 dark:text-white transition-all"
              :class="{ 'border-red-500 dark:border-red-500': confirmPassword && !passwordsMatch }"
              placeholder="Confirm your password"
            />
            <!-- Password Match Indicator -->
            <div v-if="confirmPassword" class="mt-2 text-sm flex items-center gap-2">
              <i v-if="passwordsMatch" class="pi pi-check-circle text-green-600 dark:text-green-400"></i>
              <i v-else class="pi pi-times-circle text-red-600 dark:text-red-400"></i>
              <span :class="passwordsMatch ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'">
                {{ passwordsMatch ? 'Passwords match' : 'Passwords do not match' }}
              </span>
            </div>
          </div>

          <!-- Error Message -->
          <div v-if="errorMessage" class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3 flex items-center gap-2 text-red-700 dark:text-red-400">
            <i class="pi pi-exclamation-circle"></i>
            <span class="text-sm">{{ errorMessage }}</span>
          </div>

          <!-- Success Message -->
          <div v-if="successMessage" class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-3 flex items-center gap-2 text-green-700 dark:text-green-400">
            <i class="pi pi-check-circle"></i>
            <span class="text-sm">{{ successMessage }}</span>
          </div>

          <!-- Register Button -->
          <button
            type="submit"
            :disabled="loading || !passwordsMatch || !username || !password || !confirmPassword"
            class="w-full py-3 px-4 bg-gradient-to-r from-green-600 to-emerald-500 hover:from-green-700 hover:to-emerald-600 text-white font-semibold rounded-lg shadow-md hover:shadow-lg transition-all duration-300 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            <i v-if="loading" class="pi pi-spin pi-spinner"></i>
            <i v-else class="pi pi-user-plus"></i>
            <span>{{ loading ? 'Creating account...' : 'Create Account' }}</span>
          </button>

          <!-- Login Link -->
          <div class="text-center pt-4 border-t border-gray-200 dark:border-gray-700">
            <p class="text-sm text-gray-600 dark:text-gray-400">
              Already have an account?
              <router-link to="/login" class="text-green-600 dark:text-green-400 hover:text-green-800 dark:hover:text-green-500 font-semibold transition-colors">
                Sign in here
              </router-link>
            </p>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed } from 'vue';
import { useAuthStore } from '../stores/authStore';
import { useRouter } from 'vue-router';

const store = useAuthStore();
const router = useRouter();

const username = ref('');
const password = ref('');
const confirmPassword = ref('');
const loading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');

const passwordsMatch = computed(() => {
  return password.value === confirmPassword.value;
});

async function submit() {
  errorMessage.value = '';
  successMessage.value = '';

  if (!username.value || !password.value || !confirmPassword.value) {
    errorMessage.value = 'Please fill in all fields';
    return;
  }

  if (!passwordsMatch.value) {
    errorMessage.value = 'Passwords do not match';
    return;
  }

  loading.value = true;

  try {
    await store.register(username.value, password.value);
    successMessage.value = 'Registration successful! Redirecting...';
    setTimeout(() => {
      router.push('/');
    }, 1500);
  } catch (e: any) {
    errorMessage.value = e?.response?.data?.message || 'Registration failed. Please try again.';
  } finally {
    loading.value = false;
  }
}
</script>

