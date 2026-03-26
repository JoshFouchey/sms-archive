package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.*;
import com.joshfouchey.smsarchive.model.KgEntity;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.KgEntityRepository;
import com.joshfouchey.smsarchive.service.TextToSqlService.TextToSqlException;
import com.joshfouchey.smsarchive.service.TextToSqlService.TextToSqlResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.joshfouchey.smsarchive.service.UnifiedSearchService.SearchMode;

@Service
@ConditionalOnProperty(name = "smsarchive.ai.enabled", havingValue = "true", matchIfMissing = true)
public class QaService {

    private static final Logger log = LoggerFactory.getLogger(QaService.class);

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "need", "dare", "ought",
            "i", "me", "my", "mine", "we", "our", "ours", "you", "your", "yours",
            "he", "him", "his", "she", "her", "hers", "it", "its", "they", "them",
            "their", "theirs", "what", "which", "who", "whom", "whose", "that",
            "this", "these", "those", "am", "at", "by", "for", "from", "in",
            "into", "of", "on", "to", "with", "and", "but", "or", "nor", "not",
            "so", "very", "just", "about", "above", "after", "again", "all",
            "also", "any", "because", "before", "between", "both", "each",
            "few", "how", "if", "most", "no", "other", "over", "own", "same",
            "some", "such", "than", "then", "there", "through", "too", "under",
            "until", "up", "when", "where", "while", "why", "tell", "know",
            "many", "much", "often"
    );

    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "^(who|what|where|when|why|how|does|is|did|was|has|do|can|tell|which|whose)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Broader pattern to detect data/analytics questions that text-to-SQL can handle
    private static final Pattern DATA_QUESTION_PATTERN = Pattern.compile(
            "(how\\s+many|how\\s+often|count|total|average|first\\s+(text|message)|last\\s+(text|message)|" +
            "most\\s+(recent|common|frequent|active)|least|busiest|longest|earliest|latest|" +
            "per\\s+(day|week|month|year)|since\\s+\\d{4}|in\\s+\\d{4}|between|during|" +
            "statistics|frequency|percentage|ratio|sent\\s+to|received\\s+from|" +
            "\\d{4}|which\\s+(month|year|day|week)|when\\s+did\\s+i\\s+(first|last)|" +
            "compared|rank|breakdown)",
            Pattern.CASE_INSENSITIVE
    );

    // Detects date/time qualifiers that make regex fast-path insufficient
    private static final Pattern HAS_DATE_CONTEXT = Pattern.compile(
            "(in\\s+\\d{4}|since\\s+\\d{4}|\\d{4}|last\\s+(\\d+\\s+)?(month|year|week|day)|" +
            "this\\s+(month|year|week)|between|january|february|march|april|may|june|july|" +
            "august|september|october|november|december|today|yesterday|ago)",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<AnalyticsPattern> ANALYTICS_PATTERNS = List.of(
            new AnalyticsPattern(
                    Pattern.compile("(top|most)\\s+(text|message|contact|talk)", Pattern.CASE_INSENSITIVE),
                    "TOP_CONTACTS"),
            new AnalyticsPattern(
                    Pattern.compile("who\\s+do\\s+i\\s+(text|message|talk|chat)\\s+(the\\s+)?most", Pattern.CASE_INSENSITIVE),
                    "TOP_CONTACTS"),
            new AnalyticsPattern(
                    Pattern.compile("(messages?|texts?)\\s+(per|each|every)\\s+day", Pattern.CASE_INSENSITIVE),
                    "MESSAGES_PER_DAY"),
            new AnalyticsPattern(
                    Pattern.compile("how\\s+many\\s+(messages?|texts?)", Pattern.CASE_INSENSITIVE),
                    "TOTAL_MESSAGES"),
            new AnalyticsPattern(
                    Pattern.compile("(total|how\\s+many)\\s+contacts?", Pattern.CASE_INSENSITIVE),
                    "TOTAL_CONTACTS"),
            new AnalyticsPattern(
                    Pattern.compile("(total|how\\s+many)\\s+(images?|photos?|pictures?)", Pattern.CASE_INSENSITIVE),
                    "TOTAL_IMAGES")
    );

    private final ChatModel chatModel;
    private final KnowledgeGraphService knowledgeGraphService;
    private final UnifiedSearchService unifiedSearchService;
    private final AnalyticsService analyticsService;
    private final KgEntityRepository entityRepository;

    @Autowired(required = false)
    private TextToSqlService textToSqlService;

    @Value("${smsarchive.ai.kg.model:phi4-mini}")
    private String modelName;

    public QaService(ChatModel chatModel,
                     KnowledgeGraphService knowledgeGraphService,
                     UnifiedSearchService unifiedSearchService,
                     AnalyticsService analyticsService,
                     KgEntityRepository entityRepository) {
        this.chatModel = chatModel;
        this.knowledgeGraphService = knowledgeGraphService;
        this.unifiedSearchService = unifiedSearchService;
        this.analyticsService = analyticsService;
        this.entityRepository = entityRepository;
    }

    public QaResponse ask(User user, QaRequest request) {
        long start = System.currentTimeMillis();
        String question = request.question().trim();
        String mode = request.mode() != null ? request.mode().toUpperCase() : "AUTO";

        return switch (mode) {
            case "DATA" -> askData(user, question, request, start);
            case "AI" -> askAi(user, question, request, start);
            case "SEARCH" -> handleSearch(user, question, request, start);
            default -> askAuto(user, question, request, start);
        };
    }

    /** DATA mode: text-to-SQL → regex fast-path fallback */
    private QaResponse askData(User user, String question, QaRequest request, long start) {
        // Try text-to-SQL first (handles any data question)
        if (textToSqlService != null) {
            try {
                return handleTextToSql(user, question, start);
            } catch (TextToSqlException e) {
                log.info("Text-to-SQL failed, trying regex fast-path: {}", e.getMessage());

                // Fallback to regex patterns
                String analyticsType = detectAnalyticsIntent(question);
                if (analyticsType != null) {
                    return handleAnalytics(analyticsType, start);
                }

                // Return the actual error so user knows what happened
                String errorMsg = "SQL generation failed: " + e.getMessage()
                        + ". Try rephrasing your question.";
                return QaResponse.analytics(errorMsg, null, System.currentTimeMillis() - start);
            }
        }

        // No text-to-SQL service — regex only
        String analyticsType = detectAnalyticsIntent(question);
        if (analyticsType != null) {
            return handleAnalytics(analyticsType, start);
        }

        return QaResponse.analytics("Data query service is not available. Check that the SQL model is configured.", null,
                System.currentTimeMillis() - start);
    }

    /** AI mode: KG factual → semantic search */
    private QaResponse askAi(User user, String question, QaRequest request, long start) {
        // Try KG entity extraction
        if (QUESTION_PATTERN.matcher(question).find()) {
            List<KgEntity> matchedEntities = extractEntities(user, question);
            if (!matchedEntities.isEmpty()) {
                return handleFactual(user, question, matchedEntities, request, start);
            }
        }

        // Fall through to semantic/hybrid search
        return handleSearch(user, question, request, start);
    }

    /** AUTO mode: original routing — regex → KG factual → text-to-SQL → search */
    private QaResponse askAuto(User user, String question, QaRequest request, long start) {
        // 1. Regex fast-path (only if no date/filter context)
        if (!HAS_DATE_CONTEXT.matcher(question).find()) {
            String analyticsType = detectAnalyticsIntent(question);
            if (analyticsType != null) {
                return handleAnalytics(analyticsType, start);
            }
        }

        // 2. KG factual
        if (QUESTION_PATTERN.matcher(question).find()) {
            List<KgEntity> matchedEntities = extractEntities(user, question);
            if (!matchedEntities.isEmpty()) {
                return handleFactual(user, question, matchedEntities, request, start);
            }
        }

        // 3. Text-to-SQL for data questions
        if (textToSqlService != null && DATA_QUESTION_PATTERN.matcher(question).find()) {
            try {
                return handleTextToSql(user, question, start);
            } catch (TextToSqlException e) {
                log.info("Text-to-SQL failed in auto mode: {}", e.getMessage());
            }
        }

        // 4. Search fallback
        return handleSearch(user, question, request, start);
    }

    // --- Intent Detection ---

    private String detectAnalyticsIntent(String question) {
        for (AnalyticsPattern ap : ANALYTICS_PATTERNS) {
            if (ap.pattern.matcher(question).find()) {
                return ap.type;
            }
        }
        return null;
    }

    List<KgEntity> extractEntities(User user, String question) {
        String cleaned = question.replaceAll("[?!.,;:'\"]", "").trim();
        String[] words = cleaned.split("\\s+");

        Set<Long> seen = new HashSet<>();
        List<KgEntity> matched = new ArrayList<>();

        // Try bigrams first (e.g., "John Doe", "Ford Mustang")
        for (int i = 0; i < words.length - 1; i++) {
            String bigram = words[i] + " " + words[i + 1];
            if (isSignificant(words[i]) || isSignificant(words[i + 1])) {
                searchAndCollect(user, bigram, seen, matched);
            }
        }

        // Try trigrams (e.g., "John Michael Doe")
        for (int i = 0; i < words.length - 2; i++) {
            String trigram = words[i] + " " + words[i + 1] + " " + words[i + 2];
            if (isSignificant(words[i]) || isSignificant(words[i + 2])) {
                searchAndCollect(user, trigram, seen, matched);
            }
        }

        // Try single significant words
        for (String word : words) {
            if (isSignificant(word) && word.length() >= 3) {
                searchAndCollect(user, word, seen, matched);
            }
        }

        return matched;
    }

    private boolean isSignificant(String word) {
        return !STOP_WORDS.contains(word.toLowerCase());
    }

    private void searchAndCollect(User user, String term, Set<Long> seen, List<KgEntity> matched) {
        try {
            List<KgEntity> results = entityRepository.searchByNameOrAlias(user.getId(), term);
            for (KgEntity e : results) {
                if (seen.add(e.getId())) {
                    matched.add(e);
                }
            }
        } catch (Exception e) {
            log.debug("Entity search failed for '{}': {}", term, e.getMessage());
        }
    }

    // --- Pipeline Handlers ---

    private QaResponse handleAnalytics(String type, long elapsed) {
        return switch (type) {
            case "TOP_CONTACTS" -> {
                var topContacts = analyticsService.getTopContacts(0, 10);
                String answer = formatTopContacts(topContacts);
                yield QaResponse.analytics(answer, topContacts, System.currentTimeMillis() - elapsed);
            }
            case "MESSAGES_PER_DAY" -> {
                var summary = analyticsService.getSummary();
                var perDay = analyticsService.getMessagesPerDay(30);
                String answer = String.format("You have %,d total messages. Over the last 30 days, here's the daily breakdown.",
                        summary.totalMessages());
                yield QaResponse.analytics(answer, Map.of("summary", summary, "perDay", perDay),
                        System.currentTimeMillis() - elapsed);
            }
            case "TOTAL_MESSAGES" -> {
                var summary = analyticsService.getSummary();
                String answer = String.format("You have %,d total messages across all conversations.",
                        summary.totalMessages());
                yield QaResponse.analytics(answer, summary, System.currentTimeMillis() - elapsed);
            }
            case "TOTAL_CONTACTS" -> {
                long count = analyticsService.getTotalContacts();
                String answer = String.format("You have %,d contacts in your archive.", count);
                yield QaResponse.analytics(answer, Map.of("totalContacts", count),
                        System.currentTimeMillis() - elapsed);
            }
            case "TOTAL_IMAGES" -> {
                var summary = analyticsService.getSummary();
                String answer = String.format("You have %,d images in your message archive.",
                        summary.totalImages());
                yield QaResponse.analytics(answer, summary, System.currentTimeMillis() - elapsed);
            }
            default -> QaResponse.analytics("I couldn't process that analytics request.", null, 0);
        };
    }

    private QaResponse handleTextToSql(User user, String question, long startTime) {
        TextToSqlResult result = textToSqlService.generateAndExecute(question, user.getId());
        long elapsed = System.currentTimeMillis() - startTime;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "sql_result");
        data.put("sql", result.generatedSql());
        if (!result.rows().isEmpty()) {
            data.put("columns", new ArrayList<>(result.rows().get(0).keySet()));
        } else {
            data.put("columns", List.of());
        }
        data.put("rows", result.rows());
        data.put("rowCount", result.rows().size());

        return QaResponse.analytics(result.answer(), data, elapsed);
    }

    private QaResponse handleFactual(User user, String question, List<KgEntity> entities,
                                     QaRequest request, long startTime) {
        // Collect KG facts for matched entities
        List<KgTripleDto> allFacts = new ArrayList<>();
        for (KgEntity entity : entities) {
            var facts = knowledgeGraphService.getEntityFacts(user, entity.getId());
            allFacts.addAll(facts);
            if (allFacts.size() >= 20) break; // Cap context size
        }

        // Run semantic search for supporting messages
        UnifiedSearchResult searchResults = null;
        List<QaSource> sources = new ArrayList<>();
        try {
            searchResults = unifiedSearchService.search(
                    question, SearchMode.SEMANTIC, user.getId(),
                    request.conversationId(), request.contactId(), 5);

            sources = searchResults.hits().stream()
                    .map(hit -> new QaSource(
                            hit.message().id(),
                            truncate(hit.message().body(), 200),
                            hit.message().contactName(),
                            hit.message().timestamp(),
                            hit.score()))
                    .toList();
        } catch (Exception e) {
            log.warn("Semantic search failed during Q&A, continuing with KG only: {}", e.getMessage());
        }

        // If we have KG facts, synthesize with LLM
        if (!allFacts.isEmpty()) {
            try {
                String answer = synthesizeAnswer(question, allFacts, sources);
                long elapsed = System.currentTimeMillis() - startTime;
                return QaResponse.factual(answer, sources, allFacts, searchResults, elapsed);
            } catch (Exception e) {
                log.warn("LLM synthesis failed, returning raw facts: {}", e.getMessage());
                String fallback = formatFactsFallback(allFacts);
                long elapsed = System.currentTimeMillis() - startTime;
                return QaResponse.factual(fallback, sources, allFacts, searchResults, elapsed);
            }
        }

        // No facts found — fall through to search
        return handleSearch(user, question, request, startTime);
    }

    private QaResponse handleSearch(User user, String question, QaRequest request, long startTime) {
        var results = unifiedSearchService.search(
                question, SearchMode.AUTO, user.getId(),
                request.conversationId(), request.contactId(), 20);
        long elapsed = System.currentTimeMillis() - startTime;
        return QaResponse.search(results, elapsed);
    }

    // --- LLM Synthesis ---

    private String synthesizeAnswer(String question, List<KgTripleDto> facts, List<QaSource> sources) {
        StringBuilder context = new StringBuilder();

        context.append("Known facts from the knowledge graph:\n");
        for (KgTripleDto fact : facts.stream().limit(10).toList()) {
            String object = fact.objectName() != null ? fact.objectName() : fact.objectValue();
            context.append("- ").append(fact.subjectName())
                    .append(" ").append(fact.predicate().replace("_", " "))
                    .append(" ").append(object != null ? object : "unknown")
                    .append("\n");
        }

        if (!sources.isEmpty()) {
            context.append("\nRelevant messages:\n");
            for (QaSource src : sources.stream().limit(5).toList()) {
                context.append("- [").append(src.contactName()).append("] ")
                        .append(src.body()).append("\n");
            }
        }

        String prompt = String.format("""
                You are a concise assistant answering questions about a personal message archive.
                Use ONLY the facts and messages below. Never invent information.
                
                Rules:
                1. State what you know directly and confidently. Do NOT hedge or qualify.
                2. If a fact or message answers the question, say the answer plainly.
                3. Mention who said it or which conversation it came from when relevant.
                4. Keep it to 1-2 sentences. Shorter is better.
                5. If the context truly has no answer, say "I don't have enough information to answer that."
                
                %s
                
                Question: %s
                
                Answer:""",
                context, question);

        return callLlmWithRetry(prompt);
    }

    private String callLlmWithRetry(String prompt) {
        Exception lastException = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                ChatResponse response = chatModel.call(
                        new Prompt(prompt, OllamaOptions.builder()
                                .model(modelName)
                                .temperature(0.2)
                                .numPredict(150)
                                .build()));
                return response.getResult().getOutput().getText();
            } catch (Exception e) {
                lastException = e;
                if (attempt < 1) {
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                }
            }
        }
        throw new RuntimeException("LLM synthesis failed after 2 attempts", lastException);
    }

    // --- Formatting Helpers ---

    private String formatTopContacts(List<TopContactDto> contacts) {
        if (contacts.isEmpty()) return "No contact data available.";
        StringBuilder sb = new StringBuilder("Here are your most texted contacts:\n\n");
        for (int i = 0; i < contacts.size(); i++) {
            var c = contacts.get(i);
            sb.append(String.format("%d. **%s** — %,d messages\n", i + 1, c.displayName(), c.messageCount()));
        }
        return sb.toString();
    }

    private String formatFactsFallback(List<KgTripleDto> facts) {
        return facts.stream()
                .limit(10)
                .map(f -> {
                    String obj = f.objectName() != null ? f.objectName() : f.objectValue();
                    return f.subjectName() + " " + f.predicate().replace("_", " ") + " " + (obj != null ? obj : "");
                })
                .collect(Collectors.joining(". ", "Here's what I know: ", "."));
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    private record AnalyticsPattern(Pattern pattern, String type) {}
}
