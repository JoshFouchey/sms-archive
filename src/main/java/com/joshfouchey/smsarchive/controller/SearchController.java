package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.mapper.MessageMapper;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import com.joshfouchey.smsarchive.service.CurrentUserProvider;
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


    // Search by body text
    @GetMapping("/text")
    public List<MessageDto> byText(@RequestParam String text) {
        return repo.searchByTextUser(text, currentUserProvider.getCurrentUser())
                .stream()
                .map(MessageMapper::toDto)
                .toList();    }

    // Search by timestamp range (was "date")
    @GetMapping("/dates")
    public List<Message> byDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return repo.findByTimestampBetweenUser(start, end, currentUserProvider.getCurrentUser());
    }
}
