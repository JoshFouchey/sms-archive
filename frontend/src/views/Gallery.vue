<template>
  <div class="p-4 sm:p-6 max-w-7xl mx-auto">
    <Toast />
    <h1 class="text-2xl font-bold mb-4 text-gray-800">Image Gallery</h1>

    <!-- Contact Filter Dropdown -->
    <div class="flex flex-col sm:flex-row sm:space-x-2 mb-6 space-y-2 sm:space-y-0">
      <Dropdown
        v-model="selectedContactId"
        :options="contactOptions"
        optionLabel="label"
        optionValue="value"
        placeholder="Filter by contact..."
        showClear
        class="flex-1"
        @change="onContactChange"
      />
      <Button
          :label="loading ? 'Searching...' : 'Search'"
          icon="pi pi-search"
          severity="success"
          @click="reloadImages"
          :disabled="loading"
      />
    </div>

    <!-- Image Grid (responsive columns) -->
    <div
        v-if="images.length"
        class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3"
    >
      <div
          v-for="(img, index) in images"
          :key="img.id"
          class="relative overflow-hidden rounded-lg shadow-sm group cursor-pointer bg-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          role="button"
          tabindex="0"
          @click="openImage(index, $event)"
          @keydown.enter.prevent="openImage(index, $event)"
          @keydown.space.prevent="openImage(index, $event)"
      >
        <!-- square container fallback: .aspect-square may require Tailwind aspect-ratio plugin.
             We include inline style fallback using a wrapper class below. -->
        <div class="aspect-square w-full">
          <img
              :src="getThumbnailUrl(img)"
              :alt="getAlt(img)"
              class="w-full h-full object-cover transition-transform duration-200 group-hover:scale-105"
              loading="lazy"
              @error="onThumbError($event, img)"
          />
        </div>

        <button
            @click.stop="deleteImage(img.id)"
            class="absolute top-2 right-2 bg-red-500 text-white px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition"
        >
          ✕
        </button>
      </div>
      <!-- Inline spinner when loading more pages -->
      <div v-if="loading" class="col-span-full flex justify-center my-4">
        <ProgressSpinner style="width:32px; height:32px" strokeWidth="6" />
      </div>
    </div>

    <!-- Skeletons when initial load -->
    <div v-else-if="loading" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
      <div v-for="n in skeletonCount" :key="'skeleton-'+n" class="aspect-square w-full rounded-lg skeleton"></div>
    </div>

    <!-- No Results -->
    <PrimeMessage
        v-else
        severity="warn"
        :closable="false"
        class="mt-4 text-center"
    >
      No results found.
    </PrimeMessage>

    <!-- Infinite scroll sentinel -->
    <div ref="sentinel" class="h-10"></div>

    <!-- Full-size image overlay -->
    <div
      v-if="viewerOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4"
      @click.self="closeViewer"
      @touchstart="onTouchStart"
      @touchend="onTouchEnd"
    >
      <div class="relative max-h-full max-w-full flex flex-col items-center select-none">
        <img
          v-if="currentImage"
          :src="`http://localhost:8080/${normalizePath(currentImage.filePath)}`"
          :alt="currentAlt"
          class="max-h-[80vh] max-w-[90vw] object-contain mb-4 shadow-lg"
          @load="imageLoaded = true"
          @touchstart.stop
        />
        <div v-if="!imageLoaded" class="text-white mb-4">Loading...</div>
        <div class="flex flex-wrap gap-2 justify-center">
          <Button label="Close" icon="pi pi-times" severity="secondary" @click="closeViewer" />
          <Button
            v-if="currentImage"
            label="Delete"
            icon="pi pi-trash"
            severity="danger"
            @click="confirmAndDeleteCurrent"
          />
          <Button
            v-if="hasPrev"
            icon="pi pi-arrow-left"
            severity="secondary"
            text
            @click="prevImage"
          />
          <Button
            v-if="hasNext"
            icon="pi pi-arrow-right"
            severity="secondary"
            text
            @click="nextImage"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from "vue";
import Button from "primevue/button";
import Dropdown from "primevue/dropdown";
import PrimeMessage from "primevue/message";
import ProgressSpinner from "primevue/progressspinner";
import Toast from "primevue/toast";
import { useToast } from "primevue/usetoast";
import { getImages, deleteImageById, getDistinctContacts, type MessagePart, type Contact } from "../services/api";

const toast = useToast();

// Removed text contact string; now using numeric ID
const selectedContactId = ref<number | null>(null);
const contacts = ref<Contact[]>([]);
const contactOptions = computed(() => contacts.value.map(c => ({
  label: c.name ? `${c.name} (${c.number})` : c.number,
  value: c.id
})));

const images = ref<MessagePart[]>([]);
const page = ref(0);
const size = 40; // batch size
const loading = ref(false);
const allLoaded = ref(false);

// Viewer state
const viewerOpen = ref(false);
const currentIndex = ref<number | null>(null);
const imageLoaded = ref(false);

const sentinel = ref<HTMLElement | null>(null);
const skeletonCount = 12; // initial skeleton placeholders

// Scroll & focus preservation
let savedScrollY = 0;
let lastFocusedEl: HTMLElement | null = null;

// Touch swipe state
const touchStartX = ref<number | null>(null);
function onTouchStart(e: TouchEvent) {
  if (e.touches.length === 1) {
    const t = e.touches.item(0);
    if (t) {
      touchStartX.value = t.clientX;
      return;
    }
  }
  // Fallback: reset if no single primary touch
  touchStartX.value = null;
}
function onTouchEnd(e: TouchEvent) {
  if (touchStartX.value == null) return;
  if (!e.changedTouches.length) {
    touchStartX.value = null;
    return;
  }
  const t = e.changedTouches.item(0);
  if (!t) {
    touchStartX.value = null;
    return;
  }
  const dx = t.clientX - touchStartX.value;
  const threshold = 50;
  if (Math.abs(dx) > threshold) {
    if (dx > 0) prevImage(); else nextImage();
  }
  touchStartX.value = null;
}

function openImage(index: number, ev?: Event) {
  currentIndex.value = index;
  viewerOpen.value = true;
  imageLoaded.value = false;
  if (ev && ev.currentTarget instanceof HTMLElement) {
    lastFocusedEl = ev.currentTarget;
  }
  lockBodyScroll();
}
function closeViewer() {
  viewerOpen.value = false;
  currentIndex.value = null;
  unlockBodyScroll();
  if (lastFocusedEl) {
    lastFocusedEl.focus();
  }
}
function prevImage() {
  if (currentIndex.value == null) return;
  if (currentIndex.value > 0) {
    currentIndex.value--;
    imageLoaded.value = false;
  }
}
function nextImage() {
  if (currentIndex.value == null) return;
  if (currentIndex.value < images.value.length - 1) {
    currentIndex.value++;
    imageLoaded.value = false;
  }
}
const hasPrev = computed(() => currentIndex.value != null && currentIndex.value > 0);
const hasNext = computed(() => currentIndex.value != null && currentIndex.value < images.value.length - 1);
const currentImage = computed(() => currentIndex.value == null ? null : images.value[currentIndex.value]);
const currentAlt = computed(() => currentImage.value ? currentImage.value.contentType.replace(/^image\//, "") : "");

async function loadImages() {
  if (loading.value || allLoaded.value) return;
  loading.value = true;
  try {
    const newImages = await getImages(page.value, size, selectedContactId.value ?? undefined);
    if (!Array.isArray(newImages) || newImages.length === 0) {
      if (page.value === 0) {
        // no initial results toast (optional)
      }
      allLoaded.value = true;
    } else {
      images.value.push(...newImages);
      page.value++;
    }
  } catch (err) {
    console.error(err);
    toast.add({ severity: "error", summary: "Load Error", detail: "Could not load images", life: 4000 });
  } finally {
    loading.value = false;
  }
}

function reloadImages() {
  if (viewerOpen.value) closeViewer();
  images.value = [];
  page.value = 0;
  allLoaded.value = false;
  loadImages();
}

function onContactChange() {
  reloadImages();
}

async function deleteImage(id: number) {
  try {
    const ok = await deleteImageById(id);
    if (ok) {
      images.value = images.value.filter((i) => i.id !== id);
      toast.add({ severity: "success", summary: "Deleted", detail: "Image removed", life: 2500 });
      if (currentIndex.value != null) {
        if (currentIndex.value >= images.value.length) {
          currentIndex.value = images.value.length - 1;
        }
        if (images.value.length === 0) closeViewer();
      }
    } else {
      toast.add({ severity: "error", summary: "Delete Failed", detail: "Server error", life: 4000 });
    }
  } catch (e) {
    console.error(e);
    toast.add({ severity: "error", summary: "Delete Failed", detail: "Unexpected error", life: 4000 });
  }
}

function confirmAndDeleteCurrent() {
  if (currentImage.value) {
    deleteImage(currentImage.value.id);
  }
}

function getThumbnailUrl(img: MessagePart) {
  const normalized = normalizePath(img.filePath);
  const thumb = normalized.replace(/(\.\w+)$/, "_thumb.jpg");
  return `http://localhost:8080/${thumb}`;
}

function onThumbError(ev: Event, img: MessagePart) {
  const target = ev.target as HTMLImageElement;
  target.src = `http://localhost:8080/${normalizePath(img.filePath)}`;
}

function getAlt(img: MessagePart) {
  return img.contentType ? img.contentType.replace(/^image\//, "") : "";
}

function normalizePath(p: string) {
  return (p || "").split("\\").join("/");
}

function lockBodyScroll() {
  savedScrollY = window.scrollY;
  document.body.style.position = "fixed";
  document.body.style.top = `-${savedScrollY}px`;
  document.body.style.left = "0";
  document.body.style.right = "0";
  document.body.style.width = "100%";
}
function unlockBodyScroll() {
  document.body.style.position = "";
  document.body.style.top = "";
  document.body.style.left = "";
  document.body.style.right = "";
  document.body.style.width = "";
  window.scrollTo(0, savedScrollY);
}

function handleKey(e: KeyboardEvent) {
  if (!viewerOpen.value) return;
  switch (e.key) {
    case "Escape":
      closeViewer();
      break;
    case "ArrowLeft":
      prevImage();
      break;
    case "ArrowRight":
      nextImage();
      break;
    case "Delete":
    case "Backspace":
      if (currentImage.value) confirmAndDeleteCurrent();
      break;
  }
}

let io: IntersectionObserver | null = null;
onMounted(async () => {
  // Load contacts for dropdown
  try {
    contacts.value = await getDistinctContacts();
  } catch (e) {
    console.error(e);
    toast.add({ severity: "warn", summary: "Contacts", detail: "Could not load contacts", life: 3000 });
  }
  io = new IntersectionObserver((entries) => {
    const first = entries[0];
    if (first && first.isIntersecting) loadImages();
  }, { rootMargin: "200px" });
  if (sentinel.value) io.observe(sentinel.value);
  loadImages();
  window.addEventListener("keydown", handleKey);
});

onUnmounted(() => {
  if (io && sentinel.value) io.unobserve(sentinel.value);
  io = null;
  window.removeEventListener("keydown", handleKey);
  unlockBodyScroll();
});
</script>

<style>
.skeleton {
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 37%, #f0f0f0 63%);
  background-size: 400% 100%;
  animation: shimmer 1.4s ease infinite;
}
@keyframes shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: 0 0; }
}
</style>
