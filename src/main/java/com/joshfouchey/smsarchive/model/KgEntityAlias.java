package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

@Entity
@Table(name = "kg_entity_aliases", indexes = {
    @Index(name = "idx_kg_aliases_entity", columnList = "entity_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KgEntityAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    private KgEntity entity;

    @Column(nullable = false)
    private String alias;

    @Column(length = 50)
    @Builder.Default
    private String source = "EXTRACTED";

    @Builder.Default
    private Float confidence = 0.8f;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
