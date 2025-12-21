package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.service.ConversationMaintenanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoints for conversation data maintenance and repair.
 */
@RestController
@RequestMapping("/api/admin/conversations")
public class ConversationMaintenanceController {

    private final ConversationMaintenanceService maintenanceService;

    public ConversationMaintenanceController(ConversationMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    /**
     * Diagnose conversation data integrity issues.
     * GET /api/admin/conversations/diagnose
     */
    @GetMapping("/diagnose")
    public ResponseEntity<Map<String, Object>> diagnose() {
        Map<String, Object> diagnosis = maintenanceService.diagnose();
        return ResponseEntity.ok(diagnosis);
    }

    /**
     * Fix conversations with NULL last_message_at.
     * POST /api/admin/conversations/fix/null-timestamps
     */
    @PostMapping("/fix/null-timestamps")
    public ResponseEntity<Map<String, Object>> fixNullTimestamps() {
        Map<String, Object> result = maintenanceService.fixNullLastMessageAt();
        return ResponseEntity.ok(result);
    }

    /**
     * Sync all conversation timestamps with actual message data.
     * POST /api/admin/conversations/fix/sync-timestamps
     */
    @PostMapping("/fix/sync-timestamps")
    public ResponseEntity<Map<String, Object>> syncTimestamps() {
        Map<String, Object> result = maintenanceService.syncAllLastMessageTimestamps();
        return ResponseEntity.ok(result);
    }

    /**
     * Rebuild missing conversation participants from messages.
     * POST /api/admin/conversations/fix/rebuild-participants
     */
    @PostMapping("/fix/rebuild-participants")
    public ResponseEntity<Map<String, Object>> rebuildParticipants() {
        Map<String, Object> result = maintenanceService.rebuildParticipants();
        return ResponseEntity.ok(result);
    }

    /**
     * Run all repair operations.
     * POST /api/admin/conversations/repair
     */
    @PostMapping("/repair")
    public ResponseEntity<Map<String, Object>> repairAll() {
        Map<String, Object> result = maintenanceService.repairAll();
        return ResponseEntity.ok(result);
    }
}
