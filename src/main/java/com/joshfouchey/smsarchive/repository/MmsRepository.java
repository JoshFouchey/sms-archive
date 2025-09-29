package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.Mms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface MmsRepository extends JpaRepository<Mms, Long> {
    List<Mms> findByAddress(String address);
    List<Mms> findByDateBetween(Instant start, Instant end);

    // Search text in MMS parts
    @Query("SELECT DISTINCT m FROM Mms m JOIN m.parts p WHERE LOWER(p.text) LIKE LOWER(CONCAT('%', :text, '%'))")
    List<Mms> searchByPartText(String text);
}
