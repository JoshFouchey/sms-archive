package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
import com.joshfouchey.smsarchive.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findBySenderContainingIgnoreCase(String sender);

    List<Message> findByRecipientContainingIgnoreCase(String recipient);

    @Query("select m from Message m where lower(m.body) like lower(concat('%', :text, '%'))")
    List<Message> searchByText(String text);

    List<Message> findByTimestampBetween(Instant start, Instant end);

    // Page messages for a contact
    Page<Message> findByContactId(Long contactId, Pageable pageable);

    // Java
    @Query("""
SELECT new com.joshfouchey.smsarchive.dto.ContactSummaryDto(
    c.id,
    COALESCE(c.name, c.number),
    MAX(m.timestamp),
    MAX(
        CASE WHEN m.timestamp = (
            SELECT MAX(m2.timestamp) FROM Message m2 WHERE m2.contact = c
        )
        THEN SUBSTRING(COALESCE(m.body, ''), 1, 200)
        ELSE NULL END
    ),
    MAX(
        CASE WHEN m.timestamp = (
            SELECT MAX(m3.timestamp) FROM Message m3 WHERE m3.contact = c
        )
        AND EXISTS (
            SELECT 1 FROM MessagePart p
            WHERE p.message = m
              AND p.contentType LIKE 'image/%'
        )
        THEN 1 ELSE 0 END
    ) = 1
)
FROM Message m
JOIN m.contact c
GROUP BY c.id, c.name
ORDER BY MAX(m.timestamp) DESC
""")
    List<ContactSummaryDto> findAllContactSummaries();


}
