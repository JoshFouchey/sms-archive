package com.joshfouchey.smsarchive.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import com.joshfouchey.smsarchive.service.ImportService;
import com.joshfouchey.smsarchive.service.ImportService.ImportProgress;

@RestController
@RequestMapping("/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/stream")
    public Map<String,Object> uploadStream(@RequestParam("file") MultipartFile file) throws Exception {
        Path tmp = Files.createTempFile("sms-stream", ".xml");
        file.transferTo(tmp.toFile());
        UUID id = importService.startImportAsync(tmp);
        return Map.of("jobId", id.toString(), "status", "STARTED");
    }

    @GetMapping("/progress/{id}")
    public ImportProgress progress(@PathVariable String id) {
        return importService.getProgress(UUID.fromString(id));
    }
}
