<template>
  <div class="p-4 sm:p-6 max-w-7xl mx-auto">
    <h1 class="text-2xl font-bold mb-4 text-gray-800">Image Gallery</h1>

    <!-- Search / Filter -->
    <div class="flex flex-col sm:flex-row sm:space-x-2 mb-6 space-y-2 sm:space-y-0">
      <InputText
          v-model="contact"
          placeholder="Filter by contact..."
          class="flex-1"
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
          class="relative overflow-hidden rounded-lg shadow-sm group cursor-pointer bg-gray-100"
          @click="openGallery(index)"
      >
        <!-- square container fallback: .aspect-square may require Tailwind aspect-ratio plugin.
             We include inline style fallback using a wrapper class below. -->
        <div class="aspect-square w-full">
          <img
              :src="getThumbnailUrl(img)"
              :alt="img.contentType"
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
    </div>

    <!-- No Results -->
    <PrimeMessage
        v-else-if="!loading"
        severity="warn"
        :closable="false"
        class="mt-4 text-center"
    >
      No results found.
    </PrimeMessage>

    <!-- Loading Spinner -->
    <div v-if="loading" class="flex justify-center my-6">
      <ProgressSpinner style="width:40px; height:40px" strokeWidth="6" />
    </div>

    <!-- Infinite scroll sentinel -->
    <div ref="sentinel" class="h-10"></div>

    <!-- Full-size viewer (Galleria) -->
    <Galleria
        v-model:visible="displayGalleria"
        v-model:activeIndex="activeIndex"
        :value="images"
        :numVisible="5"
        :circular="true"
        :showItemNavigators="true"
        :showThumbnails="false"
        containerStyle="max-width: 90vw; max-height: 90vh"
        dismissableMask
    >
      <template #item="slotProps">
        <img
            :src="`http://localhost:8080/${normalizePath(slotProps.item.filePath)}`"
            :alt="slotProps.item.contentType"
            class="max-w-full max-h-[80vh] object-contain mx-auto"
        />
      </template>
    </Galleria>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Galleria from "primevue/galleria";
import PrimeMessage from "primevue/message";
import ProgressSpinner from "primevue/progressspinner";
import { getImages, deleteImageById, type MessagePart } from "../services/api";

const contact = ref("");
const images = ref<MessagePart[]>([]);
const page = ref(0);
const size = 5;
const loading = ref(false);
const allLoaded = ref(false);

const displayGalleria = ref(false);
const activeIndex = ref(0);

async function loadImages() {
  if (loading.value || allLoaded.value) return;
  loading.value = true;
  try {
    const newImages = await getImages(contact.value, page.value, size);
    if (!Array.isArray(newImages) || newImages.length === 0) {
      allLoaded.value = true;
    } else {
      images.value.push(...newImages);
      page.value++;
    }
  } catch (err) {
    console.error(err);
  } finally {
    loading.value = false;
  }
}

function reloadImages() {
  images.value = [];
  page.value = 0;
  allLoaded.value = false;
  loadImages();
}

async function deleteImage(id: number) {
  if (!confirm("Delete this image?")) return;
  try {
    const ok = await deleteImageById(id);
    if (ok) {
      images.value = images.value.filter((i) => i.id !== id);
    } else {
      alert("Delete failed.");
    }
  } catch (e) {
    console.error(e);
    alert("Delete failed.");
  }
}

function openGallery(index: number) {
  activeIndex.value = index;
  displayGalleria.value = true;
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

function normalizePath(p: string) {
  return (p || "").replace(/\\/g, "/");
}

const sentinel = ref<HTMLElement | null>(null);

onMounted(() => {
  const observer = new IntersectionObserver((entries) => {
    const first = entries[0];
    if (first && first.isIntersecting) loadImages();
  }, { rootMargin: "200px" });

  if (sentinel.value) observer.observe(sentinel.value);
  loadImages();
});
</script>

<style>
/* Fallback square aspect if Tailwind aspect-ratio is not available */
.aspect-square {
  position: relative;
  width: 100%;
  padding-top: 100%;
  overflow: hidden;
}
.aspect-square > img {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
