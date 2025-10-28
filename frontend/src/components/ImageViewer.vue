<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4"
    role="dialog"
    aria-modal="true"
    :aria-label="ariaLabel"
    @click.self="requestClose"
    @keydown="onKey"
    ref="overlay"
  >
    <div class="relative max-h-full max-w-full flex flex-col items-center select-none" ref="content">
      <img
        v-if="current"
        :src="current.fullUrl"
        :alt="currentAlt"
        class="max-h-[80vh] max-w-[90vw] object-contain mb-4 shadow-lg"
        @load="imageLoaded = true"
        @error="onImageError"
      />
      <div v-if="!imageLoaded" class="text-white mb-4" aria-live="polite">Loading...</div>
      <div class="flex flex-wrap gap-2 justify-center" ref="controls">
        <button
          type="button"
          class="px-3 py-1 rounded bg-gray-200 text-gray-800 text-sm focus:outline-none focus:ring"
          @click="requestClose"
          ref="closeBtn"
        >Close<span class="sr-only"> image viewer</span></button>
        <button
          v-if="allowDelete && current?.id != null"
          type="button"
          class="px-3 py-1 rounded bg-red-600 text-white text-sm focus:outline-none focus:ring"
          @click="emitDelete"
        >Delete</button>
        <button
          v-if="hasPrev"
          type="button"
          class="px-3 py-1 rounded bg-gray-200 text-gray-800 text-sm focus:outline-none focus:ring"
          @click="goPrev"
        >Prev</button>
        <button
          v-if="hasNext"
          type="button"
          class="px-3 py-1 rounded bg-gray-200 text-gray-800 text-sm focus:outline-none focus:ring"
          @click="goNext"
        >Next</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';

export interface ViewerImage {
  id?: number;
  fullUrl: string; // required
  thumbUrl?: string;
  contentType?: string;
}

const props = defineProps<{ images: ViewerImage[]; initialIndex?: number; allowDelete?: boolean; ariaLabel?: string }>();
const emit = defineEmits<{ (e: 'close'): void; (e: 'delete', id: number): void; (e: 'indexChange', index: number): void }>();

const index = ref(props.initialIndex ?? 0);
const imageLoaded = ref(false);
const closeBtn = ref<HTMLButtonElement|null>(null);
const overlay = ref<HTMLElement|null>(null);
const content = ref<HTMLElement|null>(null);
const controls = ref<HTMLElement|null>(null);

const current = computed(() => props.images[index.value]);
const currentAlt = computed(() => current.value?.contentType?.replace(/^image\//,'') || 'image');
const hasPrev = computed(() => index.value > 0);
const hasNext = computed(() => index.value < props.images.length - 1);
const allowDelete = computed(() => props.allowDelete === true);

function requestClose() { emit('close'); }
function goPrev() { if (!hasPrev.value) return; index.value--; imageLoaded.value = false; emit('indexChange', index.value); }
function goNext() { if (!hasNext.value) return; index.value++; imageLoaded.value = false; emit('indexChange', index.value); }
function emitDelete() { if (current.value?.id != null) emit('delete', current.value.id); }
function onImageError() { /* could set fallback state */ }

function onKey(e: KeyboardEvent) {
  switch (e.key) {
    case 'Escape': requestClose(); break;
    case 'ArrowLeft': goPrev(); break;
    case 'ArrowRight': goNext(); break;
    case 'Delete':
    case 'Backspace': if (allowDelete.value && current.value?.id != null) emitDelete(); break;
    case 'Tab': trapFocus(e); break;
  }
}

function trapFocus(e: KeyboardEvent) {
  if (!overlay.value) return;
  const focusables = overlay.value.querySelectorAll<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
  if (!focusables.length) return;
  const first = focusables[0];
  const last = focusables[focusables.length - 1];
  if (!first || !last) return;
  if (e.shiftKey) {
    if (document.activeElement === first) { last.focus(); e.preventDefault(); }
  } else {
    if (document.activeElement === last) { first.focus(); e.preventDefault(); }
  }
}

// Body scroll lock
let savedScrollY = 0;
function lockBody() {
  savedScrollY = window.scrollY;
  document.body.style.position = 'fixed';
  document.body.style.top = `-${savedScrollY}px`;
  document.body.style.left = '0';
  document.body.style.right = '0';
  document.body.style.width = '100%';
}
function unlockBody() {
  document.body.style.position = '';
  document.body.style.top = '';
  document.body.style.left = '';
  document.body.style.right = '';
  document.body.style.width = '';
  window.scrollTo(0, savedScrollY);
}

watch(() => props.initialIndex, (v) => { if (typeof v === 'number') { index.value = v; imageLoaded.value = false; } });
watch(index, () => { imageLoaded.value = false; });

onMounted(() => {
  lockBody();
  setTimeout(() => { closeBtn.value?.focus(); }, 0);
});

onUnmounted(() => { unlockBody(); });
</script>

<style scoped>
.sr-only { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); border:0; }
</style>
