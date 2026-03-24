package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "message_embeddings", indexes = {
    @Index(name = "idx_message_embeddings_user", columnList = "user_id"),
    @Index(name = "idx_message_embeddings_message", columnList = "message_id")
})
@Getter
@Setter
public class MessageEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Stored as vector(768) in PostgreSQL via pgvector.
    // JPA sees it as a String column; we convert to/from float[] in the repository/service.
    @Column(name = "embedding", columnDefinition = "vector(768)")
    private String embedding;

    @Column(name = "model_name", length = 100, nullable = false)
    private String modelName;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
