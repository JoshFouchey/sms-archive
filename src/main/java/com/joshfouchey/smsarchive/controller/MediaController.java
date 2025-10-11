package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.MessagePartDto;
import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.service.MediaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/images")
    public List<MessagePartDto> getImages(
            @RequestParam(required = false) String contact,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Page<MessagePart> result = mediaService.getImages(contact, page, size);

        return result.stream()
                .map(p -> new MessagePartDto(
                        p.getId(),
                        p.getMessage().getId(),
                        p.getMessage().getSender(),
                        p.getMessage().getRecipient(),
                        p.getMessage().getTimestamp(),
                        normalizePath(p.getFilePath()),
                        p.getContentType()
                ))
                .toList();
    }

    private String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) return null; // propagate null, front-end can decide to hide
        return raw.replace("\\", "/");
    }

    @DeleteMapping("/images/{id}")
    public ResponseEntity<?> deleteImage(@PathVariable Long id) {
        boolean deleted = mediaService.deleteImage(id);
        return deleted ? ResponseEntity.ok(Map.of("status", "ok", "id", id))
                : ResponseEntity.notFound().build();
    }
}
