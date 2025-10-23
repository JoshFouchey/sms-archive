<template>
  <div class="max-w-md mx-auto p-6 bg-white dark:bg-gray-800 rounded shadow">
    <h2 class="text-2xl font-semibold mb-4">Register</h2>
    <form @submit.prevent="submit">
      <div class="mb-4">
        <label class="block mb-1">Username</label>
        <input v-model="username" class="w-full px-3 py-2 border rounded" />
      </div>
      <div class="mb-4">
        <label class="block mb-1">Password</label>
        <input v-model="password" type="password" class="w-full px-3 py-2 border rounded" />
      </div>
      <button class="px-4 py-2 bg-green-600 text-white rounded" :disabled="loading">Register</button>
      <p class="mt-4 text-sm">Already have an account? <router-link to="/login" class="text-blue-500">Login</router-link></p>
    </form>
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
async function submit() {
  loading.value = true;
  try { await store.register(username.value, password.value); router.push('/'); } catch (e) { alert('Registration failed'); } finally { loading.value=false; }
}
</script>

