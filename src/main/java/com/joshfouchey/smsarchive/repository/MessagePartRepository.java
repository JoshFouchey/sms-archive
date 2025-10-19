package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.MessagePart;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessagePartRepository extends JpaRepository<MessagePart, Long> {

    @Query("SELECT p FROM MessagePart p WHERE p.contentType LIKE 'image/%' ORDER BY p.id DESC")
    Page<MessagePart> findAllImages(Pageable pageable);


    @Query("SELECT p FROM MessagePart p JOIN p.message m JOIN m.contact c WHERE p.contentType LIKE 'image/%' AND c.id = :contactId ORDER BY p.id DESC")
    Page<MessagePart> findImagesByContactId(@Param("contactId") Long contactId, Pageable pageable);

    @Query("select count(p) from MessagePart p where p.contentType like 'image/%'")
    long countImageParts();
}