package com.joshfouchey.smsarchive.model;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "messages",
        indexes = {
                @Index(name = "ix_messages_timestamp", columnList = "timestamp"),
                @Index(name = "ix_messages_contact", columnList = "contact_id"),
                @Index(name = "ix_messages_sender", columnList = "sender"),
                @Index(name = "ix_messages_recipient", columnList = "recipient"),
                @Index(name = "ix_messages_user", columnList = "user_id"),
                // Updated prefix index now conversation-based (created via migration V6)
                @Index(name = "ix_messages_conversation", columnList = "conversation_id")
        })
@Getter
@Setter
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private MessageProtocol protocol;   // SMS/MMS/RCS

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private MessageDirection direction; // INBOUND / OUTBOUND (derived at import)

    // Original roles (retain for auditing / multi-recipient)
    private String sender;
    private String recipient;           // comma-separated list if multiple

    // Legacy single-contact pointer (nullable once groups supported)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    // New conversation reference (NOT NULL at DB level after migration)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(columnDefinition = "text")
    private String body;

    // Optional original box code
    private Integer msgBox;             // nullable; imported for duplicate logic & direction

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> media;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    private Instant deliveredAt;
    private Instant readAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MessagePart> parts = new ArrayList<>();

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
