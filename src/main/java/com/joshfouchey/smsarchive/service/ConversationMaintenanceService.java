package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.repository.ConversationRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for maintaining and fixing conversation data integrity issues.
 */
@Service
@Slf4j
public class ConversationMaintenanceService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ConversationMaintenanceService(ConversationRepository conversationRepository,
                                         MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Fix conversations with NULL last_message_at by calculating from actual messages.
     * This is the most common issue that causes conversations to not appear in the list.
     */
    @Transactional
    @CacheEvict(value = "conversationList", allEntries = true)
    public Map<String, Object> fixNullLastMessageAt() {
        log.info("Fixing conversations with NULL last_message_at...");
        
        String query = """
            UPDATE conversations c
            SET last_message_at = (
                SELECT MAX(m.timestamp) 
                FROM messages m 
                WHERE m.conversation_id = c.id
            )
            WHERE c.last_message_at IS NULL
            AND EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = c.id)
            """;
        
        int updated = entityManager.createNativeQuery(query).executeUpdate();
        
        log.info("Updated {} conversations with NULL last_message_at", updated);
        
        Map<String, Object> result = new HashMap<>();
        result.put("conversationsFixed", updated);
        result.put("issue", "NULL last_message_at");
        return result;
    }

    /**
     * Fix conversations where last_message_at is out of sync with actual messages.
     */
    @Transactional
    @CacheEvict(value = "conversationList", allEntries = true)
    public Map<String, Object> syncAllLastMessageTimestamps() {
        log.info("Syncing all conversation last_message_at timestamps...");
        
        List<Conversation> allConversations = conversationRepository.findAll();
        int updated = 0;
        
        for (Conversation conv : allConversations) {
            Message lastMessage = messageRepository.findLastMessageByConversation(conv.getId(), conv.getUser());
            
            if (lastMessage != null) {
                Instant actualLastTime = lastMessage.getTimestamp();
                if (!actualLastTime.equals(conv.getLastMessageAt())) {
                    conv.setLastMessageAt(actualLastTime);
                    conversationRepository.save(conv);
                    updated++;
                }
            }
        }
        
        log.info("Synced {} conversation timestamps", updated);
        
        Map<String, Object> result = new HashMap<>();
        result.put("conversationsChecked", allConversations.size());
        result.put("conversationsSynced", updated);
        return result;
    }

    /**
     * Find conversations with data integrity issues.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> diagnose() {
        log.info("Diagnosing conversation data integrity...");
        
        Map<String, Object> diagnosis = new HashMap<>();
        
        // Count conversations with NULL last_message_at but have messages
        String nullLastMessageQuery = """
            SELECT COUNT(*) FROM conversations c
            WHERE c.last_message_at IS NULL 
            AND EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = c.id)
            """;
        Long nullLastMessage = ((Number) entityManager.createNativeQuery(nullLastMessageQuery).getSingleResult()).longValue();
        diagnosis.put("nullLastMessageAt", nullLastMessage);
        
        // Count conversations with no participants
        String noParticipantsQuery = """
            SELECT COUNT(*) FROM conversations c
            WHERE NOT EXISTS (
                SELECT 1 FROM conversation_contacts cc WHERE cc.conversation_id = c.id
            )
            AND EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = c.id)
            """;
        Long noParticipants = ((Number) entityManager.createNativeQuery(noParticipantsQuery).getSingleResult()).longValue();
        diagnosis.put("noParticipants", noParticipants);
        
        // Count conversations with out-of-sync timestamps
        String outOfSyncQuery = """
            SELECT COUNT(*) FROM conversations c
            WHERE c.last_message_at IS NOT NULL
            AND c.last_message_at != (
                SELECT MAX(m.timestamp) FROM messages m WHERE m.conversation_id = c.id
            )
            AND EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = c.id)
            """;
        Long outOfSync = ((Number) entityManager.createNativeQuery(outOfSyncQuery).getSingleResult()).longValue();
        diagnosis.put("outOfSyncTimestamps", outOfSync);
        
        // Total conversations
        Long totalConversations = conversationRepository.count();
        diagnosis.put("totalConversations", totalConversations);
        
        // Calculate health score
        long issues = nullLastMessage + noParticipants + outOfSync;
        diagnosis.put("totalIssues", issues);
        diagnosis.put("healthScore", issues == 0 ? "HEALTHY" : issues < 5 ? "MINOR_ISSUES" : "NEEDS_ATTENTION");
        
        log.info("Diagnosis complete: {} total issues found", issues);
        
        return diagnosis;
    }

    /**
     * Rebuild participant relationships from message sender data.
     * This fixes conversations that have messages but no participants in conversation_contacts.
     */
    @Transactional
    @CacheEvict(value = {"conversationList", "contactSummaries"}, allEntries = true)
    public Map<String, Object> rebuildParticipants() {
        log.info("Rebuilding conversation participants from messages...");
        
        String rebuildQuery = """
            INSERT INTO conversation_contacts (conversation_id, contact_id)
            SELECT DISTINCT 
                m.conversation_id,
                m.sender_contact_id
            FROM messages m
            WHERE m.sender_contact_id IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM conversation_contacts cc 
                WHERE cc.conversation_id = m.conversation_id 
                AND cc.contact_id = m.sender_contact_id
            )
            AND NOT EXISTS (
                SELECT 1 FROM conversation_contacts cc 
                WHERE cc.conversation_id = m.conversation_id
            )
            ON CONFLICT DO NOTHING
            """;
        
        int added = entityManager.createNativeQuery(rebuildQuery).executeUpdate();
        
        log.info("Added {} participant relationships", added);
        
        Map<String, Object> result = new HashMap<>();
        result.put("participantsAdded", added);
        result.put("issue", "Missing conversation participants");
        return result;
    }

    /**
     * Run all fixes to repair conversation data.
     */
    @Transactional
    @CacheEvict(value = {"conversationList", "contactSummaries", "conversationMessages"}, allEntries = true)
    public Map<String, Object> repairAll() {
        log.info("Running complete conversation repair...");
        
        Map<String, Object> result = new HashMap<>();
        
        // Fix 1: NULL last_message_at
        Map<String, Object> fix1 = fixNullLastMessageAt();
        result.put("nullLastMessageFix", fix1);
        
        // Fix 2: Rebuild missing participants
        Map<String, Object> fix2 = rebuildParticipants();
        result.put("participantRebuild", fix2);
        
        // Fix 3: Out of sync timestamps
        Map<String, Object> fix3 = syncAllLastMessageTimestamps();
        result.put("timestampSync", fix3);
        
        // Run diagnosis after fixes
        Map<String, Object> finalDiagnosis = diagnose();
        result.put("finalDiagnosis", finalDiagnosis);
        
        log.info("Conversation repair complete");
        
        return result;
    }
}
