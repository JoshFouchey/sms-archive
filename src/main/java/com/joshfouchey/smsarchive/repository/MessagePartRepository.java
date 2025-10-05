// MessageRepository.java
package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.MessagePart;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Repository
public interface MessagePartRepository extends JpaRepository<MessagePart, Long> {

    @Query("SELECT p FROM MessagePart p WHERE p.contentType LIKE 'image/%'")
    Page<MessagePart> findAllImages(Pageable pageable);

    @Query("SELECT p FROM MessagePart p " +
            "JOIN p.message m " +
            "WHERE p.contentType LIKE 'image/%' " +
            "AND (m.contactName = :contact)")
    Page<MessagePart> findImagesByContact(@Param("contact") String contact, Pageable pageable);
}

