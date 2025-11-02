package com.joshfouchey.smsarchive.controller;

import com.joshfouchey.smsarchive.dto.GalleryImageDto;
import com.joshfouchey.smsarchive.dto.PagedResponse;
import com.joshfouchey.smsarchive.mapper.GalleryMapper;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.service.MediaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityNotFoundException;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/images")
    public ResponseEntity<?> getImages(
            @RequestParam(required = false) Long contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        try {
            Page<MessagePart> result = mediaService.getImages(contactId, page, size);
            List<GalleryImageDto> dto = result.stream()
                    .map(GalleryMapper::toDto)
                    .toList();

            PagedResponse<GalleryImageDto> response = new PagedResponse<>(
                    dto,
                    result.getNumber(),
                    result.getSize(),
                    result.getTotalElements(),
                    result.getTotalPages(),
                    result.isFirst(),
                    result.isLast()
            );

            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", 404,
                    "error", "Not Found",
                    "message", ex.getMessage()
            ));
        }
    }


    @DeleteMapping("/images/{id}")
    public ResponseEntity<?> deleteImage(@PathVariable Long id) {
        boolean deleted = mediaService.deleteImage(id);
        return deleted ? ResponseEntity.ok(Map.of("status", "ok", "id", id))
                : ResponseEntity.notFound().build();
    }
}
