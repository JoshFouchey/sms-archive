// MessageRepository.java
package com.joshfouchey.smsarchive.repository;

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
}
