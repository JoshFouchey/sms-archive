package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "contacts",
        indexes = @Index(name = "ux_contacts_normalized", columnList = "normalized_number", unique = true))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Original formatted number (first seen)
    @Column(nullable = false)
    private String number;

    // Canonical digits-only (normalized)
    @Column(name = "normalized_number", nullable = false, unique = true)
    private String normalizedNumber;

    private String name;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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
