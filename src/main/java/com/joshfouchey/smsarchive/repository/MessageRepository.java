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

    Page<Message> findByContactId(Long contactId, Pageable pageable);

    @Query("""
SELECT new com.joshfouchey.smsarchive.dto.ContactSummaryDto(
    c.id,
    COALESCE(c.name, c.number),
    MAX(m.timestamp),
    MAX(
        CASE WHEN m.timestamp = (
            SELECT MAX(m2.timestamp) FROM Message m2 WHERE m2.contact = c AND m2.user = :user
        ) THEN SUBSTRING(COALESCE(m.body, ''), 1, 200) ELSE NULL END
    ),
    CASE WHEN MAX(
        CASE WHEN m.timestamp = (
            SELECT MAX(m3.timestamp) FROM Message m3 WHERE m3.contact = c AND m3.user = :user
        ) AND EXISTS (
            SELECT 1 FROM MessagePart p WHERE p.message = m AND p.contentType LIKE 'image/%'
        ) THEN 1 ELSE 0 END
    ) = 1 THEN true ELSE false END
)
FROM Message m JOIN m.contact c
WHERE m.user = :user
GROUP BY c.id, c.name
ORDER BY MAX(m.timestamp) DESC
""")
    List<ContactSummaryDto> findAllContactSummaries(@Param("user") com.joshfouchey.smsarchive.model.User user);

    @Query("""
SELECT new com.joshfouchey.smsarchive.dto.TopContactDto(c.id, COALESCE(c.name, c.number), COUNT(m))
FROM Message m JOIN m.contact c
WHERE m.timestamp >= :since AND m.user = :user
GROUP BY c.id, c.name, c.number
ORDER BY COUNT(m) DESC
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

    @Query("select m from Message m where m.contact.id = :contactId and m.user = :user")
    Page<Message> findByContactIdAndUser(@Param("contactId") Long contactId, @Param("user") com.joshfouchey.smsarchive.model.User user, Pageable pageable);

    @Query("select m from Message m where m.user = :user and lower(m.body) like lower(concat('%', :text, '%'))")
    List<Message> searchByTextUser(@Param("text") String text, @Param("user") com.joshfouchey.smsarchive.model.User user);

    @Query("select m from Message m where m.user = :user and m.timestamp between :start and :end")
    List<Message> findByTimestampBetweenUser(@Param("start") Instant start, @Param("end") Instant end, @Param("user") com.joshfouchey.smsarchive.model.User user);


    @Query("select (count(m) > 0) from Message m where m.contact = :contact and m.timestamp = :ts and m.msgBox = :msgBox and m.protocol = :protocol and lower(coalesce(m.body,'')) = lower(coalesce(:body,''))")
    boolean existsDuplicate(@Param("contact") Contact contact,
                            @Param("ts") Instant timestamp,
                            @Param("msgBox") int msgBox,
                            @Param("protocol") MessageProtocol protocol,
                            @Param("body") String bodyNormalized);

    @Query("select (count(m) > 0) from Message m where m.contact.id = :contactId and m.timestamp = :ts and m.msgBox = :msgBox and m.protocol = :protocol and lower(coalesce(m.body,'')) = lower(coalesce(:body,''))")
    boolean existsDuplicateHash(@Param("contactId") Long contactId,
                                @Param("ts") Instant timestamp,
                                @Param("msgBox") int msgBox,
                                @Param("protocol") MessageProtocol protocol,
                                @Param("body") String body);

    long countByUser(com.joshfouchey.smsarchive.model.User user);

    interface DayCountProjection { java.sql.Timestamp getDay_ts(); long getCount(); }
}
