<template>
  <div class="space-y-6">
    <Toast />
    <ConfirmDialog />

    <!-- Header Section -->
    <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 rounded-2xl shadow-lg p-6 text-white">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 class="text-4xl font-bold mb-2 flex items-center gap-3">
            <i class="pi pi-images"></i>
            Image Gallery
          </h1>
          <p class="text-blue-100 dark:text-blue-200">Browse and manage your message attachments</p>
        </div>
        <div class="flex items-center gap-2">
          <div class="bg-white/10 backdrop-blur-sm rounded-lg px-4 py-2 border border-white/20">
            <div class="flex items-center gap-2">
              <i class="pi pi-image text-lg"></i>
              <div class="text-left">
                <p class="text-xs text-blue-100">Total Images</p>
                <p class="text-2xl font-bold">{{ images.length }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Filter Section -->
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 border border-gray-200 dark:border-gray-700">
      <div class="flex flex-col sm:flex-row gap-3">
        <div class="flex-1">
          <label class="text-xs font-semibold mb-2 text-gray-700 dark:text-gray-300 uppercase tracking-wide block">
            <i class="pi pi-filter text-xs mr-1"></i>
            Filter by Contact
          </label>
          <Select
            v-model="selectedContactId"
            :options="contactOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="All Contacts"
            filter
            :filterFields="['label']"
            showClear
            class="w-full"
            @change="onContactChange"
          />
        </div>
        <div class="flex items-end">
          <Button
            :label="loading ? 'Loading...' : 'Search'"
            icon="pi pi-search"
            severity="success"
            @click="reloadImages"
            :disabled="loading"
            :loading="loading"
            class="w-full sm:w-auto shadow-sm"
          />
        </div>
      </div>
    </div>

    <!-- Image Grid (responsive columns) -->
    <div
        v-if="images.length"
        class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4"
    >
      <div
          v-for="(img, index) in images"
          :key="img.id"
          class="relative overflow-hidden rounded-xl shadow-md hover:shadow-2xl group cursor-pointer bg-gradient-to-br from-gray-100 to-gray-200 dark:from-gray-800 dark:to-gray-900 focus:outline-none focus:ring-4 focus:ring-blue-500 transition-all duration-300 hover:scale-105"
          role="button"
          tabindex="0"
          @click="openImage(index, $event)"
          @keydown.enter.prevent="openImage(index, $event)"
          @keydown.space.prevent="openImage(index, $event)"
      >
        <!-- square container fallback -->
        <div class="aspect-square w-full relative">
          <img
              :src="getThumbnailUrl(img)"
              :alt="getAlt(img)"
              class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-110"
              loading="lazy"
              @error="onThumbError($event, img)"
          />
          <!-- Overlay on hover -->
          <div class="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-end p-3">
            <div class="text-white text-xs font-medium">
              <i class="pi pi-eye mr-1"></i>
              View
            </div>
          </div>
        </div>

        <button
            @click.stop="requestDelete(img.id)"
            class="absolute top-2 right-2 bg-red-500 hover:bg-red-600 text-white w-8 h-8 rounded-full opacity-0 group-hover:opacity-100 transition-all duration-300 flex items-center justify-center shadow-lg hover:scale-110 z-10"
            title="Delete image"
        >
          <i class="pi pi-trash text-sm"></i>
        </button>
      </div>
      <!-- Inline spinner when loading more pages -->
      <div v-if="loading" class="col-span-full flex justify-center my-8">
        <div class="text-center">
          <ProgressSpinner style="width:48px; height:48px" strokeWidth="4" class="text-blue-600" />
          <p class="text-sm text-gray-600 dark:text-gray-400 mt-2">Loading more images...</p>
        </div>
      </div>
    </div>

    <!-- Skeletons when initial load -->
    <div v-else-if="loading" class="space-y-4">
      <div class="bg-white dark:bg-gray-800 rounded-xl shadow-md p-4 border border-gray-200 dark:border-gray-700">
        <div class="flex items-center gap-2 text-gray-600 dark:text-gray-400">
          <i class="pi pi-spin pi-spinner text-blue-600 dark:text-blue-400"></i>
          <span class="font-medium">Loading gallery...</span>
        </div>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
        <div v-for="n in skeletonCount" :key="'skeleton-'+n" class="aspect-square w-full rounded-xl skeleton shadow-md"></div>
      </div>
    </div>

    <!-- No Results -->
    <div v-else class="bg-white dark:bg-gray-800 rounded-xl shadow-md p-8 border border-gray-200 dark:border-gray-700 text-center">
      <div class="flex flex-col items-center gap-4">
        <div class="bg-yellow-100 dark:bg-yellow-900/30 p-4 rounded-full">
          <i class="pi pi-image text-4xl text-yellow-600 dark:text-yellow-400"></i>
        </div>
        <div>
          <h3 class="text-xl font-semibold text-gray-800 dark:text-gray-200 mb-2">No Images Found</h3>
          <p class="text-gray-600 dark:text-gray-400">Try adjusting your filter or import some messages with images</p>
        </div>
      </div>
    </div>

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

<style scoped>
.skeleton {
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 37%, #f0f0f0 63%);
  background-size: 400% 100%;
  animation: shimmer 1.4s ease infinite;
}

.dark .skeleton {
  background: linear-gradient(90deg, #374151 25%, #4b5563 37%, #374151 63%);
  background-size: 400% 100%;
}

@keyframes shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: 0 0; }
}
</style>
