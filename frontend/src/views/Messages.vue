<template>
  <div class="flex h-screen bg-gray-50">
    <!-- Left panel: contacts list -->
    <div class="w-1/3 border-r bg-white p-4 overflow-y-auto">
      <h2 class="text-xl font-bold mb-4">Contacts</h2>

      <Card
          v-for="contact in contacts"
          :key="contact.contactName"
          class="mb-3 p-3 cursor-pointer hover:bg-blue-50 transition rounded-lg shadow-sm"
          @click="selectContact(contact)"
      >
        <div class="flex justify-between items-center">
          <h3 class="font-semibold text-gray-800">{{ contact.contactName }}</h3>
          <span v-if="contact.hasImage" class="text-sm text-gray-400">📷</span>
        </div>
        <p class="text-sm text-gray-600 truncate">{{ contact.lastMessagePreview }}</p>
        <p class="text-xs text-gray-400 mt-1">
          {{ new Date(contact.lastMessageTimestamp).toLocaleString() }}
        </p>
      </Card>
    </div>

    <!-- Right panel: conversation -->
    <div class="flex-1 p-4 overflow-y-auto">
      <h2 class="text-xl font-bold mb-4">
        Conversation with {{ selectedContact?.contactName || "..." }}
      </h2>

      <div v-if="messages.length === 0" class="text-gray-500">
        Select a contact to view conversation
      </div>

      <div v-for="msg in messages" :key="msg.id" class="mb-2">
        <div
            :class="{
            'text-right': msg.sender === 'Me',
            'text-left': msg.sender !== 'Me'
          }"
        >
          <span
              class="inline-block p-2 rounded-md max-w-xs break-words"
              :class="{
              'bg-blue-500 text-white': msg.sender === 'Me',
              'bg-gray-200 text-gray-900': msg.sender !== 'Me'
            }"
          >
            {{ msg.body }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import Card from "primevue/card";

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
  { contactName: "Austin Bostic", lastMessageTimestamp: new Date().toISOString(), lastMessagePreview: "Yeah take the time you need for sure.", hasImage: false },
  { contactName: "Jane Doe", lastMessageTimestamp: new Date().toISOString(), lastMessagePreview: "See you tomorrow!", hasImage: true },
  { contactName: "John Smith", lastMessageTimestamp: new Date().toISOString(), lastMessagePreview: "Got it, thanks!", hasImage: false },
]);

const selectedContact = ref<ContactSummary | null>(null);

const messages = ref<Message[]>([]);

function selectContact(contact: ContactSummary) {
  selectedContact.value = contact;

  // Dummy conversation
  messages.value = [
    { id: 1, sender: "Me", body: "Hey, how’s it going?", timestamp: new Date().toISOString() },
    { id: 2, sender: contact.contactName, body: "Pretty good! You?", timestamp: new Date().toISOString() },
    { id: 3, sender: "Me", body: "All good. Did you see the pics from last weekend?", timestamp: new Date().toISOString() },
    { id: 4, sender: contact.contactName, body: "Yes! They were amazing.", timestamp: new Date().toISOString() },
  ];
}
</script>
