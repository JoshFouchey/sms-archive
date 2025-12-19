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
      <div class="flex-1 flex flex-col overflow-hidden">
        <!-- Search bar -->
        <div class="p-4 pb-2">
          <div class="relative">
            <input
              v-model="conversationSearchQuery"
              type="text"
              placeholder="Search conversations..."
              class="w-full px-4 py-2 pl-10 text-sm border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            <i class="pi pi-search absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-xs"></i>
            <button
              v-if="conversationSearchQuery"
              @click="conversationSearchQuery = ''"
              class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
            >
              <i class="pi pi-times text-xs"></i>
            </button>
          </div>
        </div>

        <!-- Conversations list -->
        <div class="flex-1 overflow-y-auto px-4 pb-4 space-y-2">
          <div v-if="contactsLoading" class="flex flex-col items-center justify-center py-8 text-gray-500 dark:text-gray-400">
            <i class="pi pi-spin pi-spinner text-3xl text-blue-600 dark:text-blue-400 mb-2"></i>
            <span class="text-sm">Loading conversations...</span>
          </div>
          <div v-else-if="!filteredConversations.length && !conversationSearchQuery" class="flex flex-col items-center justify-center py-8 text-gray-500 dark:text-gray-400">
            <i class="pi pi-inbox text-4xl mb-2"></i>
            <span class="text-sm">No conversations yet</span>
          </div>
          <div v-else-if="!filteredConversations.length && conversationSearchQuery" class="flex flex-col items-center justify-center py-8 text-gray-500 dark:text-gray-400">
            <i class="pi pi-search text-4xl mb-2"></i>
            <span class="text-sm">No conversations match "{{ conversationSearchQuery }}"</span>
          </div>
          <div
            v-for="conversation in filteredConversations"
            :key="conversation.id"
          :class="[
            'group p-4 rounded-xl border transition-all duration-200 flex flex-col gap-1.5 shadow-sm hover:shadow-md relative',
            selectedConversation?.id === conversation.id
              ? 'bg-gradient-to-br from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 border-blue-500 text-white scale-[1.02]'
              : 'bg-white dark:bg-slate-800 border-gray-200 dark:border-slate-700 hover:border-blue-300 dark:hover:border-blue-700 hover:bg-blue-50 dark:hover:bg-slate-700',
            showActionsMenu === conversation.id ? 'z-50' : ''
          ]"
        >
          <div class="flex justify-between items-start gap-2">
            <div class="flex items-center gap-2 flex-1 min-w-0 cursor-pointer" @click="selectConversation(conversation)">
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
            
            <!-- Action menu button -->
            <div class="relative flex-shrink-0" :class="showActionsMenu === conversation.id ? 'z-50' : 'z-10'">
              <button
                @click.stop="toggleActionsMenu(conversation.id)"
                :class="[
                  'p-2 rounded-lg transition-all',
                  selectedConversation?.id === conversation.id
                    ? 'hover:bg-white/20 text-white'
                    : 'hover:bg-gray-200 dark:hover:bg-slate-600 text-gray-600 dark:text-gray-400'
                ]"
                title="Actions"
              >
                <i class="pi pi-ellipsis-v text-xs"></i>
              </button>
              
              <!-- Actions dropdown menu -->
              <div
                v-if="showActionsMenu === conversation.id"
                class="absolute right-0 top-full mt-1 bg-white dark:bg-slate-700 rounded-lg shadow-xl border border-gray-200 dark:border-slate-600 py-1 z-50 min-w-[150px]"
              >
                <button
                  @click.stop="openRenameDialog(conversation); showActionsMenu = null"
                  class="w-full px-4 py-2 text-left text-sm hover:bg-gray-100 dark:hover:bg-slate-600 flex items-center gap-2 text-gray-700 dark:text-gray-300"
                >
                  <i class="pi pi-pencil text-xs"></i>
                  <span>Rename</span>
                </button>
                <button
                  @click.stop="confirmDelete(conversation); showActionsMenu = null"
                  class="w-full px-4 py-2 text-left text-sm hover:bg-red-50 dark:hover:bg-red-900/30 flex items-center gap-2 text-red-600 dark:text-red-400"
                >
                  <i class="pi pi-trash text-xs"></i>
                  <span>Delete</span>
                </button>
              </div>
            </div>
          </div>
        </div>
        </div>
      </div>
    </aside>

    <!-- Main Content Area -->
    <main
      :class="[
        'flex-1 flex flex-col bg-gradient-to-br from-gray-50 via-white to-gray-50 dark:from-slate-900 dark:via-slate-950 dark:to-slate-900 overflow-hidden',
        !selectedConversation ? 'hidden md:flex' : 'flex'
      ]"
    >
      <!-- Header -->
      <div class="flex-shrink-0 bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-700 dark:to-cyan-600 p-4 shadow-md">
        <div class="flex items-center gap-3">
          <!-- Back button (mobile) -->
          <button
            v-if="selectedConversation"
            @click="clearConversation"
            class="md:hidden flex items-center justify-center w-10 h-10 rounded-xl bg-white/20 hover:bg-white/30 backdrop-blur-sm active:scale-95 transition-all"
          >
            <i class="pi pi-arrow-left text-white"></i>
          </button>

          <div class="flex-1 min-w-0">
            <h2 class="text-xl font-bold text-white tracking-tight truncate">
              {{ selectedConversation?.name || 'Select a conversation' }}
            </h2>
            <p v-if="selectedConversation && !isSearchViewMode" class="text-xs text-blue-100 dark:text-blue-200 mt-0.5">
              {{ totalMessages }} messages
            </p>
            <!-- Search View Mode Info -->
            <p v-if="isSearchViewMode" class="text-xs text-blue-100 dark:text-blue-200 mt-0.5 flex items-center gap-2">
              <i class="pi pi-search text-xs"></i>
              Match {{ currentMatchIndex + 1 }} of {{ searchMatches.length }}
              <span class="text-white/50">•</span>
              Showing {{ searchContextMessages.length }} messages with context
            </p>
          </div>
          
          <!-- Exit Search View button -->
          <button
            v-if="isSearchViewMode"
            @click="exitSearchView"
            class="flex items-center gap-2 px-3 py-2 rounded-xl bg-white/20 hover:bg-white/30 backdrop-blur-sm active:scale-95 transition-all text-white text-sm font-medium"
            title="Exit search view"
          >
            <i class="pi pi-times"></i>
            <span class="hidden sm:inline">Exit Search</span>
          </button>
        </div>

        <!-- Search Bar (only when conversation selected) -->
        <div v-if="selectedConversation" class="mt-3 flex gap-2">
          <div class="flex-1 relative">
            <input
              v-model="searchQuery"
              type="text"
              placeholder="Search messages..."
              class="w-full px-4 py-2 pr-10 rounded-xl bg-white/20 backdrop-blur-sm text-white placeholder-white/70 border border-white/30 focus:bg-white/30 focus:border-white/50 focus:outline-none transition-all"
              @keyup.enter="applySearch"
            />
            <i class="pi pi-search absolute right-3 top-1/2 -translate-y-1/2 text-white/70"></i>
          </div>
          
          <button
            @click="toggleFilters"
            :class="[
              'px-4 py-2 rounded-xl backdrop-blur-sm border transition-all',
              showFilters
                ? 'bg-white/30 border-white/50'
                : 'bg-white/20 border-white/30 hover:bg-white/30'
            ]"
            title="More filters"
          >
            <i class="pi pi-filter text-white"></i>
          </button>
          <button
            @click="applySearch"
            class="px-4 py-2 rounded-xl bg-white/20 hover:bg-white/30 backdrop-blur-sm border border-white/30 transition-all"
            title="Search"
          >
            <i class="pi pi-search text-white"></i>
          </button>
          <button
            v-if="isSearchActive"
            @click="clearSearch"
            class="px-4 py-2 rounded-xl bg-white/20 hover:bg-white/30 backdrop-blur-sm border border-white/30 transition-all"
            title="Clear search"
          >
            <i class="pi pi-times text-white"></i>
          </button>
        </div>

        <!-- Loading Status Indicators -->
        <div v-if="selectedConversation && loadingInBackground" class="mt-3">
          <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-700 rounded-xl p-3">
            <div class="flex items-center gap-2 text-blue-700 dark:text-blue-300">
              <i class="pi pi-spin pi-spinner text-sm flex-shrink-0"></i>
              <span class="text-sm font-medium truncate">
                Loading full history... ({{ messages.length.toLocaleString() }} / {{ totalMessages.toLocaleString() }} messages)
              </span>
            </div>
          </div>
        </div>

        <div v-else-if="selectedConversation && fullyLoaded && messages.length > 200" class="mt-3">
          <div class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-700 rounded-xl p-3">
            <div class="flex items-center gap-2 text-green-700 dark:text-green-300">
              <i class="pi pi-check-circle text-sm flex-shrink-0"></i>
              <span class="text-sm font-medium truncate">
                ✓ All {{ messages.length.toLocaleString() }} messages loaded (search ready)
              </span>
            </div>
          </div>
        </div>

        <!-- Advanced Filters (collapsible) -->
        <div
          v-if="selectedConversation && showFilters"
          class="mt-3 p-4 rounded-xl bg-white/10 backdrop-blur-sm border border-white/20 space-y-3 overflow-hidden"
        >
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3 min-w-0">
            <!-- Date From -->
            <div class="min-w-0">
              <label class="block text-xs text-white/90 mb-1.5 font-medium">From Date</label>
              <input
                v-model="dateFrom"
                type="date"
                class="w-full px-3 py-2 rounded-lg bg-white/20 backdrop-blur-sm text-white border border-white/30 focus:bg-white/30 focus:border-white/50 focus:outline-none transition-all text-sm min-w-0"
              />
            </div>

            <!-- Date To -->
            <div class="min-w-0">
              <label class="block text-xs text-white/90 mb-1.5 font-medium">To Date</label>
              <input
                v-model="dateTo"
                type="date"
                class="w-full px-3 py-2 rounded-lg bg-white/20 backdrop-blur-sm text-white border border-white/30 focus:bg-white/30 focus:border-white/50 focus:outline-none transition-all text-sm min-w-0"
              />
            </div>

            <!-- Sender Filter -->
            <div class="min-w-0">
              <label class="block text-xs text-white/90 mb-1.5 font-medium">Sender</label>
              <select
                v-model="selectedSender"
                class="w-full px-3 py-2 rounded-lg bg-white/20 backdrop-blur-sm text-white border border-white/30 focus:bg-white/30 focus:border-white/50 focus:outline-none transition-all text-sm min-w-0"
              >
                <option value="" class="bg-slate-700">All Senders</option>
                <option
                  v-for="sender in uniqueSenders"
                  :key="sender"
                  :value="sender"
                  class="bg-slate-700"
                >
                  {{ sender }}
                </option>
              </select>
            </div>
          </div>
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

      <!-- Search View Mode (completely separate) -->
      <div v-if="isSearchViewMode" class="flex-1 overflow-y-auto p-4 pb-20 bg-gradient-to-br from-gray-50 via-white to-gray-50 dark:from-slate-900 dark:via-slate-950 dark:to-slate-900">
        <!-- Search Loading -->
        <div v-if="searchContextLoading" class="flex flex-col items-center justify-center py-12">
          <i class="pi pi-spin pi-spinner text-4xl text-blue-600 dark:text-blue-400 mb-3"></i>
          <span class="text-sm text-gray-600 dark:text-gray-400">Loading search results...</span>
        </div>

        <!-- Search Results -->
        <div v-else-if="searchContextMessages.length" class="max-w-4xl mx-auto space-y-3">
          <div
            v-for="msg in searchContextMessages"
            :key="msg.id"
            :data-message-id="msg.id"
            class="flex"
            :class="msg.direction === 'OUTBOUND' ? 'justify-end' : 'justify-start'"
            v-show="!isReactionMessage(msg)"
          >
            <div class="max-w-[85%]">
              <div
                class="relative"
                :class="[
                  'rounded-2xl shadow-md text-sm leading-relaxed transition-all duration-300',
                  msg.direction === 'OUTBOUND'
                    ? 'bg-gradient-to-br from-blue-600 to-cyan-500 text-white'
                    : 'bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 border border-gray-200 dark:border-slate-700',
                  isCurrentMatch(msg.id) ? 'ring-4 ring-yellow-400 dark:ring-yellow-500 shadow-2xl scale-[1.02]' : '',
                ]"
              >
                <div class="px-4 py-3">
                  <div v-if="msg.body" class="whitespace-pre-wrap break-words">{{ msg.body }}</div>
                </div>
              </div>
              <div class="text-[10px] mt-1 opacity-75 px-1">
                {{ formatTime(msg.timestamp) }}
              </div>
            </div>
          </div>

          <!-- Navigation Controls -->
          <div class="sticky bottom-4 flex justify-center gap-3 mt-6">
            <button
              @click="prevMatch"
              :disabled="currentMatchIndex === 0"
              class="flex items-center gap-2 px-6 py-3 rounded-xl bg-white dark:bg-slate-800 border-2 border-gray-300 dark:border-slate-600 hover:border-blue-500 dark:hover:border-blue-400 disabled:opacity-50 disabled:cursor-not-allowed text-gray-900 dark:text-gray-100 font-semibold shadow-lg hover:shadow-xl transition-all"
            >
              <i class="pi pi-chevron-left"></i>
              <span>Previous Match</span>
            </button>
            
            <div class="flex items-center px-6 py-3 rounded-xl bg-blue-600 text-white font-semibold shadow-lg">
              Match {{ currentMatchIndex + 1 }} of {{ searchMatches.length }}
            </div>

            <button
              @click="nextMatch"
              :disabled="currentMatchIndex === searchMatches.length - 1"
              class="flex items-center gap-2 px-6 py-3 rounded-xl bg-white dark:bg-slate-800 border-2 border-gray-300 dark:border-slate-600 hover:border-blue-500 dark:hover:border-blue-400 disabled:opacity-50 disabled:cursor-not-allowed text-gray-900 dark:text-gray-100 font-semibold shadow-lg hover:shadow-xl transition-all"
            >
              <span>Next Match</span>
              <i class="pi pi-chevron-right"></i>
            </button>
          </div>
        </div>

        <!-- No Results -->
        <div v-else class="flex flex-col items-center justify-center py-12">
          <i class="pi pi-inbox text-5xl text-gray-400 mb-3"></i>
          <span class="text-sm text-gray-600 dark:text-gray-400">No search results</span>
        </div>
      </div>

      <!-- Normal Messages Area -->
      <div v-else ref="messagesContainer" class="flex-1 overflow-y-auto p-4 pb-20 space-y-3 min-h-0" @scroll="handleScroll">
        <!-- Loading State -->
        <div v-if="messagesLoading" class="flex flex-col items-center justify-center py-12">
          <i class="pi pi-spin pi-spinner text-4xl text-blue-600 dark:text-blue-400 mb-3"></i>
          <span class="text-sm text-gray-600 dark:text-gray-400">Loading messages...</span>
        </div>

        <!-- Messages List -->
        <div v-else-if="messages.length" class="space-y-3">
          <!-- Active search/filter indicator -->
          <div v-if="isSearchActive && searchMatches.length === 0" class="flex items-center justify-center py-2">
            <div class="bg-yellow-100 dark:bg-yellow-900/30 px-4 py-2 rounded-full text-xs text-yellow-700 dark:text-yellow-300 flex items-center gap-2 font-medium">
              <i class="pi pi-exclamation-triangle"></i>
              No matches found
            </div>
          </div>

          <!-- Loading older indicator at top (only when not searching) -->
          <div v-if="olderLoading && !isSearchActive" class="flex items-center justify-center py-2">
            <div class="bg-gray-200 dark:bg-slate-700 px-3 py-1.5 rounded-full text-xs text-gray-600 dark:text-gray-400 flex items-center gap-2">
              <i class="pi pi-spin pi-spinner text-xs"></i>
              Loading older messages...
            </div>
          </div>
          
          <!-- Hint to scroll up for more (when not loading and has more) -->
          <div v-else-if="hasMoreOlder && !isSearchActive && !fullyLoaded" class="flex items-center justify-center py-2">
            <div class="bg-blue-50 dark:bg-blue-900/20 px-3 py-1.5 rounded-full text-xs text-blue-600 dark:text-blue-400 flex items-center gap-2">
              <i class="pi pi-arrow-up text-xs"></i>
              Scroll up to load older messages
            </div>
          </div>

          <div
            v-for="msg in messages"
            :key="msg.id"
            :data-message-id="msg.id"
            class="flex"
            :class="msg.direction === 'OUTBOUND' ? 'justify-end' : 'justify-start'"
            v-show="!isReactionMessage(msg)"
          >
            <div class="max-w-[85%]">
              <div
                class="relative"
                :class="[
                  'rounded-2xl shadow-md text-sm leading-relaxed transition-all duration-300',
                  msg.direction === 'OUTBOUND'
                    ? 'bg-gradient-to-br from-blue-600 to-cyan-500 text-white'
                    : 'bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 border border-gray-200 dark:border-slate-700',
                  isCurrentMatch(msg.id) ? 'ring-4 ring-amber-400 dark:ring-amber-500 shadow-2xl scale-[1.03]' : '',
                  isMatch(msg.id) && !isCurrentMatch(msg.id) ? (msg.direction === 'OUTBOUND' ? 'ring-2 ring-amber-300/50 dark:ring-amber-400/50' : 'bg-amber-50 dark:bg-amber-900/20 border-amber-200 dark:border-amber-800') : '',
                  isSearchActive && !isMatch(msg.id) ? 'opacity-30' : ''
                ]"
              >
                <!-- Sender name header for group messages (inside bubble) -->
                <div
                  v-if="msg.direction === 'INBOUND' && selectedConversation?.participantCount && selectedConversation.participantCount >= 2 && (msg.senderContactName || msg.senderContactNumber)"
                  class="px-4 pt-2.5 pb-1.5 border-b border-gray-200 dark:border-slate-600"
                >
                  <div class="flex items-center gap-2">
                    <span 
                      class="w-2 h-2 rounded-full flex-shrink-0"
                      :class="getParticipantColor(msg.senderContactId)"
                    ></span>
                    <span class="font-semibold text-xs text-gray-700 dark:text-gray-300">{{ msg.senderContactName || msg.senderContactNumber || 'Unknown' }}</span>
                  </div>
                </div>
                
                <!-- Message content -->
                <div class="px-4 py-2.5">
                  <div v-if="msg.body && msg.body !== '[media]'">{{ msg.body }}</div>

                  <!-- Image thumbnails -->
                  <div v-if="getImageParts(msg).length" class="flex flex-wrap gap-2 mt-2">
                    <div
                      v-for="img in getImageParts(msg)"
                      :key="img.id"
                      class="relative group cursor-pointer overflow-hidden rounded-xl bg-black/10 dark:bg-black/30 transition-all hover:scale-105"
                      :class="img.isSingle ? 'w-48 h-48' : 'w-32 h-32'"
                      @click="openImage(img.globalIndex)"
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
                    :class="getGroupedReactions(msg.id).length ? 'mr-16' : ''"
                  >{{ formatTime(msg.timestamp) }}</div>
                </div>
                
                <!-- Reactions overlay -->
                <ul
                  v-if="getGroupedReactions(msg.id).length"
                  class="absolute -bottom-2 right-2 flex gap-1"
                  :aria-label="'Reactions for message ' + msg.id"
                >
                  <li
                    v-for="(r, idx) in getGroupedReactions(msg.id)"
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
        </div>

        <!-- No messages -->
        <div v-else class="flex flex-col items-center justify-center py-12 text-gray-500 dark:text-gray-400">
          <i class="pi pi-inbox text-5xl mb-3"></i>
          <span class="text-sm font-medium">No messages in this conversation</span>
        </div>
      </div>

      <!-- Image Viewer Component -->
      <ImageViewer
        v-if="viewerOpen"
        :images="viewerImages"
        :initialIndex="currentIndex ?? 0"
        :allowDelete="false"
        aria-label="Message image viewer"
        @close="closeViewer"
        @indexChange="onIndexChange"
      />
    </main>

    <!-- Rename Dialog -->
    <div v-if="showRenameDialog" class="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div class="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-md w-full p-6">
        <h3 class="text-xl font-bold text-gray-900 dark:text-gray-100 mb-4">Rename Conversation</h3>
        <input
          v-model="newConversationName"
          type="text"
          placeholder="Enter new name..."
          class="w-full px-4 py-2 rounded-lg border border-gray-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:outline-none"
          @keyup.enter="submitRename"
          @keyup.escape="cancelRename"
          autofocus
        />
        <div class="flex gap-3 mt-6">
          <button
            @click="cancelRename"
            class="flex-1 px-4 py-2 rounded-lg border border-gray-300 dark:border-slate-600 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 transition-colors"
          >
            Cancel
          </button>
          <button
            @click="submitRename"
            :disabled="!newConversationName.trim()"
            class="flex-1 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white transition-colors"
          >
            Rename
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  getAllConversations,
  getConversationMessages,
  getAllConversationMessages,
  getConversationMessageCount,
  searchWithinConversation,
  getMessageContext,
  renameConversation,
  deleteConversation,
  type ConversationSummary,
  type Message,
} from '../services/api';
import ImageViewer from '@/components/ImageViewer.vue';
import type { ViewerImage } from '@/components/ImageViewer.vue';

const route = useRoute();
const router = useRouter();

// State
const conversations = ref<ConversationSummary[]>([]);
const conversationSearchQuery = ref('');
const selectedConversation = ref<ConversationSummary | null>(null);
const messages = ref<Message[]>([]);
const messagesContainer = ref<HTMLElement | null>(null);

// Loading states
const contactsLoading = ref(false);
const messagesLoading = ref(false);
const olderLoading = ref(false);

// Pagination
const currentPage = ref(0);
const totalMessages = ref(0);
const hasMoreOlder = ref(false);

// Search and filter state
const searchQuery = ref('');
const showFilters = ref(false);
const dateFrom = ref('');
const dateTo = ref('');
const selectedSender = ref<string>('');
const isSearchActive = ref(false);
const currentMatchIndex = ref(0);
const searchMatches = ref<number[]>([]); // Array of message IDs that match

// Context-based search view state
const isSearchViewMode = ref(false); // Whether we're in focused search mode showing only context
const searchContextMessages = ref<Message[]>([]); // Messages to show in search view (match + context)
const searchContextLoading = ref(false);

// Full conversation loading state
const fullyLoaded = ref(false);
const loadingInBackground = ref(false);

// Rename/Delete state
const showRenameDialog = ref(false);
const conversationToRename = ref<ConversationSummary | null>(null);
const newConversationName = ref('');
const showActionsMenu = ref<number | null>(null); // Track which conversation's menu is open

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

// Image handling functions (matching Messages.vue approach)
interface ImagePart {
  id: number;
  fullUrl: string;
  thumbUrl: string;
  contentType: string;
  isSingle: boolean;
  globalIndex: number;
}

// Image viewer state
const viewerOpen = ref(false);
const currentIndex = ref<number | null>(null);
let savedScrollY = 0;

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

// Cache for message image parts
const messageImageCache = new Map<number, ImagePart[]>();

function getImageParts(msg: Message): ImagePart[] {
  // Check cache first
  if (messageImageCache.has(msg.id)) {
    return messageImageCache.get(msg.id)!;
  }

  const result: ImagePart[] = [];
  if (!Array.isArray(msg.parts)) return result;

  const imageParts = msg.parts.filter((p) => p.contentType && p.contentType.startsWith('image'));
  const single = imageParts.length === 1;

  for (const p of imageParts) {
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
      globalIndex: -1 // Will be set by allImages computed
    });
  }

  // Cache the result
  messageImageCache.set(msg.id, result);
  return result;
}

// Computed property for all images across all messages with global indices
const allImages = computed(() => {
  const acc: ImagePart[] = [];
  let idx = 0;
  for (const msg of messages.value) {
    const imgs = getImageParts(msg);
    for (const img of imgs) {
      img.globalIndex = idx++;
      acc.push(img);
    }
  }
  return acc;
});

// Convert to ViewerImage format
const viewerImages = computed<ViewerImage[]>(() =>
  allImages.value.map(img => ({
    id: img.id,
    fullUrl: img.fullUrl,
    thumbUrl: img.thumbUrl,
    contentType: img.contentType
  }))
);

// Get unique senders for filter dropdown
const uniqueSenders = computed(() => {
  const senders = new Set<string>();
  for (const msg of messages.value) {
    if (msg.direction === 'INBOUND' && msg.contactName) {
      senders.add(msg.contactName);
    }
  }
  return Array.from(senders).sort();
});

// Reactions support
interface ParsedReaction {
  emoji: string;
  targetMessageBody: string;
  targetNormalizedBody: string;
  senderName?: string;
  targetMessageId?: number;
}

interface MessageWithReaction extends Message {
  reaction?: ParsedReaction;
  normalizedBody?: string;
}

const reactionIndex = ref(new Map<number, ParsedReaction[]>()); // messageId -> reactions

function normalizeForMatch(text: string): string {
  return (text || '')
    .replace(/[\u201C\u201D]/g, '"')  // Replace curly quotes with regular quotes
    .replace(/[\u2000-\u200F\uFEFF]/g, '')  // Remove zero-width and special space characters
    .replace(/\s+/g, ' ')
    .trim();
}

function parseReaction(msg: Message): ParsedReaction | undefined {
  if (!msg.body) return undefined;
  
  const normalizedBody = normalizeForMatch(msg.body);
  
  // Pattern matches: emoji to "text" or emoji to "text "" (with extra trailing quotes)
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

function rebuildReactionIndex() {
  reactionIndex.value.clear();
  const priorMessages: MessageWithReaction[] = [];
  
  for (const msg of messages.value) {
    const reaction = parseReaction(msg);
    const msgWithReaction = msg as MessageWithReaction;
    
    if (reaction) {
      msgWithReaction.reaction = reaction;
      msgWithReaction.normalizedBody = normalizeForMatch(msg.body || '');
      
      // Find the target message by matching normalized body
      for (let i = priorMessages.length - 1; i >= 0; i--) {
        const candidate = priorMessages[i];
        if (!candidate || !candidate.body) continue;
        
        const candidateNormalized = candidate.normalizedBody || normalizeForMatch(candidate.body);
        if (candidateNormalized === reaction.targetNormalizedBody) {
          reaction.targetMessageId = candidate.id;
          const arr = reactionIndex.value.get(candidate.id) || [];
          arr.push(reaction);
          reactionIndex.value.set(candidate.id, arr);
          break;
        }
      }
    } else {
      msgWithReaction.normalizedBody = normalizeForMatch(msg.body || '');
      priorMessages.push(msgWithReaction);
    }
  }
}

function getGroupedReactions(messageId: number): { emoji: string; count: number; tooltip: string }[] {
  const reactions = reactionIndex.value.get(messageId) || [];
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
}

function isReactionMessage(msg: Message): boolean {
  return !!parseReaction(msg);
}

// Check if a message matches the current search/filter criteria
// Backend search now handles filtering - no need for client-side messageMatchesSearch function

// Filtered messages - NOW returns ALL messages, not just matches
// Filtered conversations based on search query
const filteredConversations = computed(() => {
  if (!conversationSearchQuery.value.trim()) {
    return conversations.value;
  }
  
  const query = conversationSearchQuery.value.toLowerCase();
  
  return conversations.value.filter(conv => {
    // Search in conversation name
    if (conv.name.toLowerCase().includes(query)) {
      return true;
    }
    
    // Search in last message preview
    if (conv.lastMessagePreview?.toLowerCase().includes(query)) {
      return true;
    }
    
    return false;
  });
});

// Color palette for group conversation participants
const participantColors = [
  'bg-blue-700 text-white',
  'bg-green-700 text-white',
  'bg-purple-700 text-white',
  'bg-orange-700 text-white',
  'bg-pink-700 text-white',
  'bg-teal-700 text-white',
  'bg-indigo-700 text-white',
  'bg-rose-700 text-white',
  'bg-amber-700 text-white',
  'bg-cyan-700 text-white',
];

// Map to store participant -> color assignments for current conversation
const participantColorMap = ref(new Map<string, string>());

function getParticipantColor(senderContactId: number | undefined | null): string {
  const defaultColor = 'bg-gray-200 text-gray-900 dark:bg-slate-700 dark:text-gray-100';

  // Return default color if no identifier
  if (!senderContactId) {
    return defaultColor;
  }

  const map = participantColorMap.value;
  const id = String(senderContactId);

  // Assign color if not already assigned
  if (!map.has(id)) {
    const colorIndex = map.size % participantColors.length;
    const color = participantColors[colorIndex] ?? defaultColor;
    map.set(id, color);
  }

  return map.get(id) || defaultColor;
}

function handleImageError(event: Event) {
  const img = event.target as HTMLImageElement;
  console.warn('Failed to load image:', img.src);
  img.style.display = 'none';
}

// Image viewer functions
function openImage(index: number) {
  currentIndex.value = index;
  viewerOpen.value = true;
  lockBodyScroll();
}

function closeViewer() {
  viewerOpen.value = false;
  currentIndex.value = null;
  unlockBodyScroll();
}

function onIndexChange(newIndex: number) {
  currentIndex.value = newIndex;
}

function lockBodyScroll() {
  savedScrollY = window.scrollY || window.pageYOffset;
  document.body.style.position = 'fixed';
  document.body.style.top = `-${savedScrollY}px`;
  document.body.style.left = '0';
  document.body.style.right = '0';
  document.body.style.width = '100%';
}

function unlockBodyScroll() {
  document.body.style.position = '';
  document.body.style.top = '';
  document.body.style.left = '';
  document.body.style.right = '';
  document.body.style.width = '';
  window.scrollTo(0, savedScrollY);
}

// Keyboard navigation for viewer
function handleKey(e: KeyboardEvent) {
  if (!viewerOpen.value) return;
  if (e.key === 'Escape') {
    closeViewer();
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKey);
  unlockBodyScroll();
});

// Search and filter functions (backend search)
async function applySearch() {
  if (!selectedConversation.value || !searchQuery.value.trim()) return;
  
  searchContextLoading.value = true;
  
  try {
    // Call backend search API - works even without loading all messages!
    const searchResult = await searchWithinConversation(
      selectedConversation.value.id,
      searchQuery.value.trim()
    );
    
    isSearchActive.value = true;
    searchMatches.value = searchResult.matchIds;
    currentMatchIndex.value = 0;
    
    // Enter search view mode showing first match with context
    if (searchMatches.value.length > 0) {
      isSearchViewMode.value = true;
      await loadSearchContext(0);
    }
  } catch (error) {
    console.error('Search failed:', error);
  } finally {
    searchContextLoading.value = false;
  }
}

// Load context around a specific match (25 before + match + 25 after)
async function loadSearchContext(matchIndex: number) {
  if (searchMatches.value.length === 0) return;
  
  const targetMessageId = searchMatches.value[matchIndex];
  
  if (!targetMessageId) {
    return;
  }
  
  searchContextLoading.value = true;
  
  try {
    // Call backend API to get context around this message (backend cached for 24 hours)
    const contextData = await getMessageContext(targetMessageId, 25, 25);
    
    // Combine before + center + after messages
    const contextMessages = [
      ...contextData.before.reverse(), // API returns before messages newest-first, so reverse
      contextData.center,
      ...contextData.after
    ];
    
    // Instantly replace with new context - no scroll animation
    searchContextMessages.value = contextMessages;
  } catch (error) {
    console.error('Failed to load search context:', error);
  } finally {
    searchContextLoading.value = false;
  }
}

function clearSearch() {
  searchQuery.value = '';
  dateFrom.value = '';
  dateTo.value = '';
  selectedSender.value = '';
  isSearchActive.value = false;
  showFilters.value = false;
  searchMatches.value = [];
  currentMatchIndex.value = 0;
  isSearchViewMode.value = false;
  searchContextMessages.value = [];
}

function exitSearchView() {
  isSearchViewMode.value = false;
  searchContextMessages.value = [];
  // Keep search active but return to full message view
}

async function nextMatch() {
  if (searchMatches.value.length === 0) return;
  currentMatchIndex.value = (currentMatchIndex.value + 1) % searchMatches.value.length;
  
  if (isSearchViewMode.value) {
    await loadSearchContext(currentMatchIndex.value);
  } else {
    scrollToMatch(currentMatchIndex.value);
  }
}

async function prevMatch() {
  if (searchMatches.value.length === 0) return;
  currentMatchIndex.value = (currentMatchIndex.value - 1 + searchMatches.value.length) % searchMatches.value.length;
  
  if (isSearchViewMode.value) {
    await loadSearchContext(currentMatchIndex.value);
  } else {
    scrollToMatch(currentMatchIndex.value);
  }
}

function scrollToMatch(index: number) {
  if (searchMatches.value.length === 0) return;
  const messageId = searchMatches.value[index];
  const element = document.querySelector(`[data-message-id="${messageId}"]`);
  if (element) {
    element.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }
}

function isCurrentMatch(messageId: number): boolean {
  if (!isSearchActive.value || searchMatches.value.length === 0) return false;
  return searchMatches.value[currentMatchIndex.value] === messageId;
}

function isMatch(messageId: number): boolean {
  return searchMatches.value.includes(messageId);
}

function toggleFilters() {
  showFilters.value = !showFilters.value;
}

// Handle scroll event - now mostly unused since we load everything in background
// Lazy load more messages when scrolling to top
function handleScroll() {
  // Don't trigger lazy loading when in search view mode
  if (isSearchViewMode.value) return;
  
  if (!messagesContainer.value || olderLoading.value || fullyLoaded.value) return;
  
  // Check if user scrolled near the top (within 100px)
  const scrollTop = messagesContainer.value.scrollTop;
  if (scrollTop < 100 && hasMoreOlder.value) {
    loadMoreOlderMessages();
  }
}

async function loadMoreOlderMessages() {
  if (!selectedConversation.value || olderLoading.value || !hasMoreOlder.value) return;
  
  olderLoading.value = true;
  
  // Save current scroll position relative to a message
  const container = messagesContainer.value;
  const firstMessage = container?.querySelector('[data-message-id]');
  const firstMessageId = firstMessage?.getAttribute('data-message-id');
  const scrollOffsetBeforeLoad = firstMessage ? firstMessage.getBoundingClientRect().top : 0;
  
  try {
    currentPage.value++;
    const res = await getConversationMessages(
      selectedConversation.value.id,
      currentPage.value,
      200,
      'desc'
    );
    
    // Prepend older messages (they come newest-first from API, so reverse them)
    const olderMessages = res.content.reverse();
    messages.value = [...olderMessages, ...messages.value];
    hasMoreOlder.value = !res.last;
    
    // Restore scroll position to keep user at same message
    await nextTick();
    if (firstMessageId) {
      const messageElement = container?.querySelector(`[data-message-id="${firstMessageId}"]`);
      if (messageElement && container) {
        const newTop = messageElement.getBoundingClientRect().top;
        container.scrollTop += (newTop - scrollOffsetBeforeLoad);
      }
    }
  } catch (e) {
    console.error('Failed to load more messages', e);
    currentPage.value--; // Revert page increment on error
  } finally {
    olderLoading.value = false;
  }
}

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
  participantColorMap.value.clear(); // Clear color assignments for new conversation
  router.push({ name: 'messages', params: { id: conversation.id } });
  await loadMessages();
}

function clearConversation() {
  selectedConversation.value = null;
  messages.value = [];
  fullyLoaded.value = false;
  loadingInBackground.value = false;
  isSearchActive.value = false;
  participantColorMap.value.clear(); // Clear color assignments
  router.push({ name: 'messages' });
}

// Load messages with hybrid approach
async function loadMessages() {
  if (!selectedConversation.value) return;
  messagesLoading.value = true;
  currentPage.value = 0;
  fullyLoaded.value = false;

  try {
    // Step 1: Get total count
    totalMessages.value = await getConversationMessageCount(selectedConversation.value.id);

    // Step 2: Load first 200 messages for quick display
    const res = await getConversationMessages(
      selectedConversation.value.id,
      currentPage.value,
      200, // Load first 200
      'desc'
    );
    messages.value = res.content.reverse(); // Show chronological order (oldest first in view)
    hasMoreOlder.value = !res.last;

    messagesLoading.value = false;

    // Scroll to bottom after initial messages render
    await nextTick();
    requestAnimationFrame(() => {
      if (messagesContainer.value) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
      }
    });

    // Step 3: Start loading ALL messages in background (cached on backend)
    // Only auto-load all messages if conversation has fewer than 5000 messages
    // For larger conversations, user can manually click "Load All" if needed
    if (totalMessages.value > 200 && totalMessages.value < 5000) {
      loadAllMessagesInBackground();
    } else if (totalMessages.value <= 200) {
      // Small conversations (≤200) are fully loaded on first fetch
      fullyLoaded.value = true;
      rebuildReactionIndex();
    }
    // For large conversations (≥5000), fullyLoaded stays false
    // User can lazy load or click "Load All" button
  } catch (e) {
    console.error('Failed to load messages', e);
    messagesLoading.value = false;
  }
}

// Load all messages in background (backend cached)
async function loadAllMessagesInBackground() {
  if (!selectedConversation.value) return;

  loadingInBackground.value = true;

  try {
    // This endpoint is cached in Caffeine on backend
    const allMessages = await getAllConversationMessages(selectedConversation.value.id);

    // Replace with full dataset
    messages.value = allMessages;
    fullyLoaded.value = true;
    hasMoreOlder.value = false; // No more to load
    
    // Rebuild reaction index after loading all messages
    rebuildReactionIndex();

    console.log(`Loaded all ${allMessages.length} messages for conversation ${selectedConversation.value.id}`);
  } catch (e) {
    console.error('Failed to load all messages', e);
  } finally {
    loadingInBackground.value = false;
  }
}


// Actions menu
function toggleActionsMenu(conversationId: number) {
  showActionsMenu.value = showActionsMenu.value === conversationId ? null : conversationId;
}

// Close menu when clicking outside
function handleClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement;
  if (!target.closest('.relative')) {
    showActionsMenu.value = null;
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside);
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
});

// Rename conversation
function openRenameDialog(conversation: ConversationSummary) {
  conversationToRename.value = conversation;
  newConversationName.value = conversation.name;
  showRenameDialog.value = true;
}

async function submitRename() {
  if (!conversationToRename.value || !newConversationName.value.trim()) return;
  
  try {
    const updated = await renameConversation(conversationToRename.value.id, newConversationName.value.trim());
    
    // Update the conversation in the list
    const index = conversations.value.findIndex(c => c.id === updated.id);
    if (index !== -1) {
      conversations.value[index] = updated;
    }
    
    // Update selected conversation if it's the one being renamed
    if (selectedConversation.value?.id === updated.id) {
      selectedConversation.value = updated;
    }
    
    showRenameDialog.value = false;
    conversationToRename.value = null;
    newConversationName.value = '';
  } catch (error) {
    console.error('Failed to rename conversation', error);
    alert('Failed to rename conversation');
  }
}

function cancelRename() {
  showRenameDialog.value = false;
  conversationToRename.value = null;
  newConversationName.value = '';
}

// Delete conversation
async function confirmDelete(conversation: ConversationSummary) {
  if (!confirm(`Are you sure you want to delete the conversation with "${conversation.name}"? This will delete all messages in this conversation.`)) {
    return;
  }
  
  try {
    const result = await deleteConversation(conversation.id);
    
    if (result === 'deleted') {
      // Remove from list
      conversations.value = conversations.value.filter(c => c.id !== conversation.id);
      
      // Clear selected if it was the deleted one
      if (selectedConversation.value?.id === conversation.id) {
        clearConversation();
      }
    } else {
      alert('Conversation not found or already deleted');
    }
  } catch (error) {
    console.error('Failed to delete conversation', error);
    alert('Failed to delete conversation');
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
