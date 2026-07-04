<template>
  <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 rounded-2xl shadow-lg p-4 sm:p-6 text-white">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div>
        <h1 class="text-2xl sm:text-4xl font-bold mb-2 flex items-center gap-3">
          <i v-if="icon" :class="icon"></i>
          {{ title }}
        </h1>
        <p v-if="subtitle" class="text-blue-100 dark:text-blue-200">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.default" class="flex items-center gap-2">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// Canonical gradient hero header used by every top-level view.
// Put PageHeaderStat chips (or anything else) in the default slot.
defineProps<{
  title: string;
  subtitle?: string;
  icon?: string;
}>();
</script>
