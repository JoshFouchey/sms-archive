<template>
  <div class="bg-white/10 backdrop-blur-sm rounded-lg px-4 py-2 border border-white/20">
    <div class="flex items-center gap-2">
      <i v-if="icon" :class="[icon, 'text-lg']"></i>
      <div class="text-left">
        <p class="text-xs text-blue-100">{{ label }}</p>
        <p class="text-2xl font-bold">{{ value }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// Frosted stat chip for use inside PageHeader's slot.
defineProps<{
  label: string;
  value: string | number;
  icon?: string;
}>();
</script>
