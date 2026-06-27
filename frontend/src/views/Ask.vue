<template>
  <div class="h-full flex flex-col">
    <!-- Header with search bar -->
    <div class="shrink-0 px-4 pt-4 pb-3 sm:px-6 sm:pt-6 sm:pb-4">
      <div class="max-w-3xl mx-auto text-center mb-4">
        <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-gray-100 mb-1 flex items-center justify-center gap-2">
          <i class="pi pi-sparkles text-amber-500"></i>
          Ask
        </h1>
      </div>

      <!-- Mode Tabs -->
      <div class="max-w-3xl mx-auto mb-4">
        <div class="flex items-center justify-center gap-1 p-1 bg-gray-100 dark:bg-gray-800 rounded-xl w-fit mx-auto">
          <button
            @click="switchMode('SEARCH')"
            :class="[
              'flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all',
              mode === 'SEARCH'
                ? 'bg-white dark:bg-gray-700 text-emerald-600 dark:text-emerald-400 shadow-sm'
                : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
            ]"
          >
            <i class="pi pi-search text-xs"></i>
            <span class="hidden sm:inline">Search</span>
          </button>
          <button
            @click="switchMode('DATA')"
            :class="[
              'flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all',
              mode === 'DATA'
                ? 'bg-white dark:bg-gray-700 text-blue-600 dark:text-blue-400 shadow-sm'
                : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
            ]"
          >
            <i class="pi pi-database text-xs"></i>
            <span class="hidden sm:inline">Data Query</span>
          </button>
        </div>
        <p class="text-xs text-gray-400 dark:text-gray-500 text-center mt-2">
          {{ mode === 'SEARCH' ? 'Find messages by meaning — great for conversations, topics, and agreements' : 'Ask data questions — the AI writes SQL to query your archive' }}
        </p>
        <div v-if="mode === 'DATA'" class="flex justify-center mt-2">
          <button
            @click="showSchema = !showSchema"
            class="text-xs text-blue-500 dark:text-blue-400 hover:text-blue-600 dark:hover:text-blue-300 font-medium"
          >
            <i class="pi pi-info-circle mr-1"></i>{{ showSchema ? 'Hide schema' : 'Show schema cheat-sheet' }}
          </button>
        </div>
      </div>

      <!-- Search Bar -->
      <div class="max-w-3xl mx-auto">
        <div class="relative">
          <i :class="[
            'absolute left-4 top-1/2 -translate-y-1/2 text-lg',
            mode === 'SEARCH' ? 'pi pi-search text-emerald-400' : 'pi pi-database text-blue-400'
          ]"></i>
          <input
            ref="searchInput"
            v-model="query"
            type="text"
            :placeholder="mode === 'SEARCH'
              ? 'That argument about the wedding · Vacation plans with wife · Bob agreed to pay me back...'
              : 'How many texts in 2024? · Who did I message most last month? · First text to Sarah...'"
            :class="[
              'w-full pl-12 pr-14 py-4 text-lg rounded-2xl border-2 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-4 shadow-lg transition-all',
              mode === 'SEARCH'
                ? 'border-gray-200 dark:border-gray-600 focus:border-emerald-500 dark:focus:border-emerald-400 focus:ring-emerald-500/10'
                : 'border-gray-200 dark:border-gray-600 focus:border-blue-500 dark:focus:border-blue-400 focus:ring-blue-500/10'
            ]"
            @keyup.enter="submitQuestion"
          />
          <button
            @click="submitQuestion"
            :disabled="!query.trim() || loading"
            :class="[
              'absolute right-2 top-1/2 -translate-y-1/2 p-2.5 rounded-xl text-white transition-all active:scale-95',
              mode === 'SEARCH'
                ? 'bg-emerald-500 hover:bg-emerald-600 disabled:bg-gray-300 dark:disabled:bg-gray-600'
                : 'bg-blue-500 hover:bg-blue-600 disabled:bg-gray-300 dark:disabled:bg-gray-600'
            ]"
          >
            <i :class="loading ? 'pi pi-spin pi-spinner' : 'pi pi-arrow-right'" class="text-lg"></i>
          </button>
        </div>

        <!-- History button -->
        <div v-if="queryHistory.length > 0" class="flex justify-center mt-2">
          <button
            @click="showHistory = true"
            class="text-xs text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 font-medium flex items-center gap-1 transition-colors"
          >
            <i class="pi pi-history"></i> {{ queryHistory.length }} recent {{ queryHistory.length === 1 ? 'query' : 'queries' }}
          </button>
        </div>

        <!-- Suggestion Chips -->
        <div v-if="!response && !loading" class="flex flex-wrap gap-2 mt-3 justify-center">
          <button
            v-for="chip in activeSuggestions"
            :key="chip"
            @click="query = chip; submitQuestion()"
            :class="[
              'px-3 py-1.5 rounded-full text-sm transition-all border',
              mode === 'SEARCH'
                ? 'bg-gray-100 dark:bg-gray-700 hover:bg-emerald-100 dark:hover:bg-emerald-900/30 text-gray-600 dark:text-gray-300 hover:text-emerald-700 dark:hover:text-emerald-300 border-gray-200 dark:border-gray-600 hover:border-emerald-300'
                : 'bg-gray-100 dark:bg-gray-700 hover:bg-blue-100 dark:hover:bg-blue-900/30 text-gray-600 dark:text-gray-300 hover:text-blue-700 dark:hover:text-blue-300 border-gray-200 dark:border-gray-600 hover:border-blue-300'
            ]"
          >
            {{ chip }}
          </button>
        </div>

        <!-- Schema Cheat-Sheet -->
        <div v-if="mode === 'DATA' && showSchema" class="mt-3 rounded-xl border border-blue-100 dark:border-blue-900 bg-blue-50/70 dark:bg-blue-900/20 p-3 text-left">
          <div class="grid sm:grid-cols-2 gap-2">
            <div v-for="table in schemaTables" :key="table.name" class="rounded-lg bg-white/70 dark:bg-gray-800/70 p-2">
              <p class="text-xs font-semibold text-blue-600 dark:text-blue-300 mb-1">{{ table.name }}</p>
              <p class="text-[11px] text-gray-500 dark:text-gray-400 leading-relaxed">{{ table.columns.join(', ') }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Results Area -->
    <div class="flex-1 overflow-y-auto px-4 pb-4 sm:px-6 sm:pb-6">
      <div class="max-w-3xl mx-auto space-y-4">

        <!-- Loading State -->
        <div v-if="loading" class="flex flex-col items-center py-12">
          <div class="relative">
            <div class="w-16 h-16 rounded-full bg-amber-100 dark:bg-amber-900/30 flex items-center justify-center animate-pulse">
              <i class="pi pi-sparkles text-2xl text-amber-500"></i>
            </div>
          </div>
          <p class="mt-4 text-sm text-gray-500 dark:text-gray-400 animate-pulse">Thinking...</p>
        </div>

        <!-- Error State -->
        <div v-if="error" class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl p-4">
          <p class="text-sm text-red-600 dark:text-red-400"><i class="pi pi-exclamation-circle mr-1"></i> {{ error }}</p>
        </div>

        <!-- AI Answer Panel (FACTUAL & ANALYTICS) -->
        <div v-if="response && response.answer" class="bg-gradient-to-br from-amber-50 to-orange-50 dark:from-amber-900/20 dark:to-orange-900/20 border border-amber-200 dark:border-amber-800 rounded-2xl p-5 shadow-md">
          <div class="flex items-start gap-3">
            <div class="w-8 h-8 rounded-full bg-amber-500 flex items-center justify-center shrink-0">
              <i class="pi pi-sparkles text-white text-sm"></i>
            </div>
            <div class="flex-1 min-w-0">
              <div class="prose prose-sm dark:prose-invert max-w-none text-gray-800 dark:text-gray-200" v-html="renderMarkdown(response.answer)"></div>
              <div class="flex items-center gap-3 mt-3 text-xs text-gray-400">
                <span class="flex items-center gap-1">
                  <i class="pi pi-tag"></i>
                  {{ response.intent }}
                </span>
                <span class="flex items-center gap-1">
                  <i class="pi pi-clock"></i>
                  {{ (response.processingTimeMs / 1000).toFixed(1) }}s
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Source Messages -->
        <div v-if="response && response.sources.length > 0">
          <h3 class="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-2 flex items-center gap-2">
            <i class="pi pi-comments text-xs"></i>
            Source Messages
          </h3>
          <div class="space-y-2">
            <div v-for="src in response.sources" :key="src.messageId"
              class="p-3 rounded-xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 hover:border-amber-300 dark:hover:border-amber-700 transition-colors cursor-pointer"
              @click="goToMessage(src.messageId, src.contactName)">
              <div class="flex items-center gap-2 mb-1">
                <span class="text-xs font-semibold text-blue-600 dark:text-blue-400">{{ src.contactName }}</span>
                <span class="text-[10px] text-gray-400">{{ formatDate(src.timestamp) }}</span>
                <span class="ml-auto text-[10px] text-gray-400">{{ (src.relevance * 100).toFixed(0) }}% match</span>
              </div>
              <p class="text-sm text-gray-700 dark:text-gray-300 line-clamp-2">{{ src.body }}</p>
            </div>
          </div>
        </div>

        <!-- Analytics Data (charts/tables) -->
        <div v-if="response && response.intent === 'ANALYTICS' && response.analyticsData">

          <!-- SQL Error (from text-to-SQL) -->
          <div v-if="sqlData?.type === 'sql_error'" class="mt-2 space-y-3">
            <div class="rounded-xl border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20 p-4">
              <div class="flex items-start gap-2">
                <i class="pi pi-exclamation-triangle text-red-500 mt-0.5"></i>
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-semibold text-red-700 dark:text-red-300">SQL query failed</p>
                  <p class="text-sm text-red-600 dark:text-red-400 break-words">{{ sqlData.dbError || sqlData.error }}</p>
                </div>
              </div>
              <div class="flex flex-wrap gap-2 mt-3">
                <button
                  @click="regenerateQuestion"
                  :disabled="loading || !query.trim()"
                  class="px-2.5 py-1.5 rounded-lg text-xs bg-red-100 dark:bg-red-900/40 text-red-700 dark:text-red-300 hover:bg-red-200 dark:hover:bg-red-900/60 disabled:opacity-50"
                >
                  <i class="pi pi-refresh mr-1"></i>Regenerate
                </button>
                <button
                  v-if="sqlForDisplay"
                  @click="startSqlEdit"
                  class="px-2.5 py-1.5 rounded-lg text-xs bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 border border-red-200 dark:border-red-800"
                >
                  <i class="pi pi-pencil mr-1"></i>Edit SQL
                </button>
              </div>
            </div>
            <details v-if="sqlForDisplay" class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden">
              <summary class="cursor-pointer px-4 py-2.5 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                Generated SQL
              </summary>
              <div class="border-t border-gray-100 dark:border-gray-700">
                <div v-if="editingSql" class="p-3 space-y-2">
                  <textarea v-model="sqlDraft" class="w-full min-h-40 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 p-3 text-xs font-mono text-gray-800 dark:text-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500/30"></textarea>
                  <div class="flex gap-2 justify-end">
                    <button @click="cancelSqlEdit" class="px-2.5 py-1.5 rounded-lg text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300">Cancel</button>
                    <button @click="runEditedSql" :disabled="sqlRunning || !sqlDraft.trim()" class="px-2.5 py-1.5 rounded-lg text-xs bg-blue-500 text-white hover:bg-blue-600 disabled:bg-gray-300 dark:disabled:bg-gray-600">
                      <i :class="sqlRunning ? 'pi pi-spin pi-spinner' : 'pi pi-play'" class="mr-1"></i>Run SQL
                    </button>
                  </div>
                </div>
                <div v-else class="relative">
                  <div class="absolute top-2 right-2 flex gap-1">
                    <button @click="startSqlEdit" class="px-2 py-1 rounded-md text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600">
                      <i class="pi pi-pencil mr-1"></i>Edit
                    </button>
                    <button @click="copySql" class="px-2 py-1 rounded-md text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600">
                      <i class="pi pi-copy mr-1"></i>Copy
                    </button>
                  </div>
                  <pre class="p-4 pr-32 text-xs overflow-x-auto text-gray-700 dark:text-gray-300"><code>{{ sqlForDisplay }}</code></pre>
                </div>
              </div>
            </details>
          </div>

          <!-- SQL Result Table (from text-to-SQL) -->
          <div v-if="sqlData?.type === 'sql_result'" class="mt-2 space-y-3">
            <div class="flex flex-wrap items-center gap-2 text-xs text-gray-400 dark:text-gray-500">
              <span class="font-mono">{{ sqlData.rowCount ?? sortedSqlRows.length }} rows</span>
              <span v-if="typeof sqlData.generationMs === 'number'" class="font-mono">AI {{ sqlData.generationMs }}ms</span>
              <span v-if="typeof sqlData.executionMs === 'number'" class="font-mono">DB {{ sqlData.executionMs }}ms</span>
              <button
                v-if="sqlData.suggestedChart && sortedSqlRows.length"
                @click="showChart = !showChart"
                :class="[
                  'flex items-center gap-1 px-2.5 py-1.5 rounded-lg transition-colors',
                  showChart
                    ? 'bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300'
                    : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'
                ]"
              >
                <i :class="showChart ? 'pi pi-table' : 'pi pi-chart-bar'"></i>
                {{ showChart ? 'Table' : 'Chart' }}
              </button>
              <button
                v-if="sortedSqlRows.length"
                @click="downloadCsv"
                class="ml-auto px-2.5 py-1.5 rounded-lg bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors"
              >
                <i class="pi pi-download mr-1"></i>CSV
              </button>
            </div>

            <!-- Chart view -->
            <div v-if="showChart && chartData" class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
              <SqlChart :type="sqlData?.suggestedChart?.type" :data="chartData" :options="chartOptions" />
            </div>

            <details class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden">
              <summary class="cursor-pointer px-4 py-2.5 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                SQL details
              </summary>
              <div class="border-t border-gray-100 dark:border-gray-700">
                <div v-if="editingSql" class="p-3 space-y-2">
                  <textarea v-model="sqlDraft" class="w-full min-h-40 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 p-3 text-xs font-mono text-gray-800 dark:text-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500/30"></textarea>
                  <div class="flex gap-2 justify-end">
                    <button @click="cancelSqlEdit" class="px-2.5 py-1.5 rounded-lg text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300">Cancel</button>
                    <button @click="runEditedSql" :disabled="sqlRunning || !sqlDraft.trim()" class="px-2.5 py-1.5 rounded-lg text-xs bg-blue-500 text-white hover:bg-blue-600 disabled:bg-gray-300 dark:disabled:bg-gray-600">
                      <i :class="sqlRunning ? 'pi pi-spin pi-spinner' : 'pi pi-play'" class="mr-1"></i>Run SQL
                    </button>
                  </div>
                </div>
                <div v-else class="relative">
                  <div class="absolute top-2 right-2 flex gap-1">
                    <button @click="startSqlEdit" class="px-2 py-1 rounded-md text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600">
                      <i class="pi pi-pencil mr-1"></i>Edit
                    </button>
                    <button @click="copySql" class="px-2 py-1 rounded-md text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600">
                      <i class="pi pi-copy mr-1"></i>Copy
                    </button>
                  </div>
                  <pre class="p-4 pr-32 text-xs overflow-x-auto text-gray-700 dark:text-gray-300"><code>{{ sqlForDisplay }}</code></pre>
                </div>
              </div>
            </details>

            <div v-if="sortedSqlRows.length && !showChart" class="rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
              <div class="overflow-x-auto max-h-[60vh]">
              <table class="w-full text-sm min-w-[400px] table-fixed">
                <thead class="sticky top-0 z-10">
                  <tr class="bg-gray-50 dark:bg-gray-800">
                    <th v-for="col in sqlColumns" :key="col"
                      @click="sortByColumn(col)"
                      class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider cursor-pointer select-none hover:text-gray-700 dark:hover:text-gray-200">
                      <span class="inline-flex items-center gap-1">
                        {{ col.replace(/_/g, ' ') }}
                        <i v-if="sortKey === col" :class="sortDirection === 'asc' ? 'pi pi-sort-up-fill' : 'pi pi-sort-down-fill'" class="text-[10px]"></i>
                        <i v-else class="pi pi-sort-alt text-[10px] opacity-40"></i>
                      </span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, i) in sortedSqlRows" :key="i"
                    class="border-t border-gray-100 dark:border-gray-700 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors">
                    <td v-for="col in sqlColumns" :key="col"
                      class="px-4 py-2.5 text-gray-700 dark:text-gray-300 truncate"
                      :title="row[col] == null ? '' : String(row[col])">
                      {{ formatCell(row[col], col) }}
                    </td>
                  </tr>
                </tbody>
              </table>
              </div>
            </div>
            <div v-else class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4 text-sm text-gray-500 dark:text-gray-400">
              Query ran successfully and returned no rows.
            </div>
          </div>

          <!-- Top Contacts Table (legacy fast-path) -->
          <div v-else-if="Array.isArray(response.analyticsData)" class="mt-2">
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
              <div v-for="(item, i) in response.analyticsData" :key="i"
                class="flex items-center gap-3 px-4 py-3 border-b border-gray-100 dark:border-gray-700 last:border-0 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors">
                <span class="text-lg font-bold text-gray-300 dark:text-gray-600 w-6 text-right">{{ i + 1 }}</span>
                <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center text-white text-sm font-bold">
                  {{ (item.displayName || '?').charAt(0).toUpperCase() }}
                </div>
                <span class="flex-1 font-medium text-sm text-gray-800 dark:text-gray-200">{{ item.displayName }}</span>
                <span class="text-sm text-gray-500 font-mono">{{ Number(item.messageCount).toLocaleString() }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Search Results (SEARCH intent) -->
        <div v-if="response && response.searchResults && response.searchResults.hits.length > 0">
          <h3 v-if="response.intent === 'SEARCH'" class="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-2 flex items-center gap-2">
            <i class="pi pi-search text-xs"></i>
            {{ response.searchResults.totalHits }} results
            <span class="text-xs font-normal text-gray-400">({{ response.searchResults.mode }})</span>
            <span v-for="entry in searchSourceCounts" :key="entry[0]" class="text-[10px] px-1.5 py-0.5 rounded-full bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400 font-normal">
              {{ entry[0] }} {{ entry[1] }}
            </span>
            <span class="ml-auto text-xs font-normal text-gray-400">{{ (response.processingTimeMs / 1000).toFixed(1) }}s</span>
          </h3>
          <div class="space-y-2">
            <div
              v-for="hit in response.searchResults.hits"
              :key="hit.message.id"
              class="p-3 rounded-xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 hover:border-emerald-300 dark:hover:border-emerald-700 transition-colors cursor-pointer"
              @click="goToMessage(hit.message.id, hit.message.contactName || hit.message.conversationName || hit.message.contactNumber)"
            >
              <div class="flex items-center gap-2 mb-1">
                <span class="text-xs font-semibold text-blue-600 dark:text-blue-400">{{ hit.message.contactName || hit.message.conversationName || hit.message.contactNumber }}</span>
                <span :class="[
                  'text-[10px] px-1.5 py-0.5 rounded-full font-medium',
                  hit.source === 'BOTH' ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300' :
                  hit.source === 'SEMANTIC' ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300' :
                  'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                ]">{{ hit.source }}</span>
                <span v-if="hit.moreFromConversation > 0"
                  class="text-[10px] px-1.5 py-0.5 rounded-full font-medium bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                  +{{ hit.moreFromConversation }} more
                </span>
                <span class="text-[10px] text-gray-400">{{ formatDate(hit.message.timestamp) }}</span>
                <span class="ml-auto text-[10px] text-gray-400">{{ formatScore(hit.score) }}</span>
              </div>
              <p class="text-sm text-gray-700 dark:text-gray-300 line-clamp-2">{{ hit.message.body }}</p>
            </div>
          </div>
        </div>

        <!-- No Results -->
        <div v-if="response && !loading && !response.answer && (!response.searchResults || response.searchResults.hits.length === 0)"
          class="text-center py-8 text-gray-400 dark:text-gray-500">
          <i class="pi pi-inbox text-3xl mb-2 block"></i>
          <p class="text-sm">No results found. Try rephrasing your question.</p>
        </div>
      </div>
    </div>

    <!-- Message Context Modal -->
    <Teleport to="body">
      <div v-if="contextModal.open" class="fixed inset-0 z-50 flex items-center justify-center p-4" @click.self="closeContext">
        <div class="absolute inset-0 bg-black/50 backdrop-blur-sm" @click="closeContext"></div>
        <div class="relative w-full max-w-2xl max-h-[85vh] bg-gray-50 dark:bg-gray-900 rounded-2xl shadow-2xl flex flex-col overflow-hidden">
          <!-- Modal Header -->
          <div class="shrink-0 flex items-center justify-between px-5 py-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
            <div class="flex items-center gap-2">
              <i class="pi pi-comments text-emerald-500"></i>
              <span class="font-semibold text-sm text-gray-800 dark:text-gray-200">Conversation Context</span>
              <span v-if="contextModal.contactName" class="text-xs text-gray-400">— {{ contextModal.contactName }}</span>
              <span v-if="contextIsGroupChat" class="text-[10px] px-1.5 py-0.5 rounded-full bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400 font-medium">Group</span>
            </div>
            <div class="flex items-center gap-2">
              <button @click="openInMessages" class="text-xs text-emerald-600 hover:text-emerald-700 dark:text-emerald-400 font-medium">
                <i class="pi pi-external-link mr-1"></i>Open in Messages
              </button>
              <button @click="closeContext" class="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-400 hover:text-gray-600 transition-colors">
                <i class="pi pi-times text-sm"></i>
              </button>
            </div>
          </div>

          <!-- Loading -->
          <div v-if="contextModal.loading" class="flex-1 flex items-center justify-center py-12">
            <i class="pi pi-spin pi-spinner text-2xl text-emerald-500"></i>
          </div>

          <!-- Messages -->
          <div v-else ref="contextScroll" class="flex-1 overflow-y-auto px-4 py-3 space-y-1">
            <MessageBubble
              v-for="msg in contextModal.messages"
              :key="msg.id"
              :message="msg"
              :is-group-chat="contextIsGroupChat"
              :participant-color-map="contextParticipantColorMap"
              :highlight-class="msg.id === contextModal.centerId ? 'ring-4 ring-emerald-400 dark:ring-emerald-500 shadow-2xl scale-[1.02]' : ''"
            />
          </div>
        </div>
      </div>
    </Teleport>
    <!-- Query History Drawer -->
    <Teleport to="body">
      <div v-if="showHistory" class="fixed inset-0 z-50 flex justify-end" @click.self="showHistory = false">
        <div class="absolute inset-0 bg-black/30 backdrop-blur-sm" @click="showHistory = false"></div>
        <div class="relative w-full max-w-sm bg-white dark:bg-gray-900 h-full shadow-2xl flex flex-col overflow-hidden">
          <div class="shrink-0 flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
            <h2 class="font-semibold text-sm text-gray-800 dark:text-gray-200 flex items-center gap-2">
              <i class="pi pi-history text-gray-400"></i>
              Query History
            </h2>
            <div class="flex items-center gap-3">
              <button @click="clearHistory" class="text-xs text-red-400 hover:text-red-500 transition-colors">Clear all</button>
              <button @click="showHistory = false" class="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-400 hover:text-gray-600 transition-colors">
                <i class="pi pi-times text-sm"></i>
              </button>
            </div>
          </div>
          <div class="flex-1 overflow-y-auto">
            <div v-if="!queryHistory.length" class="text-center py-12 text-gray-400 dark:text-gray-500 text-sm">
              No history yet.
            </div>
            <button
              v-for="entry in queryHistory"
              :key="entry.id"
              @click="restoreHistory(entry)"
              class="w-full text-left px-4 py-3 border-b border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
            >
              <div class="flex items-center gap-2 mb-1">
                <span :class="[
                  'text-[10px] px-1.5 py-0.5 rounded-full font-medium shrink-0',
                  entry.mode === 'SEARCH'
                    ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300'
                    : 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                ]">{{ entry.mode === 'SEARCH' ? 'Search' : 'Data' }}</span>
                <span class="text-[10px] text-gray-400">{{ historyTimeAgo(entry.timestamp) }}</span>
                <span v-if="historyStats(entry)" class="ml-auto text-[10px] text-gray-400 font-mono shrink-0">{{ historyStats(entry) }}</span>
              </div>
              <p class="text-sm text-gray-700 dark:text-gray-300 line-clamp-2 text-left">{{ entry.question }}</p>
            </button>
          </div>
        </div>
      </div>
    </Teleport>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, defineAsyncComponent } from 'vue';
import { useRouter } from 'vue-router';
import { marked } from 'marked';
import { askQuestion, getMessageContext, runSql, type QaResponse, type Message } from '../services/api';
import MessageBubble from '../components/MessageBubble.vue';

// chart.js is heavy and only needed when the user toggles the chart view, so load it
// lazily as a separate chunk instead of in this default-route ('/') bundle.
const SqlChart = defineAsyncComponent(() => import('../components/SqlChart.vue'));

const router = useRouter();
const searchInput = ref<HTMLInputElement | null>(null);
const query = ref('');
const loading = ref(false);
const error = ref('');
const response = ref<QaResponse | null>(null);
const mode = ref<'SEARCH' | 'DATA'>('SEARCH');
const sortKey = ref<string | null>(null);
const sortDirection = ref<'asc' | 'desc'>('asc');
const showSchema = ref(false);
const editingSql = ref(false);
const sqlDraft = ref('');
const sqlRunning = ref(false);
const showHistory = ref(false);

interface HistoryEntry {
  id: string;
  question: string;
  mode: 'SEARCH' | 'DATA';
  timestamp: number;
  response: QaResponse;
}

const HISTORY_KEY = 'sms-ask-history';
const MAX_HISTORY = 25;
const queryHistory = ref<HistoryEntry[]>([]);

function saveHistory() {
  try { localStorage.setItem(HISTORY_KEY, JSON.stringify(queryHistory.value)); } catch { /* ignore */ }
}

function addToHistory(question: string, entryMode: 'SEARCH' | 'DATA', resp: QaResponse) {
  queryHistory.value = queryHistory.value.filter(e => !(e.question === question && e.mode === entryMode));
  queryHistory.value.unshift({ id: String(Date.now()), question, mode: entryMode, timestamp: Date.now(), response: resp });
  if (queryHistory.value.length > MAX_HISTORY) queryHistory.value = queryHistory.value.slice(0, MAX_HISTORY);
  saveHistory();
}

function restoreHistory(entry: HistoryEntry) {
  query.value = entry.question;
  mode.value = entry.mode;
  response.value = entry.response;
  error.value = '';
  sortKey.value = null;
  editingSql.value = false;
  showHistory.value = false;
}

function clearHistory() {
  queryHistory.value = [];
  localStorage.removeItem(HISTORY_KEY);
}

function historyTimeAgo(ts: number): string {
  const mins = Math.floor((Date.now() - ts) / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

function historyStats(entry: HistoryEntry): string {
  if (entry.mode === 'DATA' && entry.response.analyticsData) {
    const d = entry.response.analyticsData as any;
    if (d.type === 'sql_error') return 'error';
    if (d.rowCount != null) return `${d.rowCount} rows`;
  }
  if (entry.response.searchResults) return `${entry.response.searchResults.totalHits} hits`;
  return '';
}

interface SqlAnalyticsData {
  type: 'sql_result' | 'sql_error';
  sql?: string;
  generatedSql?: string;
  executedSql?: string;
  columns?: string[];
  rows?: Record<string, any>[];
  rowCount?: number;
  generationMs?: number;
  executionMs?: number;
  error?: string;
  dbError?: string;
  suggestedChart?: { type: 'bar' | 'line'; labelCol: string; valueCol: string } | null;
}

marked.setOptions({ breaks: true, gfm: true });

function renderMarkdown(text: string): string {
  if (!text) return '';
  return marked.parse(text) as string;
}

const searchSuggestions = [
  'That argument about the wedding',
  'When we discussed vacation plans',
  'The time Bob said he\'d pay me back',
  'Conversation about moving to a new house',
  'When Alice talked about her new job',
];

const dataSuggestions = [
  'How many texts did I send in 2024?',
  'Who do I text the most?',
  'When did I first message Sarah?',
  'Which month had the most messages?',
  'How many photos did I send?',
];

const schemaTables = [
  {
    name: 'messages',
    columns: ['id', 'user_id', 'sender_contact_id', 'conversation_id', 'timestamp', 'body', 'direction', 'protocol'],
  },
  {
    name: 'contacts',
    columns: ['id', 'user_id', 'number', 'normalized_number', 'name'],
  },
  {
    name: 'conversations',
    columns: ['id', 'user_id', 'name', 'last_message_at'],
  },
  {
    name: 'conversation_contacts',
    columns: ['conversation_id', 'contact_id'],
  },
  {
    name: 'message_parts',
    columns: ['id', 'message_id', 'ct', 'name', 'file_path', 'size_bytes'],
  },
];

const activeSuggestions = computed(() =>
  mode.value === 'SEARCH' ? searchSuggestions : dataSuggestions
);

function switchMode(newMode: 'SEARCH' | 'DATA') {
  mode.value = newMode;
  response.value = null;
  error.value = '';
  sortKey.value = null;
  editingSql.value = false;
  searchInput.value?.focus();
}

onMounted(() => {
  searchInput.value?.focus();
  try {
    const stored = localStorage.getItem(HISTORY_KEY);
    if (stored) queryHistory.value = JSON.parse(stored);
  } catch { /* ignore corrupt storage */ }
});

async function submitQuestion() {
  const q = query.value.trim();
  if (!q || loading.value) return;

  loading.value = true;
  error.value = '';
  response.value = null;

  try {
    response.value = await askQuestion({ question: q, mode: mode.value });
    sortKey.value = null;
    editingSql.value = false;
    showChart.value = false;
    addToHistory(q, mode.value, response.value);
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || 'Failed to get answer';
  } finally {
    loading.value = false;
  }
}

const contextScroll = ref<HTMLElement | null>(null);
const contextModal = ref<{
  open: boolean;
  loading: boolean;
  centerId: number | null;
  contactName: string;
  messages: Message[];
  conversationId: number | null;
}>({
  open: false,
  loading: false,
  centerId: null,
  contactName: '',
  messages: [],
  conversationId: null,
});

const contextIsGroupChat = computed(() => {
  const msgs = contextModal.value.messages;
  if (!msgs.length) return false;
  const uniqueSenders = new Set(
    msgs.filter(m => m.senderContactId).map(m => m.senderContactId)
  );
  return uniqueSenders.size > 1;
});

const contextParticipantColorMap = ref(new Map<string, string>());

async function goToMessage(messageId: number, contactName?: string) {
  contextParticipantColorMap.value = new Map<string, string>();
  contextModal.value = {
    open: true,
    loading: true,
    centerId: messageId,
    contactName: contactName || '',
    messages: [],
    conversationId: null,
  };

  try {
    const ctx = await getMessageContext(messageId, 50, 50);
    const allMessages = [...ctx.before, ctx.center, ...ctx.after];
    contextModal.value.messages = allMessages;
    contextModal.value.conversationId = ctx.conversationId;
    contextModal.value.loading = false;

    // Scroll to the highlighted message after render
    await nextTick();
    const el = contextScroll.value?.querySelector(`[data-message-id="${messageId}"]`);
    if (el) {
      el.scrollIntoView({ block: 'center' });
    }
  } catch (e) {
    console.error('Failed to load message context', e);
    contextModal.value.loading = false;
  }
}

function closeContext() {
  contextModal.value.open = false;
}

function openInMessages() {
  if (contextModal.value.centerId) {
    router.push({ path: '/messages', query: { messageId: String(contextModal.value.centerId) } });
  }
  closeContext();
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function formatScore(score: number): string {
  return score >= 1 ? score.toFixed(1) : (score * 100).toFixed(0) + '%';
}

function formatCell(val: any, col?: string): string {
  if (val == null) return '—';
  if (typeof val === 'number') {
    const lowerCol = (col || '').toLowerCase();
    // Don't add commas to year/month/day columns or small ID-like values
    if (/\b(year|month|day|hour|minute|week)\b/.test(lowerCol)) {
      return String(val);
    }
    return Number.isInteger(val) ? val.toLocaleString() : val.toFixed(2);
  }
  // Truncate long strings (e.g. message bodies)
  const str = String(val);
  return str.length > 100 ? str.substring(0, 100) + '…' : str;
}

const sqlData = computed<SqlAnalyticsData | null>(() => {
  const data = response.value?.analyticsData;
  if (!data || typeof data !== 'object' || Array.isArray(data)) return null;
  if (data.type !== 'sql_result' && data.type !== 'sql_error') return null;
  return data as SqlAnalyticsData;
});

const showChart = ref(false);

const chartData = computed(() => {
  const d = sqlData.value;
  if (!d?.suggestedChart || !d.rows?.length) return null;
  const { labelCol, valueCol } = d.suggestedChart;
  const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  return {
    labels: d.rows.map(r => String(r[labelCol] ?? '')),
    datasets: [{
      label: valueCol.replace(/_/g, ' '),
      data: d.rows.map(r => Number(r[valueCol] ?? 0)),
      backgroundColor: isDark ? 'rgba(96, 165, 250, 0.5)' : 'rgba(59, 130, 246, 0.5)',
      borderColor: isDark ? 'rgb(96, 165, 250)' : 'rgb(59, 130, 246)',
      borderWidth: 2,
      tension: 0.3,
    }],
  };
});

const chartOptions = {
  responsive: true,
  maintainAspectRatio: true,
  plugins: { legend: { display: false } },
  scales: { y: { beginAtZero: true } },
};

const sqlForDisplay = computed(() =>
  sqlData.value?.executedSql || sqlData.value?.sql || sqlData.value?.generatedSql || ''
);

const sqlColumns = computed(() => sqlData.value?.columns || []);

const searchSourceCounts = computed(() =>
  Object.entries(response.value?.searchResults?.diagnostics?.sourceCounts || {})
);

const sortedSqlRows = computed(() => {
  const rows = sqlData.value?.rows || [];
  const key = sortKey.value;
  if (!key) return rows;

  return [...rows].sort((a, b) => {
    const av = a[key];
    const bv = b[key];
    if (av == null && bv == null) return 0;
    if (av == null) return 1;
    if (bv == null) return -1;

    const an = typeof av === 'number' ? av : Number(av);
    const bn = typeof bv === 'number' ? bv : Number(bv);
    const bothNumeric = Number.isFinite(an) && Number.isFinite(bn);
    const comparison = bothNumeric
      ? an - bn
      : String(av).localeCompare(String(bv), undefined, { numeric: true, sensitivity: 'base' });

    return sortDirection.value === 'asc' ? comparison : -comparison;
  });
});

function sortByColumn(col: string) {
  if (sortKey.value === col) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortKey.value = col;
    sortDirection.value = 'asc';
  }
}

function startSqlEdit() {
  sqlDraft.value = sqlForDisplay.value;
  editingSql.value = true;
}

function cancelSqlEdit() {
  editingSql.value = false;
  sqlDraft.value = '';
}

async function runEditedSql() {
  const sql = sqlDraft.value.trim();
  if (!sql || sqlRunning.value) return;

  sqlRunning.value = true;
  error.value = '';
  try {
    response.value = await runSql(sql);
    sortKey.value = null;
    editingSql.value = false;
    showChart.value = false;
    addToHistory(sql, 'DATA', response.value);
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || 'Failed to run SQL';
  } finally {
    sqlRunning.value = false;
  }
}

function regenerateQuestion() {
  editingSql.value = false;
  submitQuestion();
}

async function copySql() {
  if (!sqlForDisplay.value) return;
  try {
    await navigator.clipboard.writeText(sqlForDisplay.value);
  } catch (e) {
    console.error('Failed to copy SQL', e);
  }
}

function csvEscape(value: any): string {
  if (value == null) return '';
  const str = String(value);
  return /[",\n\r]/.test(str) ? `"${str.replace(/"/g, '""')}"` : str;
}

function downloadCsv() {
  const data = sqlData.value;
  if (!data?.columns?.length) return;

  const lines = [
    data.columns.map(csvEscape).join(','),
    ...sortedSqlRows.value.map(row => data.columns!.map(col => csvEscape(row[col])).join(',')),
  ];
  const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'sms-archive-query.csv';
  link.click();
  URL.revokeObjectURL(url);
}
</script>
