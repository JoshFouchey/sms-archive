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
@Table(name = "messages")
@Getter
@Setter
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String protocol;      // "SMS", "MMS", "RCS"
    private String contactName;      // "SMS", "MMS", "RCS"
    private String sender;        // normalized sender field
    private String recipient;     // normalized recipient field
    private Instant timestamp;    // consistent timestamp naming
    private Integer msgBox;       // inbox=1, sent=2, etc.

    @Column(columnDefinition = "text")
    private String body;          // SMS/RCS text, or MMS caption

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> media;     // MMS parts, RCS attachments, gifs, etc.

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;  // delivery status, reactions, read receipts, etc.

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MessagePart> parts = new ArrayList<>();
}
