package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
import com.joshfouchey.smsarchive.dto.MessageContextDto;
import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.dto.PagedResponse;
import com.joshfouchey.smsarchive.service.MessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
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

    @GetMapping("/{messageId}/context")
    public MessageContextDto getMessageContext(@PathVariable("messageId") Long messageId,
                                               @RequestParam(defaultValue = "25") int before,
                                               @RequestParam(defaultValue = "25") int after) {
        return messageService.getMessageContext(messageId, before, after);
    }
}
