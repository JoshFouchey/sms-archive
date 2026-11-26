package com.joshfouchey.smsarchive.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import com.joshfouchey.smsarchive.service.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.joshfouchey.smsarchive.service.ImportService.ImportProgress;

@RestController
@RequestMapping("/import")
public class ImportController {
    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/stream")
    public Map<String,Object> uploadStream(@RequestParam("file") MultipartFile file) throws Exception {
        Path tmp = Files.createTempFile("sms-stream", ".xml");
        try {
            file.transferTo(tmp.toFile());
            // The import only reads the upload, so drop our copy once the job ends either way
            UUID id = importService.startImportAsync(tmp, (jobId, success) -> deleteQuietly(tmp));
            return Map.of("jobId", id.toString(), "status", "STARTED");
        } catch (Exception e) {
            deleteQuietly(tmp);
            throw e;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete import upload temp file {}", path, e);
        }
    }

    @GetMapping("/progress/{id}")
    public ImportProgress progress(@PathVariable String id) {
        return importService.getProgress(UUID.fromString(id));
    }
}
