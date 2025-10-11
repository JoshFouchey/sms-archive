<template>
  <div class="p-6 text-sm text-gray-500 dark:text-gray-300">
    <p>Home view has been merged into the Dashboard. Redirecting...</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
const router = useRouter();
onMounted(() => router.replace('/'));
</script>