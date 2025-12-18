package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "contacts",
        indexes = {
                @Index(name = "ux_contacts_user_normalized", columnList = "user_id,normalized_number", unique = true)
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Original formatted number (first seen)
    @Column(nullable = false)
    private String number;

    // Canonical digits-only (normalized)
    @Column(name = "normalized_number", nullable = false)
    private String normalizedNumber;

    private String name;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Merge support - track when contacts are merged together
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_id")
    private Contact mergedInto;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
