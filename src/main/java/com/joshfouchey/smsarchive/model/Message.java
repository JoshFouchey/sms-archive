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
                // Composite prefix index used by duplicate check BEFORE body comparison
                @Index(name = "ix_messages_dedupe_prefix", columnList = "contact_id,timestamp,msg_box,protocol"),
                @Index(name = "idx_messages_conversation", columnList = "conversation_id")
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation; // nullable for legacy single-contact messages

    @Column(nullable = false)
    private Instant timestamp;

    @Column(columnDefinition = "text")
    private String body;

    // Optional original box code if you still want it (remove if not needed)
    private Integer msgBox;             // nullable; omit usage if redundant

    // JSON attachments summary (thumbnails, part refs, etc.)
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> media;

    // Arbitrary metadata: statuses, reactions, import raw fields, subject, group id
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // Delivery/read tracking (promote if you need fast querying)
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
