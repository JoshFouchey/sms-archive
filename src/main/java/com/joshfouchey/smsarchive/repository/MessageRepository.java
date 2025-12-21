// src/main/java/com/joshfouchey/smsarchive/repository/MessageRepository.java
package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByTimestampBetween(Instant start, Instant end);

    @Query(value = """
WITH latest_messages AS (
    SELECT DISTINCT ON (c.id)
        c.id AS contact_id,
        COALESCE(c.name, c.number) AS contact_name,
        m.timestamp AS last_message_timestamp,
        SUBSTRING(COALESCE(m.body, ''), 1, 200) AS last_message_preview,
        EXISTS (
            SELECT 1 FROM message_parts mp 
            WHERE mp.message_id = m.id 
            AND mp.ct LIKE 'image/%'
        ) AS has_image
    FROM messages m
    JOIN conversations conv ON m.conversation_id = conv.id
    JOIN conversation_contacts cc ON conv.id = cc.conversation_id
    JOIN contacts c ON cc.contact_id = c.id
    WHERE m.user_id = :userId
    ORDER BY c.id, m.timestamp DESC
)
SELECT 
    contact_id AS contactId,
    contact_name AS contactName,
    last_message_timestamp AS lastMessageTimestamp,
    last_message_preview AS lastMessagePreview,
    has_image AS hasImage
FROM latest_messages
ORDER BY last_message_timestamp DESC
""", nativeQuery = true)
    List<ContactSummaryProjection> findAllContactSummaries(@Param("userId") UUID userId);

    @Query("""
SELECT new com.joshfouchey.smsarchive.dto.TopContactDto(
    c.id, 
    COALESCE(c.name, c.number), 
    COUNT(m.id)
)
FROM Message m 
JOIN m.conversation conv
JOIN conv.participants c
WHERE m.timestamp >= :since 
  AND m.user = :user
GROUP BY c.id, c.name, c.number
ORDER BY COUNT(m.id) DESC
""")
    List<com.joshfouchey.smsarchive.dto.TopContactDto> findTopContactsSince(@Param("since") Instant since, @Param("user") com.joshfouchey.smsarchive.model.User user);

    @Query(value = """
SELECT date_trunc('day', m.timestamp) AS day_ts, COUNT(*) AS count
FROM messages m
WHERE m.timestamp >= :since AND m.user_id = :userId
GROUP BY day_ts
ORDER BY day_ts
""", nativeQuery = true)
    List<DayCountProjection> countMessagesPerDaySince(@Param("since") Instant since, @Param("userId") UUID userId);

    // Find messages where the conversation has a specific contact as participant
    @Query("select m from Message m join m.conversation conv join conv.participants c where c.id = :contactId and m.user = :user")
    Page<Message> findByContactIdAndUser(@Param("contactId") Long contactId, @Param("user") com.joshfouchey.smsarchive.model.User user, Pageable pageable);

    // Full-text search using PostgreSQL GIN index
    @Query(value = """
        SELECT m.* FROM messages m 
        WHERE m.user_id = :userId 
        AND to_tsvector('english', COALESCE(m.body, '')) @@ plainto_tsquery('english', :text)
        ORDER BY m.timestamp DESC
        """, nativeQuery = true)
    List<Message> searchByTextUser(@Param("text") String text, @Param("userId") UUID userId);

    // Paginated full-text search
    @Query(value = """
        SELECT m.* FROM messages m 
        WHERE m.user_id = :userId 
        AND to_tsvector('english', COALESCE(m.body, '')) @@ plainto_tsquery('english', :text)
        ORDER BY m.timestamp DESC
        """, 
        countQuery = """
        SELECT COUNT(*) FROM messages m 
        WHERE m.user_id = :userId 
        AND to_tsvector('english', COALESCE(m.body, '')) @@ plainto_tsquery('english', :text)
        """,
        nativeQuery = true)
    Page<Message> searchByTextUserPaginated(@Param("text") String text, @Param("userId") UUID userId, Pageable pageable);

    // Paginated full-text search with contact filter
    @Query(value = """
        SELECT m.* FROM messages m
        JOIN conversations conv ON m.conversation_id = conv.id
        JOIN conversation_contacts cc ON conv.id = cc.conversation_id
        WHERE m.user_id = :userId 
        AND cc.contact_id = :contactId
        AND to_tsvector('english', COALESCE(m.body, '')) @@ plainto_tsquery('english', :text)
        ORDER BY m.timestamp DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM messages m
        JOIN conversations conv ON m.conversation_id = conv.id
        JOIN conversation_contacts cc ON conv.id = cc.conversation_id
        WHERE m.user_id = :userId 
        AND cc.contact_id = :contactId
        AND to_tsvector('english', COALESCE(m.body, '')) @@ plainto_tsquery('english', :text)
        """,
        nativeQuery = true)
    Page<Message> searchByTextAndContactUser(@Param("text") String text, @Param("contactId") Long contactId, @Param("userId") UUID userId, Pageable pageable);

    // Search within a specific conversation
    @Query(value = """
        SELECT m.* FROM messages m 
        WHERE m.user_id = :userId 
        AND m.conversation_id = :conversationId
        AND to_tsvector('english', COALESCE(m.body, '')) @@ plainto_tsquery('english', :text)
        ORDER BY m.timestamp DESC
        """, nativeQuery = true)
    List<Message> searchWithinConversation(@Param("conversationId") Long conversationId, @Param("text") String text, @Param("userId") UUID userId);

    @Query("select m from Message m where m.user = :user and m.timestamp between :start and :end")
    List<Message> findByTimestampBetweenUser(@Param("start") Instant start, @Param("end") Instant end, @Param("user") com.joshfouchey.smsarchive.model.User user);



    long countByUser(com.joshfouchey.smsarchive.model.User user);

    @Query("select m from Message m where m.conversation.id = :conversationId and m.user = :user")
    Page<Message> findByConversationIdAndUser(@Param("conversationId") Long conversationId,
                                               @Param("user") com.joshfouchey.smsarchive.model.User user,
                                               Pageable pageable);

    @Query("select m from Message m where m.conversation.id = :conversationId and m.user = :user order by m.timestamp desc limit 1")
    Message findLastMessageByConversation(@Param("conversationId") Long conversationId,
                                          @Param("user") com.joshfouchey.smsarchive.model.User user);

    @Query("select m from Message m where m.conversation.id = :conversationId and m.user = :user order by m.timestamp asc")
    List<Message> findAllByConversationIdAndUser(@Param("conversationId") Long conversationId,
                                                 @Param("user") com.joshfouchey.smsarchive.model.User user,
                                                 Pageable pageable);

    @Query("select count(m) from Message m where m.conversation.id = :conversationId and m.user = :user")
    Long countByConversationIdAndUser(@Param("conversationId") Long conversationId,
                                      @Param("user") com.joshfouchey.smsarchive.model.User user);

    // Duplicate checking for group messages (with conversation and user)
    // Note: bodyNormalized is already lowercased and trimmed by the caller
    @Query("select (count(m) > 0) from Message m where m.user = :user and m.conversation = :conversation and m.timestamp = :ts and m.msgBox = :msgBox and m.protocol = :protocol and lower(trim(coalesce(m.body,''))) = :body")
    boolean existsByConversationAndTimestampAndBody(@Param("conversation") com.joshfouchey.smsarchive.model.Conversation conversation,
                                                     @Param("ts") Instant timestamp,
                                                     @Param("body") String bodyNormalized,
                                                     @Param("msgBox") Integer msgBox,
                                                     @Param("protocol") com.joshfouchey.smsarchive.model.MessageProtocol protocol,
                                                     @Param("user") com.joshfouchey.smsarchive.model.User user);

    @Query("select (count(m) > 0) from Message m where m.user = :user and m.timestamp = :ts and m.msgBox = :msgBox and m.protocol = :protocol and lower(trim(coalesce(m.body,''))) = :body")
    boolean existsByTimestampAndBody(@Param("ts") Instant timestamp,
                                      @Param("body") String bodyNormalized,
                                      @Param("msgBox") Integer msgBox,
                                      @Param("protocol") com.joshfouchey.smsarchive.model.MessageProtocol protocol,
                                      @Param("user") com.joshfouchey.smsarchive.model.User user);

    @EntityGraph(attributePaths = {"parts", "senderContact"})
    @Query("select m from Message m where m.id = :id and m.user = :user")
    Message findByIdAndUser(@Param("id") Long id, @Param("user") com.joshfouchey.smsarchive.model.User user);

    @EntityGraph(attributePaths = {"parts", "senderContact"})
    @Query("select m from Message m where m.conversation.id = :conversationId and m.user = :user and m.timestamp < :centerTs order by m.timestamp desc")
    List<Message> findBeforeInConversation(@Param("conversationId") Long conversationId,
                                           @Param("centerTs") Instant centerTs,
                                           @Param("user") com.joshfouchey.smsarchive.model.User user,
                                           Pageable pageable);

    @EntityGraph(attributePaths = {"parts", "senderContact"})
    @Query("select m from Message m where m.conversation.id = :conversationId and m.user = :user and m.timestamp > :centerTs order by m.timestamp asc")
    List<Message> findAfterInConversation(@Param("conversationId") Long conversationId,
                                          @Param("centerTs") Instant centerTs,
                                          @Param("user") com.joshfouchey.smsarchive.model.User user,
                                          Pageable pageable);

    // Timeline index queries for historical navigation
    @Query(value = """
SELECT EXTRACT(YEAR FROM m.timestamp) AS year,
       EXTRACT(MONTH FROM m.timestamp) AS month,
       COUNT(*) AS count,
       MIN(m.id) AS first_message_id,
       MAX(m.id) AS last_message_id,
       MIN(m.timestamp) AS first_timestamp,
       MAX(m.timestamp) AS last_timestamp
FROM messages m
WHERE m.conversation_id = :conversationId AND m.user_id = :userId
GROUP BY year, month
ORDER BY year, month
""", nativeQuery = true)
    List<TimelineBucketProjection> getConversationTimeline(@Param("conversationId") Long conversationId,
                                                            @Param("userId") UUID userId);

    // Date-range message queries for jump-to-date functionality
    @Query("select m from Message m where m.conversation.id = :conversationId and m.user = :user and m.timestamp >= :dateFrom and m.timestamp <= :dateTo order by m.timestamp asc")
    Page<Message> findByConversationAndDateRange(@Param("conversationId") Long conversationId,
                                                  @Param("dateFrom") Instant dateFrom,
                                                  @Param("dateTo") Instant dateTo,
                                                  @Param("user") com.joshfouchey.smsarchive.model.User user,
                                                  Pageable pageable);

    interface DayCountProjection { java.sql.Timestamp getDay_ts(); long getCount(); }

    interface TimelineBucketProjection {
        int getYear();
        int getMonth();
        long getCount();
        Long getFirst_message_id();
        Long getLast_message_id();
        java.sql.Timestamp getFirst_timestamp();
        java.sql.Timestamp getLast_timestamp();
    }

    interface ContactSummaryProjection {
        Long getContactId();
        String getContactName();
        java.sql.Timestamp getLastMessageTimestamp();
        String getLastMessagePreview();
        boolean getHasImage();
    }
}
