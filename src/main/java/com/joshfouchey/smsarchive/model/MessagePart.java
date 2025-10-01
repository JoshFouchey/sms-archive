// MessagePart.java
package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "message_parts")
public class MessagePart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    private Integer seq;

    @Column(name = "ct")
    private String contentType;

    private String name;

    @Column(columnDefinition = "text")
    private String text;

    private String filePath;

    // getters and setters
}
