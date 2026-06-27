<script setup lang="ts">
// Isolates chart.js + vue-chartjs so they are bundled into their own async chunk.
// This component is only imported (via defineAsyncComponent) when the user actually
// toggles the chart view, keeping chart.js out of the default route's initial load.
import { Bar, Line } from 'vue-chartjs';
import {
  Chart as ChartJS,
  CategoryScale, LinearScale,
  BarElement, LineElement, PointElement,
  Title, Tooltip, Legend,
} from 'chart.js';

ChartJS.register(CategoryScale, LinearScale, BarElement, LineElement, PointElement, Title, Tooltip, Legend);

defineProps<{
  type?: 'bar' | 'line';
  data: any;
  options: any;
}>();
</script>

<template>
  <Line v-if="type === 'line'" :data="data" :options="options" />
  <Bar v-else :data="data" :options="options" />
</template>
