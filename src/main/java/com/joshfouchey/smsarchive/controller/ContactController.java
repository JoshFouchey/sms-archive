package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.ContactDto;
import com.joshfouchey.smsarchive.service.ContactService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public List<ContactDto> getAllContacts() {
        return contactService.getAllDistinctContacts();
    }
}

