package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.dto.ConversationSummaryDto;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessageProtocol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<Message, Long> {
    @Query("select m from Message m where m.conversation.id = :conversationId and m.user = :user")
    Page<Message> findByConversationIdAndUser(@Param("conversationId") Long conversationId, @Param("user") com.joshfouchey.smsarchive.model.User user, Pageable pageable);

    @Query("select (count(m) > 0) from Message m where m.conversation.id = :conversationId and m.timestamp = :ts and m.msgBox = :msgBox and m.protocol = :protocol and lower(coalesce(m.body,'')) = lower(coalesce(:body,''))")
    boolean existsConversationDuplicate(@Param("conversationId") Long conversationId,
                                        @Param("ts") Instant timestamp,
                                        @Param("msgBox") int msgBox,
                                        @Param("protocol") MessageProtocol protocol,
                                        @Param("body") String bodyNormalized);

    @Query("""
SELECT new com.joshfouchey.smsarchive.dto.ConversationSummaryDto(
    c.id,
    c.type,
    COALESCE(c.displayName, c.externalThreadId),
    COUNT(DISTINCT p.contact.id),
    MAX(m.timestamp),
    MAX(CASE WHEN m.timestamp = (
        SELECT MAX(m2.timestamp) FROM Message m2 WHERE m2.conversation = c AND m2.user = :user
    ) THEN SUBSTRING(COALESCE(m.body, ''),1,200) ELSE NULL END),
    CASE WHEN MAX(CASE WHEN m.timestamp = (
        SELECT MAX(m3.timestamp) FROM Message m3 WHERE m3.conversation = c AND m3.user = :user
    ) AND EXISTS (SELECT 1 FROM MessagePart mp WHERE mp.message = m AND mp.contentType LIKE 'image/%') THEN 1 ELSE 0 END) = 1 THEN true ELSE false END
)
FROM Message m JOIN m.conversation c LEFT JOIN c.participants p
WHERE m.user = :user
GROUP BY c.id, c.type, c.displayName, c.externalThreadId
ORDER BY MAX(m.timestamp) DESC
""")
    List<ConversationSummaryDto> findAllConversationSummaries(@Param("user") com.joshfouchey.smsarchive.model.User user);
}

