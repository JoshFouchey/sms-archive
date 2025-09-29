package com.joshfouchey.smsarchive.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

import com.joshfouchey.smsarchive.service.ImportService;
@RestController
@RequestMapping("/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file) throws Exception {
        File tmp = File.createTempFile("sms", ".xml");
        file.transferTo(tmp);
        importService.importFromXml(tmp);
        return "Import complete!";
    }
}
