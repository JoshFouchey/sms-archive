package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.EmbeddingJob;
import com.joshfouchey.smsarchive.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmbeddingJobRepository extends JpaRepository<EmbeddingJob, UUID> {

    List<EmbeddingJob> findByUserOrderByCreatedAtDesc(User user);

    Optional<EmbeddingJob> findByIdAndUser(UUID id, User user);

    Optional<EmbeddingJob> findFirstByUserAndStatusOrderByCreatedAtDesc(User user, String status);

    List<EmbeddingJob> findByStatusIn(List<String> statuses);
}
