package com.joshfouchey.smsarchive.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
public class Sms {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // let DB assign IDs
    private Long id;

    private String address;

    private Instant date;

    @Column(length = 5000) // SMS bodies can be long
    private String body;

    private int type;
}
