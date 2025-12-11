// src/main/java/com/joshfouchey/smsarchive/repository/MessageRepository.java
package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("select m from Message m where lower(m.body) like lower(concat('%', :text, '%'))")
    List<Message> searchByText(@Param("text") String text);

    List<Message> findByTimestampBetween(Instant start, Instant end);

    @Query("""
SELECT new com.joshfouchey.smsarchive.dto.ContactSummaryDto(
    c.id,
    COALESCE(c.name, c.number),
    MAX(m.timestamp),
    MAX(
        CASE WHEN m.timestamp = (
            SELECT MAX(m2.timestamp) 
            FROM Message m2 
            JOIN m2.conversation conv 
            JOIN conv.participants p 
            WHERE p.id = c.id AND m2.user = :user
        ) THEN SUBSTRING(COALESCE(m.body, ''), 1, 200) ELSE NULL END
    ),
    CASE WHEN MAX(
        CASE WHEN m.timestamp = (
            SELECT MAX(m3.timestamp) 
            FROM Message m3 
            JOIN m3.conversation conv2 
            JOIN conv2.participants p2 
            WHERE p2.id = c.id AND m3.user = :user
        ) AND EXISTS (
            SELECT 1 FROM MessagePart p WHERE p.message = m AND p.contentType LIKE 'image/%'
        ) THEN 1 ELSE 0 END
    ) = 1 THEN true ELSE false END
)
FROM Message m 
JOIN m.conversation conv 
JOIN conv.participants c
WHERE m.user = :user
GROUP BY c.id, c.name
ORDER BY MAX(m.timestamp) DESC
""")
    List<ContactSummaryDto> findAllContactSummaries(@Param("user") com.joshfouchey.smsarchive.model.User user);

    @Query("""
SELECT new com.joshfouchey.smsarchive.dto.TopContactDto(
    c.id, 
    COALESCE(c.name, c.number), 
    COUNT(m.id)
)
FROM Message m 
JOIN m.senderContact c
WHERE m.timestamp >= :since 
  AND m.user = :user
  AND m.direction = com.joshfouchey.smsarchive.model.MessageDirection.INBOUND
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

    @Query("select m from Message m where m.user = :user and lower(m.body) like lower(concat('%', :text, '%'))")
    List<Message> searchByTextUser(@Param("text") String text, @Param("user") com.joshfouchey.smsarchive.model.User user);

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

    @Query("select m from Message m where m.conversation.id = :conversationId and m.user = :user")
    List<Message> findAllByConversationIdAndUser(@Param("conversationId") Long conversationId,
                                                 @Param("user") com.joshfouchey.smsarchive.model.User user);

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

    @Query("select m from Message m where m.id = :id and m.user = :user")
    Message findByIdAndUser(@Param("id") Long id, @Param("user") com.joshfouchey.smsarchive.model.User user);

    @Query("select m from Message m where m.conversation.id = :conversationId and m.user = :user and m.timestamp < :centerTs order by m.timestamp desc")
    List<Message> findBeforeInConversation(@Param("conversationId") Long conversationId,
                                           @Param("centerTs") Instant centerTs,
                                           @Param("user") com.joshfouchey.smsarchive.model.User user,
                                           Pageable pageable);

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
}
