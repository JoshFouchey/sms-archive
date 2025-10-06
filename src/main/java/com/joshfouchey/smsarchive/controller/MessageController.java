package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
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

    // Returns distinct contacts with preview info
    @GetMapping("/contacts")
    public List<ContactSummaryDto> getAllContacts() {
        return messageService.getAllContactSummaries();
    }

    // (Later) You’ll add a /conversation endpoint here
    // @GetMapping("/conversation/{contactName}")
    // public List<MessageDto> getMessagesByContact(@PathVariable String contactName, @RequestParam int page, @RequestParam int size) {
    //     return messagesService.getMessagesByContact(contactName, page, size);
    // }
}
