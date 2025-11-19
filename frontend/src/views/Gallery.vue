<template>
  <div class="p-4 sm:p-6 max-w-7xl mx-auto">
    <Toast />
    <ConfirmDialog />
    <h1 class="text-2xl font-bold mb-4 text-gray-800">Image Gallery</h1>

    <!-- Contact Filter Dropdown -->
    <div class="flex flex-col sm:flex-row sm:space-x-2 mb-6 space-y-2 sm:space-y-0">
      <Select
        v-model="selectedContactId"
        :options="contactOptions"
        optionLabel="label"
        optionValue="value"
        placeholder="Filter by contact..."
        filter
        :filterFields="['label']"
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
            @click.stop="requestDelete(img.id)"
            class="absolute top-2 right-2 bg-red-500 text-white px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition"
        >✕</button>
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

    <!-- Full-size image overlay replaced by shared component -->
    <ImageViewer
      v-if="viewerOpen"
      :images="viewerImages"
      :initialIndex="currentIndex ?? 0"
      :allowDelete="true"
      aria-label="Gallery image viewer"
      @close="closeViewer"
      @delete="requestDelete"
      @indexChange="onIndexChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from "vue";
import Button from "primevue/button";
import Select from "primevue/select";
import PrimeMessage from "primevue/message";
import ProgressSpinner from "primevue/progressspinner";
import Toast from "primevue/toast";
import ConfirmDialog from 'primevue/confirmdialog';
import { useToast } from "primevue/usetoast";
import { useConfirm } from 'primevue/useconfirm';
import { getImages, deleteImageById, getDistinctContacts, type GalleryImage, type Contact } from "../services/api";
import ImageViewer from '@/components/ImageViewer.vue';
import type { ViewerImage } from '@/components/ImageViewer.vue';

const toast = useToast();
const confirm = useConfirm();

// Removed text contact string; now using numeric ID
const selectedContactId = ref<number | null>(null);
const contacts = ref<Contact[]>([]);
const contactOptions = computed(() => contacts.value.map(c => ({
  label: c.name ? `${c.name} (${c.number})` : c.number,
  value: c.id
})));

const images = ref<GalleryImage[]>([]);
const page = ref(0);
const size = 40; // batch size
const loading = ref(false);
const allLoaded = ref(false);

// Viewer state
const viewerOpen = ref(false);
const currentIndex = ref<number | null>(null);

// Build viewer image array
const viewerImages = computed<ViewerImage[]>(() => images.value.map(i => ({ id: i.id, fullUrl: getFullImageUrl(i), thumbUrl: getThumbnailUrl(i), contentType: i.contentType })));

function openImage(index: number, ev?: Event) {
  currentIndex.value = index;
  viewerOpen.value = true;
  if (ev && ev.currentTarget instanceof HTMLElement) lastFocusedEl = ev.currentTarget;
  lockBodyScroll();
}
function closeViewer() {
  viewerOpen.value = false;
  currentIndex.value = null;
  unlockBodyScroll();
  if (lastFocusedEl) lastFocusedEl.focus();
}
function onIndexChange(idx: number) { currentIndex.value = idx; }

async function loadImages() {
  if (loading.value || allLoaded.value) return;
  loading.value = true;
  try {
    const response = await getImages(page.value, size, selectedContactId.value ?? undefined);
    const newImages = response.content || [];
    // Filter out images with null filePath
    const validImages = newImages.filter(img => img.filePath != null && img.filePath !== '');
    if (validImages.length === 0) {
      allLoaded.value = true;
    } else {
      images.value.push(...validImages);
      page.value++;
    }
    // If we got fewer items than requested, we've reached the end
    if (newImages.length < size) {
      allLoaded.value = true;
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

function onContactChange() { reloadImages(); }

// Request delete (either start soft-delete or confirm dialog if already pending)
function requestDelete(id: number) {
  confirm.require({
    message: 'Delete this image? This action cannot be undone.',
    header: 'Confirm Deletion',
    icon: 'pi pi-exclamation-triangle',
    acceptLabel: 'Delete',
    rejectLabel: 'Cancel',
    acceptClass: 'p-button-danger',
    accept: () => performDeletion(id),
    reject: () => {}
  });
}

async function performDeletion(id: number) {
  try {
    const ok = await deleteImageById(id);
    if (ok) {
      const oldLength = images.value.length;
      images.value = images.value.filter(i => i.id !== id);
      toast.add({ severity: 'success', summary: 'Deleted', detail: 'Image removed', life: 2500 });
      if (viewerOpen.value) {
        if (currentIndex.value != null) {
          if (currentIndex.value >= images.value.length) currentIndex.value = images.value.length - 1;
          if (images.value.length === 0) closeViewer();
          else if (oldLength !== images.value.length) { /* ensure displayed image updates */ }
        }
      }
    } else {
      toast.add({ severity: 'error', summary: 'Delete Failed', detail: 'Server error', life: 4000 });
    }
  } catch (e) {
    console.error(e);
    toast.add({ severity: 'error', summary: 'Delete Failed', detail: 'Unexpected error', life: 4000 });
  }
}

function extractRelativeMediaPath(fp: string): string | null {
  if (!fp) return null;
  const norm = normalizePath(fp);
  // Look for '/media/messages/' segment or strip absolute prefix like '/app/media/messages/'
  const markers = ['/media/messages/', '/app/media/messages/', 'media/messages/'];
  for (const m of markers) {
    const idx = norm.indexOf(m);
    if (idx >= 0) {
      // If marker already starts with /media/messages/ return from that marker
      if (m === '/media/messages/') return norm.substring(idx + '/media/messages/'.length);
      // Strip everything up to and including marker to get relative part segment(s)
      return norm.substring(idx + m.length);
    }
  }
  // If absolute path, attempt last two segments (contactId/filename)
  const parts = norm.split('/').filter(Boolean);
  if (parts.length >= 2) {
    return parts.slice(-2).join('/');
  }
  return null;
}
function buildBackendMediaUrl(rel: string, thumb: boolean): string {
  if (!rel) return '';
  if (thumb) {
    // replace extension with _thumb.jpg
    const thumbName = rel.replace(/(\.[A-Za-z0-9]{1,6})$/, '_thumb.jpg');
    return `/media/messages/${thumbName}`;
  }
  return `/media/messages/${rel}`;
}
function getThumbnailUrl(img: GalleryImage) {
  const rel = extractRelativeMediaPath(img.filePath);
  if (!rel) return '';
  return buildBackendMediaUrl(rel, true);
}
function getFullImageUrl(img: GalleryImage) {
  const rel = extractRelativeMediaPath(img.filePath);
  if (!rel) return '';
  return buildBackendMediaUrl(rel, false);
}
function onThumbError(ev: Event, img: GalleryImage) {
  const target = ev.target as HTMLImageElement;
  const rel = extractRelativeMediaPath(img.filePath);
  if (!rel) return;
  target.src = buildBackendMediaUrl(rel, false);
}

function getAlt(img: GalleryImage) {
  return img.contentType ? img.contentType.replace(/^image\//, "") : "";
}

function normalizePath(p: string) {
  return (p || "").split("\\").join("/");
}

let savedScrollY = 0;
let lastFocusedEl: HTMLElement | null = null;
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

// (Re-added after refactor) sentinel ref for intersection observer and skeleton count constant
const sentinel = ref<HTMLElement | null>(null);
const skeletonCount = 12;

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
});

onUnmounted(() => {
  if (io && sentinel.value) io.unobserve(sentinel.value);
  io = null;
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
