package com.joshfouchey.smsarchive.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Service responsible for creating and managing media thumbnails.
 * Extracted from ImportService to enable reuse across import and rebuild operations.
 */
@Slf4j
@Service
public class ThumbnailService {

    private static final Set<String> SUPPORTED_THUMB_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/bmp"
    );

    private static final Set<String> UNSUPPORTED_IMAGE_TYPES = Set.of(
            "image/heic", "image/heif"
    );

    private static final int THUMB_SIZE = 400;
    private static final double THUMB_QUALITY = 0.80;

    /**
     * Check if content type is supported for thumbnail generation.
     */
    public boolean isSupported(String contentType) {
        if (contentType == null) return false;
        return SUPPORTED_THUMB_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * Check if content type is an unsupported image that needs a placeholder.
     */
    public boolean isUnsupportedNeedsPlaceholder(String contentType) {
        if (contentType == null) return false;
        return UNSUPPORTED_IMAGE_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * Create a thumbnail for the given original image file.
     *
     * @param original    Path to the original image file
     * @param thumbDest   Path where thumbnail should be saved
     * @param contentType MIME type of the original image
     * @param force       If true, regenerate even if thumbnail exists
     * @return true if thumbnail was created or already exists, false otherwise
     */
    public boolean createThumbnail(Path original, Path thumbDest, String contentType, boolean force) {
        try {
            // Check if original exists
            if (!Files.exists(original)) {
                log.warn("Original file does not exist: {}", original);
                return false;
            }

            // Skip if thumbnail exists and not forcing
            if (Files.exists(thumbDest) && !force) {
                log.debug("Thumbnail already exists, skipping: {}", thumbDest);
                return true;
            }

            // Handle supported image types
            if (isSupported(contentType)) {
                Thumbnails.of(original.toFile())
                        .size(THUMB_SIZE, THUMB_SIZE)
                        .outputFormat("jpg")
                        .outputQuality(THUMB_QUALITY)
                        .toFile(thumbDest.toFile());
                log.debug("Created thumbnail: {}", thumbDest);
                return true;
            }

            // Handle unsupported image types (HEIC/HEIF)
            if (isUnsupportedNeedsPlaceholder(contentType)) {
                if (!Files.exists(thumbDest) || force) {
                    createUnsupportedPlaceholder(thumbDest, "HEIC");
                    log.debug("Created placeholder thumbnail: {}", thumbDest);
                    return true;
                }
            }

            log.debug("Content type not supported for thumbnails: {}", contentType);
            return false;

        } catch (Exception e) {
            log.warn("Failed to create thumbnail for {}: {}", original, e.getMessage());
            return false;
        }
    }

    /**
     * Create a placeholder thumbnail for unsupported image formats.
     *
     * @param thumbDest Path where placeholder should be saved
     * @param label     Text label to display on placeholder (e.g., "HEIC")
     */
    public void createUnsupportedPlaceholder(Path thumbDest, String label) {
        try {
            int w = THUMB_SIZE;
            int h = THUMB_SIZE;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();

            // Dark gray background
            g.setColor(Color.DARK_GRAY);
            g.fillRect(0, 0, w, h);

            // White text
            g.setColor(Color.WHITE);

            // Main label (large, bold)
            g.setFont(new Font("SansSerif", Font.BOLD, 36));
            int tw = g.getFontMetrics().stringWidth(label);
            g.drawString(label, (w - tw) / 2, h / 2 - 10);

            // Subtitle (smaller, plain)
            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            String sub = "Not Supported";
            int sw = g.getFontMetrics().stringWidth(sub);
            g.drawString(sub, (w - sw) / 2, h / 2 + 25);

            g.dispose();

            // Write to file
            ImageIO.write(img, "jpg", thumbDest.toFile());
            log.debug("Created unsupported placeholder: {}", thumbDest);

        } catch (Exception e) {
            log.warn("Failed to create unsupported thumbnail placeholder: {}", e.getMessage());
        }
    }

    /**
     * Derive the thumbnail path from an original file path using the new naming convention.
     *
     * @param originalPath Path to the original file
     * @return Path where thumbnail should be stored
     */
    public Path deriveStemThumbnail(Path originalPath) {
        if (originalPath == null || originalPath.getParent() == null) {
            throw new IllegalArgumentException("Invalid original path");
        }
        String fileName = originalPath.getFileName().toString();
        int dotIdx = fileName.lastIndexOf('.');
        String stem = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
        return originalPath.getParent().resolve(stem + "_thumb.jpg");
    }
}
