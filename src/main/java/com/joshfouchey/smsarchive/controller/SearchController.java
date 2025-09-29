package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.model.Sms;
import com.joshfouchey.smsarchive.model.Mms;
import com.joshfouchey.smsarchive.service.SearchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    // --- SMS ---
    @GetMapping("/sms/address")
    public List<Sms> searchSmsByAddress(@RequestParam String address) {
        return searchService.searchSmsByAddress(address);
    }

    @GetMapping("/sms/text")
    public List<Sms> searchSmsByText(@RequestParam String text) {
        return searchService.searchSmsByText(text);
    }

    @GetMapping("/sms/dates")
    public List<Sms> searchSmsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return searchService.searchSmsByDateRange(start, end);
    }

    // --- MMS ---
    @GetMapping("/mms/address")
    public List<Mms> searchMmsByAddress(@RequestParam String address) {
        return searchService.searchMmsByAddress(address);
    }

    @GetMapping("/mms/text")
    public List<Mms> searchMmsByText(@RequestParam String text) {
        return searchService.searchMmsByText(text);
    }

    @GetMapping("/mms/dates")
    public List<Mms> searchMmsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return searchService.searchMmsByDateRange(start, end);
    }
}
