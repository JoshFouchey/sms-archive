package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

@Entity
@Table(name = "kg_triples", indexes = {
    @Index(name = "idx_kg_triples_subject", columnList = "subject_id"),
    @Index(name = "idx_kg_triples_object", columnList = "object_id"),
    @Index(name = "idx_kg_triples_predicate", columnList = "user_id,predicate"),
    @Index(name = "idx_kg_triples_source", columnList = "source_message_id"),
    @Index(name = "idx_kg_triples_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KgTriple {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private KgEntity subject;

    @Column(length = 100, nullable = false)
    private String predicate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_id")
    private KgEntity object;

    @Column(name = "object_value", columnDefinition = "text")
    private String objectValue;

    @Builder.Default
    private Float confidence = 0.8f;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_message_id")
    private Message sourceMessage;

    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_negated")
    @Builder.Default
    private Boolean isNegated = false;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "fact_hash", length = 64)
    private String factHash;

    @Column(name = "fact_date")
    private Instant factDate;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_by")
    private KgTriple supersededBy;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "conflict_cluster_id")
    private Long conflictClusterId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
