package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.ConversationSummaryDto;
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
            @RequestParam(defaultValue = "desc") String sort) {
        return conversationService.getConversationMessages(conversationId, page, size, sort);
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
}
