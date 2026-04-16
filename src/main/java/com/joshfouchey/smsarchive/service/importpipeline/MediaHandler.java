package com.joshfouchey.smsarchive.service.importpipeline;

import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.service.MediaFileNamer;
import com.joshfouchey.smsarchive.service.ThumbnailService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class MediaHandler {

    private static final Map<String, String> CONTENT_TYPE_EXT_MAP = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/jpg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/bmp", ".bmp"),
            Map.entry("image/heic", ".heic"),
            Map.entry("image/heif", ".heic"),
            Map.entry("video/mp4", ".mp4"),
            Map.entry("video/3gpp", ".3gp"),
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("audio/ogg", ".ogg"),
            Map.entry("text/plain", ".txt")
    );

    private final ThumbnailService thumbnailService;
    private final Path mediaRoot;

    public MediaHandler(ThumbnailService thumbnailService, Path mediaRoot) {
        this.thumbnailService = thumbnailService;
        this.mediaRoot = mediaRoot;
    }

    public Optional<String> saveMediaPart(String base64, MessagePart part) {
        try {
            if (!isValidBase64(base64)) { log.error("Invalid Base64 input: {}", base64); return Optional.empty(); }
            Message message = part.getMessage();
            String conversationDirName = (message.getConversation() != null && message.getConversation().getId() != null)
                    ? message.getConversation().getId().toString() : "_noconversation";
            Path dir = mediaRoot.resolve(conversationDirName);
            Files.createDirectories(dir);
            byte[] dataBytes = Base64.getDecoder().decode(base64);
            String ext = guessExtension(part.getContentType(), part.getName());
            Path original = MediaFileNamer.buildUniqueMediaPath(dir, message.getId(), part.getSeq(), message.getTimestamp(), dataBytes, ext);
            Files.write(original, dataBytes);
            ensureThumbnail(original, part.getContentType(), false);
            part.setFilePath(original.toString());
            safeSetSize(part, original);
            return Optional.of(original.toString());
        } catch (Exception e) { log.error("Media save failed", e); return Optional.empty(); }
    }

    public void ensureThumbnail(Path original, String contentType, boolean allowExisting) {
        try {
            Path thumb = thumbnailService.deriveStemThumbnail(original);
            if (Files.exists(thumb) && allowExisting) return;
            thumbnailService.createThumbnail(original, thumb, contentType, true);
        } catch (Exception ex) {
            log.warn("Thumbnail generation failed for {}", original, ex);
        }
    }

    public void safeSetSize(MessagePart part, Path path) {
        try {
            if (Files.exists(path)) {
                part.setSizeBytes(Files.size(path));
            }
        } catch (Exception ex) {
            log.debug("Unable to determine size for media part at {}: {}", path, ex.getMessage());
        }
    }

    public String guessExtension(String contentType, String name) {
        if (StringUtils.isNotBlank(name) && name.contains(".")) {
            String ext = StringUtils.substringAfterLast(name, ".");
            if (ext.length() <= 6) return "." + ext.toLowerCase();
        }
        if (StringUtils.isBlank(contentType)) return ".bin";
        String lower = contentType.toLowerCase();
        return CONTENT_TYPE_EXT_MAP.getOrDefault(lower, ".bin");
    }

    public boolean isValidBase64(String base64) {
        try { Base64.getDecoder().decode(base64); return true; } catch (IllegalArgumentException _) { return false; }
    }
}
