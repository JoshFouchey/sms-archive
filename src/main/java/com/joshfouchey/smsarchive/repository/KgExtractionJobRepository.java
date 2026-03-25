package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.KgExtractionJob;
import com.joshfouchey.smsarchive.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KgExtractionJobRepository extends JpaRepository<KgExtractionJob, UUID> {

    List<KgExtractionJob> findByUserOrderByCreatedAtDesc(User user);

    Optional<KgExtractionJob> findByIdAndUser(UUID id, User user);

    Optional<KgExtractionJob> findFirstByUserAndStatusOrderByCreatedAtDesc(User user, String status);

    List<KgExtractionJob> findByStatusIn(List<String> statuses);
}
