package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "mms_parts")
public class MmsPart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "mms_id", nullable = false)
    private Mms mms;

    private Integer seq;

    @Column(name = "ct", columnDefinition = "text")
    private String contentType;   // maps to ct TEXT in DB

    @Column(columnDefinition = "text")
    private String name;

    @Column(columnDefinition = "text")
    private String text;

    @Column(name = "file_path", columnDefinition = "text")
    private String filePath;
}
