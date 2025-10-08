<template>
  <div class="flex h-screen bg-gray-100 dark:bg-slate-950 text-gray-900 dark:text-gray-100">
    <!-- Left panel: contacts list -->
    <aside class="w-1/3 max-w-sm border-r border-gray-200 dark:border-slate-700 bg-gray-50/90 dark:bg-slate-900/70 backdrop-blur p-4 overflow-y-auto">
      <h2 class="text-lg font-semibold mb-4 tracking-tight text-gray-700 dark:text-gray-200">
        Contacts
      </h2>

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
          {{ new Date(contact.lastMessageTimestamp).toLocaleString() }}
        </p>
      </div>
    </aside>

    <!-- Right panel: conversation -->
    <main class="flex-1 flex flex-col p-4 overflow-y-auto bg-white/70 dark:bg-slate-900/60 backdrop-blur">
      <h2 class="text-lg font-semibold mb-4 tracking-tight text-gray-700 dark:text-gray-200">
        Conversation with
        <span class="text-blue-600 dark:text-blue-400">
          {{ selectedContact?.contactName || '...' }}
        </span>
      </h2>

      <div
          v-if="messages.length === 0"
          class="mt-8 text-sm text-gray-500 dark:text-gray-500 italic"
      >
        Select a contact to view conversation.
      </div>

      <div
          v-for="msg in messages"
          :key="msg.id"
          class="mb-3 flex flex-col"
          :class="msg.sender === 'Me' ? 'items-end' : 'items-start'"
      >
        <div
            :class="[
            'max-w-[78%] rounded-lg px-3 py-2 shadow-sm text-sm leading-snug',
            msg.sender === 'Me'
              ? 'bg-blue-600 dark:bg-blue-500 text-white'
              : 'bg-gray-200 text-gray-900 dark:bg-slate-700 dark:text-gray-100'
          ]"
        >
          {{ msg.body }}
        </div>
        <span
            class="mt-1 text-[10px] tracking-wide uppercase"
            :class="msg.sender === 'Me'
              ? 'text-blue-400/80 dark:text-blue-300/70'
              : 'text-gray-400 dark:text-gray-500'"
        >
          {{ new Date(msg.timestamp).toLocaleTimeString() }}
        </span>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

interface ContactSummary {
  contactName: string;
  lastMessageTimestamp: string;
  lastMessagePreview: string;
  hasImage: boolean;
}

interface Message {
  id: number;
  sender: string;
  body: string;
  timestamp: string;
}

const contacts = ref<ContactSummary[]>([
  {
    contactName: 'Austin Bostic',
    lastMessageTimestamp: new Date().toISOString(),
    lastMessagePreview: 'Yeah take the time you need for sure.',
    hasImage: false
  },
  {
    contactName: 'Jane Doe',
    lastMessageTimestamp: new Date().toISOString(),
    lastMessagePreview: 'See you tomorrow!',
    hasImage: true
  },
  {
    contactName: 'John Smith',
    lastMessageTimestamp: new Date().toISOString(),
    lastMessagePreview: 'Got it, thanks!',
    hasImage: false
  }
]);

const selectedContact = ref<ContactSummary | null>(null);
const messages = ref<Message[]>([]);

function selectContact(contact: ContactSummary) {
  if (selectedContact.value?.contactName === contact.contactName) return;
  selectedContact.value = contact;
  messages.value = [
    { id: 1, sender: 'Me', body: 'Hey, how’s it going?', timestamp: new Date().toISOString() },
    { id: 2, sender: contact.contactName, body: 'Pretty good! You?', timestamp: new Date().toISOString() },
    { id: 3, sender: 'Me', body: 'All good. Did you see the pics from last weekend?', timestamp: new Date().toISOString() },
    { id: 4, sender: contact.contactName, body: 'Yes! They were amazing.', timestamp: new Date().toISOString() }
  ];
}
</script>
