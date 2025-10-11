package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.AnalyticsDashboardDto;
import com.joshfouchey.smsarchive.dto.AnalyticsSummaryDto;
import com.joshfouchey.smsarchive.dto.MessageCountPerDayDto;
import com.joshfouchey.smsarchive.dto.TopContactDto;
import com.joshfouchey.smsarchive.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryDto summary() {
        return service.getSummary();
    }

    @GetMapping("/top-contacts")
    public List<TopContactDto> topContacts(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return service.getTopContacts(days, limit);
    }

    @GetMapping("/messages-per-day")
    public List<MessageCountPerDayDto> messagesPerDay(
            @RequestParam(defaultValue = "30") int days
    ) {
        return service.getMessagesPerDay(days);
    }

    @GetMapping("/total-contacts")
    public long totalContacts() {
        return service.getTotalContacts();
    }

    @GetMapping("/dashboard")
    public AnalyticsDashboardDto dashboard(
            @RequestParam(name = "topContactDays", defaultValue = "30") int topContactDays,
            @RequestParam(name = "topLimit", defaultValue = "10") int topLimit,
            @RequestParam(name = "perDayDays", defaultValue = "30") int perDayDays
    ) {
        return service.getDashboard(topContactDays, topLimit, perDayDays);
    }
}