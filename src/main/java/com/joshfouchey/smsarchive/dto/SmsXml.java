package com.joshfouchey.smsarchive.dto;

import jakarta.xml.bind.annotation.XmlAttribute;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SmsXml {
    @XmlAttribute private String protocol;
    @XmlAttribute private String address;
    @XmlAttribute private String date;
    @XmlAttribute(name = "type") private String msgBox;
    @XmlAttribute private String body;
    @XmlAttribute(name = "contact_name") private String contactName;

    // getters + setters
}
