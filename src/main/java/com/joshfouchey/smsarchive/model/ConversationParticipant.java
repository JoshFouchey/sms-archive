package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;

@Entity
@Table(name = "conversation_participants")
@Getter
@Setter
public class ConversationParticipant {
    @EmbeddedId
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conversationId")
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("contactId")
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(name = "is_self")
    private boolean self;

    @Embeddable
    @Getter
    @Setter
    public static class Id implements Serializable {
        private Long conversationId;
        private Long contactId;
    }
}

