package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "embedding_jobs", indexes = {
    @Index(name = "idx_embedding_jobs_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class EmbeddingJob {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "total_messages")
    private Long totalMessages = 0L;

    private Long processed = 0L;

    private Long failed = 0L;

    @Column(name = "model_name", length = 100, nullable = false)
    private String modelName;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }
}
