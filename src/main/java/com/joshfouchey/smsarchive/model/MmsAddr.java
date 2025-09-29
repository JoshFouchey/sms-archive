package com.joshfouchey.smsarchive.model;

import com.joshfouchey.smsarchive.model.Mms;
import jakarta.persistence.*;

@Entity
@Table(name = "mms_addrs")
public class MmsAddr {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String address;
    private Short type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mms_id")
    private Mms mms;

    // getters + setters
}
