package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
import com.joshfouchey.smsarchive.dto.ConversationSummaryDto;
import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.dto.PagedResponse;
import com.joshfouchey.smsarchive.service.MessageService;
import com.joshfouchey.smsarchive.service.ConversationMessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final ConversationMessageService conversationMessageService;

    public MessageController(MessageService messageService, ConversationMessageService conversationMessageService) {
        this.messageService = messageService;
        this.conversationMessageService = conversationMessageService;
    }

    @GetMapping("/contacts")
    public List<ContactSummaryDto> getAllContacts() {
        return messageService.getAllContactSummaries();
    }

    @GetMapping("/contact/{contactId}")
    public PagedResponse<MessageDto> getMessagesByContact(
            @PathVariable("contactId") Long contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "desc") String sort) {
        return messageService.getMessagesByContactId(contactId, page, size, sort);
    }

    @GetMapping("/conversations")
    public List<ConversationSummaryDto> getAllConversations() {
        return conversationMessageService.getAllConversationSummaries();
    }

    @GetMapping("/conversation/{conversationId}")
    public PagedResponse<MessageDto> getMessagesByConversation(
            @PathVariable("conversationId") Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "desc") String sort) {
        return conversationMessageService.getMessagesByConversationId(conversationId, page, size, sort);
    }
}
