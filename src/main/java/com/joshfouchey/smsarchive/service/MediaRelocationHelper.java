package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessagePart;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helper component to relocate media parts and associated thumbnails from temporary
 * import directories ("_noconversation") into their final conversation-specific directory.
 */
@Slf4j
public class MediaRelocationHelper {
    private final ThumbnailService thumbnailService;
    private final Path mediaRoot;

    MediaRelocationHelper(ThumbnailService thumbnailService, Path mediaRoot) {
        this.thumbnailService = thumbnailService;
        this.mediaRoot = mediaRoot;
    }

    public void relocate(Message msg) {
        if (msg == null || msg.getConversation() == null || msg.getConversation().getId() == null || msg.getParts() == null) return;
        Path targetDir = mediaRoot.resolve(msg.getConversation().getId().toString());
        try { Files.createDirectories(targetDir); } catch (Exception e) { log.error("Failed creating target media dir {}", targetDir, e); return; }
        Map<String, String> movedPaths = new HashMap<>();
        for (MessagePart part : msg.getParts()) {
            relocatePart(part, targetDir, movedPaths);
        }
        if (!movedPaths.isEmpty()) {
            updateMediaJson(msg, movedPaths);
        }
    }

    private void relocatePart(MessagePart part, Path targetDir, Map<String, String> movedPaths) {
        String fp = part.getFilePath();
        if (fp == null) return;
        Path current = Paths.get(fp).normalize();
        Path parent = current.getParent();
        if (parent == null) return;
        if (!"_noconversation".equals(parent.getFileName().toString())) return; // only relocate temp media
        try {
            Path newPath = uniqueTargetPath(current, targetDir);
            Files.move(current, newPath, StandardCopyOption.REPLACE_EXISTING);
            part.setFilePath(newPath.toString());
            movedPaths.put(fp, newPath.toString());
            movedPaths.put(current.toString(), newPath.toString());
            relocateThumbnail(current, targetDir);
        } catch (Exception ex) {
            log.warn("Failed to relocate media part {}", current, ex);
        }
    }

    /** The message's media JSON stores the same file paths as its parts; keep them in sync. */
    @SuppressWarnings("unchecked")
    private void updateMediaJson(Message msg, Map<String, String> movedPaths) {
        Map<String, Object> media = msg.getMedia();
        if (media == null) return;
        Object partsObj = media.get("parts");
        if (!(partsObj instanceof List<?> entries)) return;
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> m && m.get("filePath") instanceof String oldPath) {
                String newPath = movedPaths.get(oldPath);
                if (newPath != null) {
                    ((Map<String, Object>) m).put("filePath", newPath);
                }
            }
        }
    }

    private Path uniqueTargetPath(Path current, Path targetDir) {
        Path newPath = targetDir.resolve(current.getFileName());
        if (Files.exists(newPath)) {
            String baseName = current.getFileName().toString();
            int dotIdx = baseName.lastIndexOf('.');
            String stem = dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName;
            String ext = dotIdx > 0 ? baseName.substring(dotIdx) : "";
            newPath = targetDir.resolve(stem + "_" + UUID.randomUUID() + ext);
        }
        return newPath;
    }

    private void relocateThumbnail(Path current, Path targetDir) {
        try {
            Path oldThumb = thumbnailService.deriveStemThumbnail(current);
            if (!Files.exists(oldThumb)) return;
            Path relocated = targetDir.resolve(oldThumb.getFileName());
            if (Files.exists(relocated)) {
                String baseName = oldThumb.getFileName().toString();
                int dotIdx = baseName.lastIndexOf('.');
                String stem = dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName;
                relocated = targetDir.resolve(stem + "_" + UUID.randomUUID() + ".jpg");
            }
            try { Files.move(oldThumb, relocated, StandardCopyOption.REPLACE_EXISTING); }
            catch (Exception ex) { log.warn("Failed moving thumbnail {}", oldThumb, ex); }
        } catch (Exception ignore) { }
    }
}

