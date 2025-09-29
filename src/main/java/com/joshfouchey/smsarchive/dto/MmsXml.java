package com.joshfouchey.smsarchive.dto;

import jakarta.xml.bind.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class MmsXml {
    @XmlAttribute private String date;
    @XmlAttribute(name = "msg_box") private String msgBox;
    @XmlAttribute private String address;
    @XmlAttribute(name = "contact_name") private String contactName;

    @XmlElement(name = "parts")
    private Parts parts;

    @XmlElement(name = "addrs")
    private Addrs addrs;

    // getters + setters

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Parts {
        @XmlElement(name = "part")
        private List<Part> partList;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Part {
        @XmlAttribute private String seq;
        @XmlAttribute private String ct;
        @XmlAttribute private String name;
        @XmlAttribute private String text;
        @XmlAttribute(name = "file_path") private String filePath;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Addrs {
        @XmlElement(name = "addr")
        private List<Addr> addrList;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Addr {
        @XmlAttribute private String address;
        @XmlAttribute private String type;
    }
}
