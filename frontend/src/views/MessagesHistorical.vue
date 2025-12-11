<template>
  <div class="flex h-screen bg-gray-100 dark:bg-slate-950 text-gray-900 dark:text-gray-100">
    <!-- Conversations List Sidebar -->
    <aside
      :class="[
        'w-full md:w-80 lg:w-96 border-r border-gray-200 dark:border-slate-700 bg-gradient-to-b from-gray-50 to-gray-100 dark:from-slate-900 dark:to-slate-950 backdrop-blur overflow-y-auto flex flex-col',
        selectedConversation ? 'hidden md:flex' : 'flex'
      ]"
    >
      <!-- Sidebar Header -->
      <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 p-4 shadow-md">
        <div class="flex items-center gap-3">
          <div class="bg-white/20 backdrop-blur-sm p-2.5 rounded-xl">
            <i class="pi pi-comments text-2xl text-white"></i>
          </div>
          <div>
            <h2 class="text-xl font-bold text-white tracking-tight">Conversations</h2>
            <p class="text-xs text-blue-100 dark:text-blue-200">{{ conversations.length }} total</p>
          </div>
        </div>
      </div>

      <!-- Conversations List -->
      <div class="flex-1 overflow-y-auto p-4 space-y-2">
        <div v-if="contactsLoading" class="flex flex-col items-center justify-center py-8 text-gray-500 dark:text-gray-400">
          <i class="pi pi-spin pi-spinner text-3xl text-blue-600 dark:text-blue-400 mb-2"></i>
          <span class="text-sm">Loading conversations...</span>
        </div>
        <div v-else-if="!conversations.length" class="flex flex-col items-center justify-center py-8 text-gray-500 dark:text-gray-400">
          <i class="pi pi-inbox text-4xl mb-2"></i>
          <span class="text-sm">No conversations yet</span>
        </div>
        <div
          v-for="conversation in conversations"
          :key="conversation.id"
          @click="selectConversation(conversation)"
          :class="[
            'group p-4 rounded-xl border cursor-pointer transition-all duration-200 flex flex-col gap-1.5 shadow-sm hover:shadow-md',
            selectedConversation?.id === conversation.id
              ? 'bg-gradient-to-br from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 border-blue-500 text-white scale-[1.02]'
              : 'bg-white dark:bg-slate-800 border-gray-200 dark:border-slate-700 hover:border-blue-300 dark:hover:border-blue-700 hover:bg-blue-50 dark:hover:bg-slate-700 active:scale-[0.98]'
          ]"
        >
          <div class="flex justify-between items-start gap-2">
            <div class="flex items-center gap-2 flex-1 min-w-0">
              <div :class="[
                'w-10 h-10 rounded-full flex items-center justify-center text-lg font-bold shrink-0',
                selectedConversation?.id === conversation.id
                  ? 'bg-white/20 text-white'
                  : 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
              ]">
                {{ conversation.name.charAt(0).toUpperCase() }}
              </div>
              <div class="flex-1 min-w-0">
                <h3 :class="[
                  'font-semibold truncate text-sm',
                  selectedConversation?.id === conversation.id ? 'text-white' : 'text-gray-900 dark:text-gray-100'
                ]">
                  {{ conversation.name }}
                </h3>
                <p :class="[
                  'text-xs truncate mt-0.5',
                  selectedConversation?.id === conversation.id ? 'text-blue-100' : 'text-gray-600 dark:text-gray-400'
                ]">
                  {{ conversation.lastMessagePreview }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <!-- Main Content Area -->
    <main
      :class="[
        'flex-1 flex bg-gradient-to-br from-gray-50 via-white to-gray-50 dark:from-slate-900 dark:via-slate-950 dark:to-slate-900',
        !selectedConversation ? 'hidden md:flex' : 'flex'
      ]"
    >
      <!-- Backdrop for mobile timeline -->
      <div
        v-if="showTimeline && selectedConversation"
        @click="showTimeline = false"
        class="fixed inset-0 bg-black/50 z-30 lg:hidden"
      ></div>

      <!-- Timeline Sidebar (only when conversation selected) -->
      <aside
        v-if="selectedConversation"
        :class="[
          'w-72 border-r border-gray-200 dark:border-slate-700 bg-white dark:bg-slate-800 overflow-y-auto flex-col',
          showTimeline ? 'flex absolute lg:relative inset-y-0 left-0 z-40 lg:z-auto shadow-xl lg:shadow-none' : 'hidden lg:flex'
        ]"
      >
        <!-- Timeline Header -->
        <div class="bg-gradient-to-r from-purple-600 to-indigo-500 dark:from-purple-700 dark:to-indigo-600 p-3 shadow-md flex items-center justify-between">
          <div class="flex items-center gap-2">
            <i class="pi pi-calendar text-white text-lg"></i>
            <h3 class="text-sm font-bold text-white">Timeline</h3>
          </div>
          <button @click="showTimeline = false" class="lg:hidden text-white hover:bg-white/20 rounded p-1">
            <i class="pi pi-times"></i>
          </button>
        </div>

        <!-- Timeline Loading -->
        <div v-if="timelineLoading" class="flex items-center justify-center py-8">
          <i class="pi pi-spin pi-spinner text-2xl text-purple-600 dark:text-purple-400"></i>
        </div>

        <!-- Timeline Content -->
        <div v-else-if="timeline" class="p-3 space-y-2">
          <div v-for="year in timeline.years" :key="year.year" class="border border-gray-200 dark:border-slate-700 rounded-lg overflow-hidden">
            <!-- Year Header -->
            <button
              @click="toggleYear(year.year)"
              class="w-full bg-gray-100 dark:bg-slate-700 hover:bg-gray-200 dark:hover:bg-slate-600 px-3 py-2 flex items-center justify-between text-left transition-colors"
            >
              <div class="flex items-center gap-2">
                <i :class="['pi text-xs transition-transform', expandedYears.has(year.year) ? 'pi-chevron-down' : 'pi-chevron-right']"></i>
                <span class="font-bold text-sm">{{ year.year }}</span>
              </div>
              <span class="text-xs bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300 px-2 py-0.5 rounded-full">
                {{ year.count }}
              </span>
            </button>

            <!-- Months List -->
            <div v-if="expandedYears.has(year.year)" class="bg-white dark:bg-slate-800">
              <button
                v-for="month in year.months"
                :key="`${year.year}-${month.month}`"
                @click="jumpToMonth(month)"
                class="w-full px-4 py-2 text-left hover:bg-blue-50 dark:hover:bg-slate-700 transition-colors flex items-center justify-between group border-t border-gray-100 dark:border-slate-700"
              >
                <div class="flex items-center gap-2">
                  <i class="pi pi-calendar text-xs text-gray-400 group-hover:text-blue-600 dark:group-hover:text-blue-400"></i>
                  <span class="text-xs font-medium text-gray-700 dark:text-gray-300 group-hover:text-blue-600 dark:group-hover:text-blue-400">
                    {{ getMonthName(month.month) }}
                  </span>
                </div>
                <span class="text-xs text-gray-500 dark:text-gray-400 group-hover:text-blue-600 dark:group-hover:text-blue-400">
                  {{ month.count }}
                </span>
              </button>
            </div>
          </div>
        </div>
      </aside>

      <!-- Conversation View -->
      <div class="flex-1 flex flex-col relative">
        <!-- Header -->
        <div class="bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 p-4 shadow-md flex items-center gap-3">
          <!-- Back button (mobile) -->
          <button
            v-if="selectedConversation"
            @click="clearConversation"
            class="md:hidden flex items-center justify-center w-10 h-10 rounded-xl bg-white/20 hover:bg-white/30 backdrop-blur-sm active:scale-95 transition-all"
          >
            <i class="pi pi-arrow-left text-white"></i>
          </button>

          <!-- Timeline toggle (mobile/tablet) -->
          <button
            v-if="selectedConversation"
            @click="showTimeline = !showTimeline"
            class="lg:hidden flex items-center justify-center w-10 h-10 rounded-xl bg-white/20 hover:bg-white/30 backdrop-blur-sm active:scale-95 transition-all"
          >
            <i class="pi pi-calendar text-white"></i>
          </button>

          <div class="flex-1 min-w-0">
            <h2 class="text-xl font-bold text-white tracking-tight truncate">
              {{ selectedConversation?.name || 'Select a conversation' }}
            </h2>
            <p v-if="selectedConversation" class="text-xs text-blue-100 dark:text-blue-200 mt-0.5">
              {{ totalMessages }} messages
            </p>
          </div>
        </div>

        <!-- Empty State -->
        <div v-if="!selectedConversation" class="flex-1 flex items-center justify-center p-8">
          <div class="text-center">
            <div class="bg-blue-100 dark:bg-blue-900/30 p-6 rounded-full inline-flex mb-4">
              <i class="pi pi-comments text-5xl text-blue-600 dark:text-blue-400"></i>
            </div>
            <h3 class="text-xl font-semibold text-gray-800 dark:text-gray-200 mb-2">No Conversation Selected</h3>
            <p class="text-sm text-gray-600 dark:text-gray-400">Choose a conversation to view messages</p>
          </div>
        </div>

        <!-- Messages Area -->
        <div v-else class="flex-1 overflow-y-auto p-4 space-y-3">
          <!-- Loading State -->
          <div v-if="messagesLoading" class="flex flex-col items-center justify-center py-12">
            <i class="pi pi-spin pi-spinner text-4xl text-blue-600 dark:text-blue-400 mb-3"></i>
            <span class="text-sm text-gray-600 dark:text-gray-400">Loading messages...</span>
          </div>

          <!-- Messages List -->
          <div v-else-if="messages.length" class="space-y-3">
            <!-- Top Sentinel for Infinite Scroll (Load Older) -->
            <div ref="topSentinel" class="h-2"></div>

            <!-- Loading older indicator -->
            <div v-if="olderLoading" class="flex items-center justify-center py-2">
              <div class="bg-gray-200 dark:bg-slate-700 px-3 py-1.5 rounded-full text-xs text-gray-600 dark:text-gray-400 flex items-center gap-2">
                <i class="pi pi-spin pi-spinner text-xs"></i>
                Loading older...
              </div>
            </div>

            <!-- Sticky Current Date Indicator -->
            <div
              v-if="currentVisibleDate"
              class="sticky top-0 z-10 flex justify-center py-2"
            >
              <div class="bg-purple-600 dark:bg-purple-700 text-white px-4 py-1.5 rounded-full text-xs font-semibold shadow-lg backdrop-blur-sm">
                {{ formatDateIndicator(currentVisibleDate) }}
              </div>
            </div>

            <div
              v-for="msg in messages"
              :key="msg.id"
              :data-timestamp="msg.timestamp"
              :data-message-id="msg.id"
              class="message-item flex"
              :class="msg.direction === 'OUTBOUND' ? 'justify-end' : 'justify-start'"
            >
              <div
                :class="[
                  'max-w-[85%] rounded-2xl px-4 py-2.5 shadow-md text-sm leading-relaxed transition-all hover:shadow-lg',
                  msg.direction === 'OUTBOUND'
                    ? 'bg-gradient-to-br from-blue-600 to-cyan-500 text-white'
                    : 'bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 border border-gray-200 dark:border-slate-700'
                ]"
              >
                <div v-if="msg.body">{{ msg.body }}</div>
                <div class="text-[10px] mt-1 opacity-75">{{ formatTime(msg.timestamp) }}</div>
              </div>
            </div>

            <!-- Loading newer indicator -->
            <div v-if="newerLoading" class="flex items-center justify-center py-2">
              <div class="bg-gray-200 dark:bg-slate-700 px-3 py-1.5 rounded-full text-xs text-gray-600 dark:text-gray-400 flex items-center gap-2">
                <i class="pi pi-spin pi-spinner text-xs"></i>
                Loading newer...
              </div>
            </div>

            <!-- Bottom Sentinel for Infinite Scroll (Load Newer) -->
            <div ref="bottomSentinel" class="h-2"></div>
          </div>

          <!-- No messages -->
          <div v-else class="flex flex-col items-center justify-center py-12 text-gray-500 dark:text-gray-400">
            <i class="pi pi-inbox text-5xl mb-3"></i>
            <span class="text-sm font-medium">No messages in this conversation</span>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  getAllConversations,
  getConversationMessages,
  getConversationTimeline,
  getConversationMessagesByDateRange,
  type ConversationSummary,
  type ConversationTimeline,
  type Message,
} from '../services/api';

const route = useRoute();
const router = useRouter();

// State
const conversations = ref<ConversationSummary[]>([]);
const selectedConversation = ref<ConversationSummary | null>(null);
const messages = ref<Message[]>([]);
const timeline = ref<ConversationTimeline | null>(null);
const expandedYears = ref(new Set<number>());
const showTimeline = ref(false);

// Infinite scroll sentinels
const topSentinel = ref<HTMLElement | null>(null);
const bottomSentinel = ref<HTMLElement | null>(null);

// Loading states
const contactsLoading = ref(false);
const messagesLoading = ref(false);
const timelineLoading = ref(false);
const olderLoading = ref(false);
const newerLoading = ref(false);

// Pagination
const currentPage = ref(0);
const pageSize = ref(100);
const totalMessages = ref(0);
const hasMoreOlder = ref(false);
const hasMoreNewer = ref(false);

// Current visible date for sticky indicator (updates as you scroll)
const currentVisibleDate = ref<string | null>(null);

// Track first visible message for date indicator
const messageObserver = ref<IntersectionObserver | null>(null);

// Month names
const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function getMonthName(month: number): string {
  return monthNames[month - 1] || '';
}

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

function formatDateIndicator(timestamp: string): string {
  const d = new Date(timestamp);
  return d.toLocaleString(undefined, {
    year: 'numeric',
    month: 'long',
  });
}

// Update sticky date indicator based on visible messages
function setupMessageObserver() {
  // Clean up existing observer
  if (messageObserver.value) {
    messageObserver.value.disconnect();
  }

  // Create observer to track which message is at the top of viewport
  messageObserver.value = new IntersectionObserver(
    (entries) => {
      // Find the first message that's visible at the top
      const visibleMessages = entries
        .filter(entry => entry.isIntersecting)
        .sort((a, b) => {
          // Sort by position in viewport (top to bottom)
          return a.boundingClientRect.top - b.boundingClientRect.top;
        });

      if (visibleMessages.length > 0) {
        const firstVisible = visibleMessages[0];
        if (firstVisible) {
          const topMessage = firstVisible.target as HTMLElement;
          const timestamp = topMessage.dataset.timestamp;
          if (timestamp) {
            currentVisibleDate.value = timestamp;
          }
        }
      }
    },
    {
      threshold: 0,
      rootMargin: '0px 0px -80% 0px' // Trigger when message is in top 20% of viewport
    }
  );

  // Observe all message elements
  const messageElements = document.querySelectorAll('.message-item');
  messageElements.forEach(el => {
    messageObserver.value?.observe(el);
  });
}

// Watch for message changes and update observers
watch(messages, () => {
  // Wait for DOM update then setup observers
  setTimeout(() => {
    setupMessageObserver();
  }, 100);
});

// Setup infinite scroll observers
onMounted(() => {
  // Observer for top sentinel (load older messages)
  const topObserver = new IntersectionObserver(
    (entries) => {
      const entry = entries[0];
      if (entry && entry.isIntersecting && hasMoreOlder.value && !olderLoading.value && !messagesLoading.value) {
        loadOlderMessages();
      }
    },
    { threshold: 0.1 }
  );

  // Observer for bottom sentinel (load newer messages)
  const bottomObserver = new IntersectionObserver(
    (entries) => {
      const entry = entries[0];
      if (entry && entry.isIntersecting && hasMoreNewer.value && !newerLoading.value && !messagesLoading.value) {
        loadNewerMessages();
      }
    },
    { threshold: 0.1 }
  );

  // Start observing when sentinels are available
  watch([topSentinel, bottomSentinel], () => {
    if (topSentinel.value) {
      topObserver.observe(topSentinel.value);
    }
    if (bottomSentinel.value) {
      bottomObserver.observe(bottomSentinel.value);
    }
  });

  // Cleanup observers on unmount
  onBeforeUnmount(() => {
    topObserver.disconnect();
    bottomObserver.disconnect();
    if (messageObserver.value) {
      messageObserver.value.disconnect();
    }
  });
});

// Load conversations
async function loadConversations() {
  contactsLoading.value = true;
  try {
    conversations.value = await getAllConversations();
  } catch (e) {
    console.error('Failed to load conversations', e);
  } finally {
    contactsLoading.value = false;
  }
}

// Select conversation
async function selectConversation(conversation: ConversationSummary) {
  selectedConversation.value = conversation;
  router.push({ name: 'messages', params: { id: conversation.id } });
  await Promise.all([loadMessages(), loadTimeline()]);
}

function clearConversation() {
  selectedConversation.value = null;
  messages.value = [];
  timeline.value = null;
  router.push({ name: 'messages' });
}

// Load messages
async function loadMessages() {
  if (!selectedConversation.value) return;
  messagesLoading.value = true;
  try {
    const res = await getConversationMessages(
      selectedConversation.value.id,
      currentPage.value,
      pageSize.value,
      'desc'
    );
    messages.value = res.content.reverse(); // Show chronological order
    totalMessages.value = res.totalElements;
    hasMoreOlder.value = !res.first;
    hasMoreNewer.value = !res.last;
  } catch (e) {
    console.error('Failed to load messages', e);
  } finally {
    messagesLoading.value = false;
  }
}

// Load timeline
async function loadTimeline() {
  if (!selectedConversation.value) return;
  timelineLoading.value = true;
  try {
    timeline.value = await getConversationTimeline(selectedConversation.value.id);
    // Auto-expand most recent year
    if (timeline.value?.years && timeline.value.years.length > 0) {
      const latestYear = timeline.value.years[timeline.value.years.length - 1]?.year;
      if (latestYear) {
        expandedYears.value.add(latestYear);
      }
    }
  } catch (e) {
    console.error('Failed to load timeline', e);
  } finally {
    timelineLoading.value = false;
  }
}

// Toggle year expansion
function toggleYear(year: number) {
  if (expandedYears.value.has(year)) {
    expandedYears.value.delete(year);
  } else {
    expandedYears.value.add(year);
  }
}

// Jump to month
async function jumpToMonth(month: any) {
  if (!selectedConversation.value) return;
  messagesLoading.value = true;

  try {
    // Find the "page" that contains this month by calculating how many messages
    // exist before this date, then load that page
    const year = month.year;
    const monthNum = month.month;
    const targetDate = `${year}-${String(monthNum).padStart(2, '0')}-01T00:00:00Z`;

    // Load messages starting from this date going forward
    const res = await getConversationMessagesByDateRange(
      selectedConversation.value.id,
      targetDate,
      '9999-12-31T23:59:59Z', // Far future to get messages from this point onward
      0,
      pageSize.value,
      'asc'
    );

    messages.value = res.content;
    totalMessages.value = res.totalElements;

    // Enable infinite scroll in both directions
    // There are likely older messages before this month
    hasMoreOlder.value = true;
    // There might be newer messages after this batch
    hasMoreNewer.value = res.content.length >= pageSize.value;

    // Reset page tracking - we'll use date-based loading from here
    currentPage.value = 0;
  } catch (e) {
    console.error('Failed to jump to month', e);
  } finally {
    messagesLoading.value = false;
  }
}

// Load older/newer messages (timestamp-based for continuous scrolling)
async function loadOlderMessages() {
  if (!selectedConversation.value || olderLoading.value || !messages.value.length) return;

  const oldestMessage = messages.value[0];
  if (!oldestMessage) return;

  olderLoading.value = true;

  try {
    // Get the oldest message timestamp
    const oldestTimestamp = oldestMessage!.timestamp;

    // Load messages older than this timestamp
    const res = await getConversationMessagesByDateRange(
      selectedConversation.value.id,
      '1970-01-01T00:00:00Z', // Beginning of time
      oldestTimestamp, // Up to (but not including) oldest current message
      0,
      pageSize.value,
      'desc' // Get most recent of the older messages
    );

    if (res.content.length > 0) {
      // Prepend older messages (they come in desc order, so reverse)
      messages.value = [...res.content.reverse(), ...messages.value];
      hasMoreOlder.value = res.content.length >= pageSize.value;
    } else {
      hasMoreOlder.value = false;
    }
  } catch (e) {
    console.error('Failed to load older messages', e);
  } finally {
    olderLoading.value = false;
  }
}

async function loadNewerMessages() {
  if (!selectedConversation.value || newerLoading.value || !messages.value.length) return;

  const newestMessage = messages.value[messages.value.length - 1];
  if (!newestMessage) return;

  newerLoading.value = true;

  try {
    // Get the newest message timestamp
    const newestTimestamp = newestMessage!.timestamp;

    // Load messages newer than this timestamp
    const res = await getConversationMessagesByDateRange(
      selectedConversation.value.id,
      newestTimestamp, // From (but not including) newest current message
      '9999-12-31T23:59:59Z', // Far future
      0,
      pageSize.value,
      'asc' // Get oldest of the newer messages first
    );

    if (res.content.length > 0) {
      // Filter out the duplicate (if API includes the boundary message)
      const newMessages = res.content.filter(m => m.id !== newestMessage!.id);
      messages.value = [...messages.value, ...newMessages];
      hasMoreNewer.value = newMessages.length >= pageSize.value;
    } else {
      hasMoreNewer.value = false;
    }
  } catch (e) {
    console.error('Failed to load newer messages', e);
  } finally {
    newerLoading.value = false;
  }
}

// Initialize
onMounted(async () => {
  await loadConversations();

  // Check for conversation ID in route
  const id = route.params.id;
  if (id) {
    const conv = conversations.value.find(c => c.id === Number(id));
    if (conv) {
      await selectConversation(conv);
    }
  }
});
</script>
