package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.SmsBackup;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import java.io.File;

public class XmlParser {

    public SmsBackup parse(File file) throws Exception {
        JAXBContext context = JAXBContext.newInstance(SmsBackup.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return (SmsBackup) unmarshaller.unmarshal(file);
    }
}
