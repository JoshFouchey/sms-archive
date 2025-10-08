package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "message_parts",
        indexes = {
                @Index(name = "ix_message_parts_message", columnList = "message_id"),
                @Index(name = "ix_message_parts_ct", columnList = "ct")
        })
@Getter
@Setter
public class MessagePart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private Message message;

    private Integer seq;

    @Column(name = "ct")
    private String contentType;

    private String name;

    @Column(columnDefinition = "text")
    private String text;

    private String filePath;
    private Long sizeBytes;
}
