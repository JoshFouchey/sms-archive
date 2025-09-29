package com.joshfouchey.smsarchive.dto;

import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@XmlRootElement(name = "smses")
@XmlAccessorType(XmlAccessType.FIELD)
public class SmsBackup {

    @XmlAttribute(name = "count")
    private int count;

    @XmlElement(name = "sms")
    private List<SmsXml> smsList;

    @XmlElement(name = "mms")
    private List<MmsXml> mmsList;

    // getters + setters
}
