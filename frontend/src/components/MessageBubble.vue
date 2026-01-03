<template>
  <div 
    :data-message-id="message.id"
    class="flex"
    :class="message.direction === 'OUTBOUND' ? 'justify-end' : 'justify-start'"
    v-show="!isReactionMessage(message)"
  >
    <div class="max-w-[85%]">
      <div
        class="relative"
        :class="[
          'rounded-2xl shadow-md text-sm leading-relaxed transition-all duration-300',
          message.direction === 'OUTBOUND'
            ? 'bg-gradient-to-br from-blue-600 to-cyan-500 text-white'
            : 'bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 border border-gray-200 dark:border-slate-700',
          highlightClass,
          dimmed ? 'opacity-30' : ''
        ]"
      >
        <!-- Sender name header for group messages -->
        <div
          v-if="message.direction === 'INBOUND' && isGroupChat && (message.senderContactName || message.senderContactNumber)"
          class="px-4 pt-2.5 pb-1.5 border-b border-gray-200 dark:border-slate-600"
        >
          <div class="flex items-center gap-2">
            <span 
              class="w-2 h-2 rounded-full flex-shrink-0"
              :class="getParticipantColor(message.senderContactId)"
            ></span>
            <span class="font-semibold text-xs text-gray-700 dark:text-gray-300">
              {{ message.senderContactName || message.senderContactNumber || 'Unknown' }}
            </span>
          </div>
        </div>

        <!-- Message content -->
        <div class="px-4 py-2.5">
          <div v-if="message.body && message.body !== '[media]'" class="whitespace-pre-wrap break-words">
            {{ message.body }}
          </div>

          <!-- Image thumbnails -->
          <div v-if="imageParts.length" class="flex flex-wrap gap-2 mt-2">
            <div
              v-for="img in imageParts"
              :key="img.id"
              class="relative group cursor-pointer overflow-hidden rounded-xl bg-black/10 dark:bg-black/30 transition-all hover:scale-105"
              :class="img.isSingle ? 'w-48 h-48' : 'w-32 h-32'"
              @click="$emit('imageClick', img.globalIndex)"
              role="button"
              tabindex="0"
              aria-label="Open full size image"
            >
              <img
                :src="img.thumbUrl"
                :alt="img.contentType || 'attachment'"
                class="w-full h-full object-cover transition-transform duration-200 group-hover:scale-110"
                loading="lazy"
                @error="handleImageError"
              />
            </div>
          </div>

          <div 
            class="text-[10px] mt-1 opacity-75"
            :class="groupedReactions.length ? 'mr-16' : ''"
          >
            {{ formatTime(message.timestamp) }}
          </div>
        </div>

        <!-- Reactions overlay -->
        <ul
          v-if="groupedReactions.length"
          class="absolute -bottom-2 right-2 flex gap-1"
          :aria-label="'Reactions for message ' + message.id"
        >
          <li
            v-for="(r, idx) in groupedReactions"
            :key="idx"
            class="bg-white dark:bg-slate-800 border-2 border-gray-300 dark:border-slate-600 rounded-full px-2.5 py-1 text-xs shadow-md flex items-center gap-1"
            :title="r.tooltip"
          >
            <span>{{ r.emoji }}</span>
            <span v-if="r.count > 1" class="text-[10px] font-bold">{{ r.count }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { Message } from '../services/api';

interface ImagePart {
  id: number;
  fullUrl: string;
  thumbUrl: string;
  contentType: string;
  isSingle: boolean;
  globalIndex: number;
}

interface GroupedReaction {
  emoji: string;
  count: number;
  tooltip: string;
}

interface ParsedReaction {
  emoji: string;
  targetMessageBody: string;
  targetNormalizedBody: string;
  senderName?: string;
  targetMessageId?: number;
}

const props = defineProps<{
  message: Message;
  isGroupChat?: boolean;
  participantColorMap?: Map<string, string>;
  reactionIndex?: Map<number, ParsedReaction[]>;
  highlightClass?: string;
  dimmed?: boolean;
}>();

defineEmits<{
  imageClick: [index: number];
}>();

const participantColors = [
  'bg-blue-700',
  'bg-green-700',
  'bg-purple-700',
  'bg-orange-700',
  'bg-pink-700',
  'bg-teal-700',
  'bg-indigo-700',
  'bg-rose-700',
  'bg-amber-700',
  'bg-cyan-700',
];

function getParticipantColor(senderContactId: number | undefined | null): string {
  const defaultColor = 'bg-gray-200';

  if (!senderContactId || !props.participantColorMap) {
    return defaultColor;
  }

  const map = props.participantColorMap;
  const id = String(senderContactId);

  if (!map.has(id)) {
    const colorIndex = map.size % participantColors.length;
    const color = participantColors[colorIndex] ?? defaultColor;
    map.set(id, color);
  }

  return map.get(id) || defaultColor;
}

function normalizeForMatch(text: string): string {
  return (text || '')
    .replace(/[\u201C\u201D]/g, '"')
    .replace(/[\u2000-\u200F\uFEFF]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function parseReaction(msg: Message): ParsedReaction | undefined {
  if (!msg.body) return undefined;
  
  const normalizedBody = normalizeForMatch(msg.body);
  const match = normalizedBody.match(/^(.+?)\s+to\s*"(.+?)"\s*"?\s*$/);
  if (!match || match.length < 3) return undefined;
  
  const rawEmoji = match[1] ?? '';
  const rawTarget = match[2] ?? '';
  if (!rawEmoji || !rawTarget) return undefined;
  
  const emoji = rawEmoji.trim();
  if (emoji.length > 12) return undefined;
  
  const senderName = msg.senderContactName || msg.senderContactNumber || undefined;
  
  return {
    emoji,
    targetMessageBody: rawTarget,
    targetNormalizedBody: normalizeForMatch(rawTarget),
    ...(senderName ? { senderName } : {})
  };
}

function isReactionMessage(msg: Message): boolean {
  return !!parseReaction(msg);
}

const groupedReactions = computed<GroupedReaction[]>(() => {
  if (!props.reactionIndex) return [];
  
  const reactions = props.reactionIndex.get(props.message.id) || [];
  if (!reactions.length) return [];
  
  const counts = new Map<string, { emoji: string; count: number; senders: string[] }>();
  for (const r of reactions) {
    const key = r.emoji;
    if (!counts.has(key)) {
      counts.set(key, { emoji: r.emoji, count: 0, senders: [] });
    }
    const entry = counts.get(key)!;
    entry.count += 1;
    if (r.senderName) entry.senders.push(r.senderName);
  }
  
  return Array.from(counts.values()).map(e => ({
    emoji: e.emoji,
    count: e.count,
    tooltip: e.senders.length ? `${e.emoji} by ${e.senders.join(', ')}` : `${e.emoji}`
  }));
});

function normalizePath(p: string) {
  return (p || '').replace(/\\/g, '/');
}

function extractRelativeMediaPath(fp: string): string | null {
  if (!fp) return null;
  const norm = normalizePath(fp);
  const markers = ['/media/messages/', '/app/media/messages/', 'media/messages/'];
  for (const m of markers) {
    const idx = norm.indexOf(m);
    if (idx >= 0) {
      if (m === '/media/messages/') return norm.substring(idx + '/media/messages/'.length);
      return norm.substring(idx + m.length);
    }
  }
  const parts = norm.split('/').filter(Boolean);
  if (parts.length >= 2) return parts.slice(-2).join('/');
  return null;
}

function buildMediaUrl(rel: string, thumb: boolean): string {
  if (!rel) return '';
  if (thumb) return `/media/messages/${rel.replace(/(\.[A-Za-z0-9]{1,6})$/, '_thumb.jpg')}`;
  return `/media/messages/${rel}`;
}

const imageParts = computed<ImagePart[]>(() => {
  const result: ImagePart[] = [];
  if (!Array.isArray(props.message.parts)) return result;

  const images = props.message.parts.filter((p) => p.contentType && p.contentType.startsWith('image'));
  const single = images.length === 1;

  for (const p of images) {
    const rel = extractRelativeMediaPath(p.filePath || '');
    if (!rel) continue;
    const fullUrl = buildMediaUrl(rel, false);
    const thumbUrl = buildMediaUrl(rel, true);
    result.push({
      id: p.id,
      fullUrl,
      thumbUrl,
      contentType: p.contentType,
      isSingle: single,
      globalIndex: -1 // Will be set by parent if needed
    });
  }

  return result;
});

function formatTime(timestamp: string): string {
  const d = new Date(timestamp);
  return d.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function handleImageError(event: Event) {
  const img = event.target as HTMLImageElement;
  console.warn('Failed to load image:', img.src);
  img.style.display = 'none';
}
</script>
