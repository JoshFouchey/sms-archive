package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.ConversationSummaryDto;
import com.joshfouchey.smsarchive.dto.ConversationTimelineDto;
import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.dto.PagedResponse;
import com.joshfouchey.smsarchive.service.ConversationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @DeleteMapping("/{conversationId}")
    public org.springframework.http.ResponseEntity<Void> deleteConversation(@PathVariable Long conversationId) {
        try {
            conversationService.deleteConversationById(conversationId);
            return org.springframework.http.ResponseEntity.noContent().build();
        } catch (RuntimeException ex) {
            // Conversation not found or not owned
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Load ALL messages for a conversation (cached).
     * Used for client-side search/filter operations.
     */
    @GetMapping("/{conversationId}/messages/all")
    public List<MessageDto> getAllConversationMessages(@PathVariable Long conversationId) {
        return conversationService.getAllConversationMessages(conversationId);
    }

    /**
     * Get total message count for a conversation (cached).
     */
    @GetMapping("/{conversationId}/messages/count")
    public java.util.Map<String, Long> getConversationMessageCount(@PathVariable Long conversationId) {
        Long count = conversationService.getConversationMessageCount(conversationId);
        return java.util.Map.of("count", count);
    }
}
