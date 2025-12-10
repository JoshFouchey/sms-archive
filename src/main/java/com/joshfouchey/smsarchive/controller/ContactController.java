package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.ContactDto;
import com.joshfouchey.smsarchive.dto.ContactMergeResultDto;
import com.joshfouchey.smsarchive.service.ContactService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;

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

    // PUT /api/contacts/{id} to update a contact's name (null or blank clears name)
    @PutMapping("/{id}")
    public ResponseEntity<ContactDto> updateContactName(@PathVariable Long id, @RequestBody UpdateContactNameRequest request) {
        try {
            var updated = contactService.updateContactName(id, request.name());
            return ResponseEntity.ok(updated);
        } catch (java.util.NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{primaryId}/merge/{mergeFromId}")
    public ResponseEntity<ContactMergeResultDto> mergeContacts(
            @PathVariable Long primaryId,
            @PathVariable Long mergeFromId) {
        try {
            ContactMergeResultDto result = contactService.mergeContacts(primaryId, mergeFromId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(
                new ContactMergeResultDto(null, null, null, null, 0, 0, 0, false, ex.getMessage())
            );
        }
    }

    public record UpdateContactNameRequest(String name) {}
}
