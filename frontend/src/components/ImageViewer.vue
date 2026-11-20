<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/85 p-4"
    role="dialog"
    aria-modal="true"
    :aria-label="ariaLabel"
    @click.self="requestClose"
    @keydown="onKey"
    @touchstart.passive="onTouchStart"
    @touchend.passive="onTouchEnd"
    ref="overlay"
  >
    <!-- Edge navigation arrows -->
    <button
      v-if="hasPrev"
      type="button"
      aria-label="Previous image"
      class="absolute left-2 sm:left-4 top-1/2 -translate-y-1/2 z-10 w-11 h-11 sm:w-12 sm:h-12 rounded-full bg-white/10 hover:bg-white/25 backdrop-blur-sm border border-white/20 text-white flex items-center justify-center transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
      @click="goPrev"
    >
      <i class="pi pi-chevron-left text-lg"></i>
    </button>
    <button
      v-if="hasNext"
      type="button"
      aria-label="Next image"
      class="absolute right-2 sm:right-4 top-1/2 -translate-y-1/2 z-10 w-11 h-11 sm:w-12 sm:h-12 rounded-full bg-white/10 hover:bg-white/25 backdrop-blur-sm border border-white/20 text-white flex items-center justify-center transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
      @click="goNext"
    >
      <i class="pi pi-chevron-right text-lg"></i>
    </button>

    <div class="relative max-h-full max-w-full flex flex-col items-center select-none">
      <img
        v-if="current"
        :src="current.fullUrl"
        :alt="currentAlt"
        class="max-h-[78vh] max-w-[90vw] object-contain mb-4 shadow-lg rounded-lg"
        @load="imageLoaded = true"
        @error="onImageError"
      />
      <div v-if="!imageLoaded" class="text-white mb-4 flex items-center gap-2" aria-live="polite">
        <i class="pi pi-spin pi-spinner"></i>
        <span class="text-sm">Loading...</span>
      </div>

      <div class="flex flex-wrap items-center gap-2 justify-center">
        <!-- Position counter -->
        <div
          v-if="images.length > 1"
          class="px-4 py-2.5 rounded-xl bg-white/10 backdrop-blur-sm border border-white/20 text-white text-sm font-medium tabular-nums"
          aria-live="polite"
        >
          {{ index + 1 }} / {{ images.length }}
        </div>
        <a
          v-if="current"
          :href="current.fullUrl"
          download
          class="px-4 py-2.5 rounded-xl bg-white/10 hover:bg-white/25 backdrop-blur-sm border border-white/20 !text-white text-sm font-medium flex items-center gap-2 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
          aria-label="Download image"
        >
          <i class="pi pi-download"></i>
          <span class="hidden sm:inline">Download</span>
        </a>
        <button
          v-if="allowDelete && current?.id != null"
          type="button"
          class="px-4 py-2.5 rounded-xl bg-red-600/80 hover:bg-red-600 backdrop-blur-sm border border-red-400/40 text-white text-sm font-medium flex items-center gap-2 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
          @click="emitDelete"
        >
          <i class="pi pi-trash"></i>
          <span class="hidden sm:inline">Delete</span>
        </button>
        <button
          type="button"
          class="px-4 py-2.5 rounded-xl bg-white/10 hover:bg-white/25 backdrop-blur-sm border border-white/20 text-white text-sm font-medium flex items-center gap-2 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
          @click="requestClose"
          ref="closeBtn"
        >
          <i class="pi pi-times"></i>
          <span>Close<span class="sr-only"> image viewer</span></span>
        </button>
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

// Swipe navigation (horizontal swipes only; vertical movement is ignored)
let touchStartX = 0;
let touchStartY = 0;
function onTouchStart(e: TouchEvent) {
  const t = e.changedTouches[0];
  if (!t) return;
  touchStartX = t.clientX;
  touchStartY = t.clientY;
}
function onTouchEnd(e: TouchEvent) {
  const t = e.changedTouches[0];
  if (!t) return;
  const dx = t.clientX - touchStartX;
  const dy = t.clientY - touchStartY;
  if (Math.abs(dx) < 50 || Math.abs(dx) < Math.abs(dy)) return;
  if (dx > 0) goPrev(); else goNext();
}

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
  imageLoaded.value = false; // Reset loading state when viewer opens
  lockBody();
  setTimeout(() => { closeBtn.value?.focus(); }, 0);
});

onUnmounted(() => { unlockBody(); });
</script>

<style scoped>
.sr-only { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); border:0; }
</style>
