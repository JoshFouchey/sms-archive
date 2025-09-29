package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.Sms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface SmsRepository extends JpaRepository<Sms, Long> {
    List<Sms> findByAddress(String address);
    List<Sms> findByDateBetween(Instant start, Instant end);

    // Full-text search (uses ILIKE for simplicity, can swap for Postgres FTS)
    @Query("SELECT s FROM Sms s WHERE LOWER(s.body) LIKE LOWER(CONCAT('%', :text, '%'))")
    List<Sms> searchByText(String text);
}
