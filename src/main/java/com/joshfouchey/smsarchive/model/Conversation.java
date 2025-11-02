package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conversations_user", columnList = "user_id"),
        @Index(name = "idx_conversations_last_message", columnList = "last_message_at"),
        // New index to quickly resolve group conversations by external thread key
        @Index(name = "idx_conversations_user_threadkey", columnList = "user_id,thread_key")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 12, nullable = false)
    private ConversationType type;

    // For GROUP this is the group label; for ONE_TO_ONE may be cached display.
    private String name;

    // External thread/group key (e.g. RCS group address or MMS thread id); nullable for ONE_TO_ONE
    @Column(name = "thread_key", length = 255)
    private String threadKey;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(name = "conversation_contacts",
            joinColumns = @JoinColumn(name = "conversation_id"),
            inverseJoinColumns = @JoinColumn(name = "contact_id"))
    @Builder.Default
    private Set<Contact> participants = new HashSet<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
