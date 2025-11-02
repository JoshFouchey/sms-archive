package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("select c from Conversation c where c.user = :user order by c.lastMessageAt desc nulls last, c.id desc")
    List<Conversation> findAllByUserOrderByLastMessage(@Param("user") User user);

    @Query("select c from Conversation c join c.participants p where c.user = :user and p.normalizedNumber = :normalizedNumber and size(c.participants) = 1")
    List<Conversation> findByUserAndSingleParticipant(@Param("user") User user, @Param("normalizedNumber") String normalizedNumber);

    @Query("select c from Conversation c where c.user = :user and c.id = :id")
    Optional<Conversation> findByIdAndUser(@Param("id") Long id, @Param("user") User user);

    @Query("select c from Conversation c where c.user = :user and c.threadKey = :threadKey")
    Optional<Conversation> findByThreadKey(@Param("user") User user, @Param("threadKey") String threadKey);
}
