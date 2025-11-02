package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.Conversation;
import com.joshfouchey.smsarchive.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByUserAndExternalThreadId(User user, String externalThreadId);

    @Query("select distinct c from Conversation c left join fetch c.participants p left join fetch p.contact where c.user = :user and c.externalThreadId = :ext")
    Optional<Conversation> findWithParticipantsByUserAndExternalThreadId(@Param("user") User user, @Param("ext") String externalThreadId);
}
