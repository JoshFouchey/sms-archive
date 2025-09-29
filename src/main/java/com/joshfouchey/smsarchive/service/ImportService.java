package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Sms;
import com.joshfouchey.smsarchive.model.Mms;
import com.joshfouchey.smsarchive.model.MmsPart;
import com.joshfouchey.smsarchive.repository.SmsRepository;
import com.joshfouchey.smsarchive.repository.MmsRepository;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImportService {

    private final SmsRepository smsRepo;
    private final MmsRepository mmsRepo;

    public ImportService(SmsRepository smsRepo, MmsRepository mmsRepo) {
        this.smsRepo = smsRepo;
        this.mmsRepo = mmsRepo;
    }

    public int importFromXml(File xmlFile) throws Exception {
        int count = 0;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        // --- Import SMS ---
        NodeList smsNodes = doc.getElementsByTagName("sms");
        for (int i = 0; i < smsNodes.getLength(); i++) {
            Element el = (Element) smsNodes.item(i);

            Sms sms = new Sms();
            sms.setAddress(el.getAttribute("address"));
            sms.setDate(Instant.ofEpochMilli(Long.parseLong(el.getAttribute("date"))));
            sms.setBody(el.getAttribute("body"));
            sms.setType(Integer.parseInt(el.getAttribute("type")));

            smsRepo.save(sms);
            count++;
        }

        // --- Import MMS ---
        NodeList mmsNodes = doc.getElementsByTagName("mms");
        for (int i = 0; i < mmsNodes.getLength(); i++) {
            Element el = (Element) mmsNodes.item(i);

            Mms mms = new Mms();
            mms.setAddress(el.getAttribute("address"));
            mms.setContactName(el.getAttribute("contact_name"));
            mms.setDate(Instant.ofEpochMilli(Long.parseLong(el.getAttribute("date"))));
            mms.setMsgBox(Integer.parseInt(el.getAttribute("msg_box")));

            List<MmsPart> parts = new ArrayList<>();
            NodeList partNodes = el.getElementsByTagName("part");
            for (int j = 0; j < partNodes.getLength(); j++) {
                Element partEl = (Element) partNodes.item(j);

                MmsPart part = new MmsPart();
                part.setContentType(partEl.getAttribute("ct"));
                part.setText(partEl.getAttribute("text"));
                part.setMms(mms); // set relationship
                parts.add(part);
            }

            mms.setParts(parts);
            mmsRepo.save(mms);
            count++;
        }

        return count;
    }

}
