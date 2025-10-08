<template>
  <div class="flex h-screen bg-gray-100 dark:bg-slate-950 text-gray-900 dark:text-gray-100">
    <!-- Contacts panel -->
    <aside class="w-1/3 max-w-sm border-r border-gray-200 dark:border-slate-700 bg-gray-50/90 dark:bg-slate-900/70 backdrop-blur p-4 overflow-y-auto">
      <h2 class="text-lg font-semibold mb-4 tracking-tight text-gray-700 dark:text-gray-200">
        Contacts
      </h2>

      <div v-if="contactsLoading" class="text-sm text-gray-500 dark:text-gray-400">Loading contacts...</div>
      <div v-else-if="contactsError" class="text-sm text-red-600 dark:text-red-400">{{ contactsError }}</div>
      <div v-else-if="!contacts.length" class="text-sm text-gray-500 dark:text-gray-400">No contacts.</div>

      <div
          v-for="contact in contacts"
          :key="contact.contactName"
          @click="selectContact(contact)"
          :class="[
          'group mb-3 p-3 rounded-lg border cursor-pointer transition-colors duration-150',
          'flex flex-col gap-1',
          selectedContact?.contactName === contact.contactName
            ? 'bg-blue-600 border-blue-500 text-white shadow-sm'
            : 'bg-white/90 dark:bg-slate-800/80 border-gray-200 dark:border-slate-700 hover:bg-blue-50 dark:hover:bg-slate-700/70'
        ]"
      >
        <div class="flex justify-between items-center">
          <h3
              :class="[
              'font-medium truncate',
              selectedContact?.contactName === contact.contactName
                ? 'text-white'
                : 'text-gray-800 dark:text-gray-100'
            ]"
          >
            {{ contact.contactName }}
          </h3>
          <span
              v-if="contact.hasImage"
              :class="[
              'text-sm',
              selectedContact?.contactName === contact.contactName
                ? 'text-blue-100'
                : 'text-gray-400 dark:text-gray-500 group-hover:text-blue-500 dark:group-hover:text-blue-400'
            ]"
          >
            📷
          </span>
        </div>

        <p
            class="text-xs truncate"
            :class="selectedContact?.contactName === contact.contactName
            ? 'text-blue-50/90'
            : 'text-gray-600 dark:text-gray-400'"
        >
          {{ contact.lastMessagePreview }}
        </p>
        <p
            class="text-[10px] uppercase tracking-wide"
            :class="selectedContact?.contactName === contact.contactName
            ? 'text-blue-100/70'
            : 'text-gray-400 dark:text-gray-500'"
        >
          {{ formatDate(contact.lastMessageTimestamp) }}
        </p>
      </div>
    </aside>

    <!-- Conversation panel -->
    <main class="flex-1 flex flex-col p-4 overflow-y-auto bg-white/70 dark:bg-slate-900/60 backdrop-blur">
      <h2 class="text-lg font-semibold mb-4 tracking-tight text-gray-700 dark:text-gray-200">
        Conversation with
        <span class="text-blue-600 dark:text-blue-400">
          {{ selectedContact?.contactName || '...' }}
        </span>
      </h2>

      <div v-if="messagesLoading" class="text-sm text-gray-500 dark:text-gray-400">Loading messages...</div>
      <div v-else-if="messagesError" class="text-sm text-red-600 dark:text-red-400">{{ messagesError }}</div>
      <div v-else-if="!selectedContact" class="mt-8 text-sm text-gray-500 dark:text-gray-500 italic">
        Select a contact to view conversation.
      </div>
      <div v-else-if="!messages.length" class="text-sm text-gray-500 dark:text-gray-400">
        No messages.
      </div>

      <div
          v-for="msg in messages"
          :key="msg.id"
          class="mb-3 flex flex-col"
          :class="msg.isMe ? 'items-end' : 'items-start'"
      >
        <div
            :class="[
            'max-w-[78%] rounded-lg px-3 py-2 shadow-sm text-sm leading-snug',
            msg.isMe
              ? 'bg-blue-600 dark:bg-blue-500 text-white'
              : 'bg-gray-200 text-gray-900 dark:bg-slate-700 dark:text-gray-100'
          ]"
        >
          {{ msg.body }}
        </div>
        <span
            class="mt-1 text-[10px] tracking-wide uppercase"
            :class="msg.isMe
            ? 'text-blue-400/80 dark:text-blue-300/70'
            : 'text-gray-400 dark:text-gray-500'"
        >
          {{ formatTime(msg.timestamp) }}
        </span>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import {
  getAllContactSummaries,
  getMessagesByContactId,
  type ContactSummary as ApiContactSummary,
  type Message as ApiMessage,
  type PagedResponse
} from '../services/api';

interface UiMessage {
  id: number;
  body: string;
  timestamp: string;
  isMe: boolean;
}

const contacts = ref<ApiContactSummary[]>([]);
const contactsLoading = ref(true);
const contactsError = ref('');

const selectedContact = ref<ApiContactSummary | null>(null);

const messages = ref<UiMessage[]>([]);
const messagesLoading = ref(false);
const messagesError = ref('');

onMounted(async () => {
  try {
    contacts.value = await getAllContactSummaries();
  } catch (e: any) {
    contactsError.value = e?.message || 'Failed to load contacts';
  } finally {
    contactsLoading.value = false;
  }
});

async function selectContact(contact: ApiContactSummary) {
  if (selectedContact.value?.contactId === contact.contactId) return;
  selectedContact.value = contact;
  messages.value = [];
  messagesError.value = '';
  messagesLoading.value = true;
  try {
    const paged: PagedResponse<ApiMessage> = await getMessagesByContactId(contact.contactId, 0, 100, 'desc');
    messages.value = paged.content.map(m => ({
      id: m.id,
      body: m.body && m.body.trim().length ? m.body : '[media]',
      timestamp: m.timestamp,
      isMe: (m.sender?.toLowerCase?.() === 'me')
    }));
  } catch (e: any) {
    messagesError.value = e?.message || 'Failed to load messages';
  } finally {
    messagesLoading.value = false;
  }
}

function formatDate(iso: string) {
  return iso ? new Date(iso).toLocaleString() : '';
}
function formatTime(iso: string) {
  return iso ? new Date(iso).toLocaleTimeString() : '';
}
</script>