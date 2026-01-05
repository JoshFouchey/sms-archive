package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("select c from Conversation c where c.user = :user order by c.lastMessageAt desc nulls last, c.id desc")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"participants"})
    List<Conversation> findAllByUserOrderByLastMessage(@Param("user") User user);

    // Optimized query to fetch conversations with last message details in single query
    @Query(value = """
        SELECT c.id as conversationId,
               c.name as conversationName,
               c.last_message_at as lastMessageAt,
               m.id as lastMessageId,
               m.body as lastMessageBody,
               m.media as lastMessageMedia
        FROM conversations c
        LEFT JOIN LATERAL (
            SELECT id, body, media
            FROM messages
            WHERE conversation_id = c.id AND user_id = c.user_id
            ORDER BY timestamp DESC
            LIMIT 1
        ) m ON true
        WHERE c.user_id = :userId
        ORDER BY c.last_message_at DESC NULLS LAST, c.id DESC
        """, nativeQuery = true)
    List<ConversationWithLastMessageProjection> findAllByUserWithLastMessage(@Param("userId") java.util.UUID userId);

    interface ConversationWithLastMessageProjection {
        Long getConversationId();
        String getConversationName();
        java.time.Instant getLastMessageAt();
        Long getLastMessageId();
        String getLastMessageBody();
        String getLastMessageMedia();
    }

    @Query("select c from Conversation c join c.participants p where c.user = :user and p.normalizedNumber = :normalizedNumber and size(c.participants) = 1")
    List<Conversation> findByUserAndSingleParticipant(@Param("user") User user, @Param("normalizedNumber") String normalizedNumber);

    @Query("select c from Conversation c where c.user = :user and c.id = :id")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"participants"})
    Optional<Conversation> findByIdAndUser(@Param("id") Long id, @Param("user") User user);

    @Query("select c from Conversation c where c.user = :user and c.threadKey = :threadKey")
    Optional<Conversation> findByThreadKey(@Param("user") User user, @Param("threadKey") String threadKey);

    @Query("select c from Conversation c join c.participants p where p = :contact")
    List<Conversation> findByParticipant(@Param("contact") com.joshfouchey.smsarchive.model.Contact contact);
}
