package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.dto.PagedResponse;
import com.joshfouchey.smsarchive.mapper.MessageMapper;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.service.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final MessageRepository repo;
    private final CurrentUserProvider currentUserProvider;

    public SearchController(MessageRepository repo, CurrentUserProvider currentUserProvider) {
        this.repo = repo;
        this.currentUserProvider = currentUserProvider;
    }


    // Search by body text with pagination and optional contact filter
    @GetMapping("/text")
    public PagedResponse<MessageDto> byText(
            @RequestParam String text,
            @RequestParam(required = false) Long contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        var user = currentUserProvider.getCurrentUser();
        // Use unsorted Pageable since our query already has ORDER BY clause
        Pageable pageable = PageRequest.of(page, size);
        Page<Message> results;
        
        if (contactId != null) {
            // Search with contact filter
            results = repo.searchByTextAndContactUser(text, contactId, user.getId(), pageable);
        } else {
            // Search all messages
            results = repo.searchByTextUserPaginated(text, user.getId(), pageable);
        }
        
        return new PagedResponse<>(
                results.getContent().stream().map(MessageMapper::toDto).toList(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.isFirst(),
                results.isLast()
        );
    }

    // Search by timestamp range (was "date")
    @GetMapping("/dates")
    public List<Message> byDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return repo.findByTimestampBetweenUser(start, end, currentUserProvider.getCurrentUser());
    }
}
