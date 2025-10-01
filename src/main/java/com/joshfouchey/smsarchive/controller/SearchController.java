package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.MessageDto;
import com.joshfouchey.smsarchive.mapper.MessageMapper;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final MessageRepository repo;

    public SearchController(MessageRepository repo) {
        this.repo = repo;
    }

    // Search by sender (was "address")
    @GetMapping("/sender")
    public List<Message> bySender(@RequestParam String sender) {
        return repo.findBySenderContainingIgnoreCase(sender);
    }

    // Search by recipient (new field we mapped in Message.java)
    @GetMapping("/recipient")
    public List<Message> byRecipient(@RequestParam String recipient) {
        return repo.findByRecipientContainingIgnoreCase(recipient);
    }

    // Search by body text
    @GetMapping("/text")
    public List<MessageDto> byText(@RequestParam String text) {
        return repo.searchByText(text)
                .stream()
                .map(MessageMapper::toDto)
                .toList();    }

    // Search by timestamp range (was "date")
    @GetMapping("/dates")
    public List<Message> byDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return repo.findByTimestampBetween(start, end);
    }
}
