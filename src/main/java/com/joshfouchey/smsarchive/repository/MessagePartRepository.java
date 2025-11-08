package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessagePartRepository extends JpaRepository<MessagePart, Long> {

    @Query("SELECT p FROM MessagePart p WHERE p.contentType LIKE 'image/%' AND p.message.user = :user ORDER BY p.id DESC")
    Page<MessagePart> findAllImages(@Param("user") User user, Pageable pageable);


    @Query("SELECT p FROM MessagePart p JOIN p.message m JOIN m.conversation conv JOIN conv.participants c WHERE p.contentType LIKE 'image/%' AND c.id = :contactId AND m.user = :user ORDER BY p.id DESC")
    Page<MessagePart> findImagesByContactId(@Param("contactId") Long contactId, @Param("user") User user, Pageable pageable);

    @Query("select count(p) from MessagePart p where p.contentType like 'image/%' and p.message.user = :user")
    long countImageParts(@Param("user") User user);

    // New methods for thumbnail rebuild job
    @Query("SELECT p FROM MessagePart p WHERE p.contentType LIKE 'image/%' AND p.filePath IS NOT NULL AND p.message.user = :user ORDER BY p.id ASC")
    List<MessagePart> findAllImagePartsByUser(@Param("user") User user);

    @Query("SELECT p FROM MessagePart p JOIN p.message m JOIN m.conversation conv JOIN conv.participants c WHERE p.contentType LIKE 'image/%' AND p.filePath IS NOT NULL AND c.id = :contactId AND m.user = :user ORDER BY p.id ASC")
    List<MessagePart> findImagePartsByContactId(@Param("contactId") Long contactId, @Param("user") User user);
}