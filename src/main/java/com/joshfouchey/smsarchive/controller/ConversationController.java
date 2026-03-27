package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.ConversationSummaryDto;
import com.joshfouchey.smsarchive.dto.ConversationTimelineDto;
import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.dto.PagedResponse;
import com.joshfouchey.smsarchive.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.joshfouchey.smsarchive.util.InputLimits.*;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<ConversationSummaryDto> getAllConversations() {
        return conversationService.getAllConversations();
    }

    @GetMapping("/{conversationId}/messages")
    public PagedResponse<MessageDto> getConversationMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "desc") String sort,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {

        // If date range is provided, use date-range query
        if (dateFrom != null || dateTo != null) {
            return conversationService.getConversationMessagesByDateRange(
                    conversationId, dateFrom, dateTo, page, size, sort);
        }

        // Otherwise use standard pagination
        return conversationService.getConversationMessages(conversationId, page, size, sort);
    }

    @GetMapping("/{conversationId}/timeline")
    public ConversationTimelineDto getConversationTimeline(@PathVariable Long conversationId) {
        return conversationService.getConversationTimeline(conversationId);
    }

    record RenameRequest(String name) {}

    @PatchMapping("/{conversationId}/name")
    public ResponseEntity<ConversationSummaryDto> renameConversation(
            @PathVariable Long conversationId,
            @RequestBody RenameRequest body) {
        try {
            String newName = body.name();
            if (newName == null || newName.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            newName = truncate(newName.trim(), CONVERSATION_NAME_MAX);
            ConversationSummaryDto updated = conversationService.renameConversation(conversationId, newName);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable Long conversationId) {
        try {
            conversationService.deleteConversationById(conversationId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException ex) {
            // Conversation not found or not owned
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Search messages within a conversation.
     * Returns message IDs that match the search query (for navigation).
     */
    @GetMapping("/{conversationId}/messages/search")
    public Map<String, Object> searchConversationMessages(
            @PathVariable Long conversationId,
            @RequestParam String query) {
        String safeQuery = truncate(query, SEARCH_QUERY_MAX);
        return conversationService.searchWithinConversation(conversationId, safeQuery);
    }

    /**
     * Load ALL messages for a conversation (cached).
     * Used for client-side search/filter operations.
     * Returns lightweight DTOs to reduce JSON payload size.
     */
    @GetMapping("/{conversationId}/messages/all")
    public List<com.joshfouchey.smsarchive.dto.api.ConversationMessagesDto> getAllConversationMessages(@PathVariable Long conversationId) {
        return conversationService.getAllConversationMessages(conversationId);
    }

    /**
     * Get total message count for a conversation (cached).
     */
    @GetMapping("/{conversationId}/messages/count")
    public Map<String, Long> getConversationMessageCount(@PathVariable Long conversationId) {
        Long count = conversationService.getConversationMessageCount(conversationId);
        return Map.of("count", count);
    }
}
