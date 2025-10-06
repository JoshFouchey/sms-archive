    // MessageRepository.java
    package com.joshfouchey.smsarchive.repository;

    import com.joshfouchey.smsarchive.dto.ContactSummaryDto;
    import com.joshfouchey.smsarchive.model.Message;
    import org.springframework.data.jpa.repository.JpaRepository;
    import org.springframework.data.jpa.repository.Query;

    import java.time.Instant;
    import java.util.List;

    public interface MessageRepository extends JpaRepository<Message, Long> {

        // Search sender (formerly "address")
        List<Message> findBySenderContainingIgnoreCase(String sender);

        // Search recipient
        List<Message> findByRecipientContainingIgnoreCase(String recipient);

        // Search body text
        @Query("select m from Message m where lower(m.body) like lower(concat('%', :text, '%'))")
        List<Message> searchByText(String text);

        // Time range search (formerly "date")
        List<Message> findByTimestampBetween(Instant start, Instant end);

        @Query("""
        SELECT new com.joshfouchey.smsarchive.dto.ContactSummaryDto(
            m.contactName,
            MAX(m.timestamp),
            SUBSTRING(
                (SELECT m2.body FROM Message m2
                 WHERE m2.contactName = m.contactName
                 ORDER BY m2.timestamp DESC LIMIT 1),
            1, 200),
            EXISTS (
                SELECT p.id FROM MessagePart p
                WHERE p.message = (
                    SELECT m3 FROM Message m3
                    WHERE m3.contactName = m.contactName
                    ORDER BY m3.timestamp DESC LIMIT 1
                )
                AND p.contentType LIKE 'image/%'
            )
        )
        FROM Message m
        WHERE m.contactName IS NOT NULL
        GROUP BY m.contactName
        ORDER BY MAX(m.timestamp) DESC
    """)
        List<ContactSummaryDto> findAllContactSummaries();
    }
