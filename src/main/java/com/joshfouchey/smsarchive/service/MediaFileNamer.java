package com.joshfouchey.smsarchive.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Utility responsible for generating unique media file names.
 * Pattern: msg-{msgId}-seq{seq}-{epochSeconds}-{hash8}[optional -N]{ext}
 */
public final class MediaFileNamer {
    private MediaFileNamer() {}

    public static Path buildUniqueMediaPath(Path dir, Long messageId, Integer seq, Instant timestamp, byte[] dataBytes, String ext) {
        if (dir == null) throw new IllegalArgumentException("dir required");
        if (seq == null) seq = 0;
        if (ext == null || ext.isBlank()) ext = ".bin";
        String msgIdStr = messageId == null ? "temp" : messageId.toString();
        long epoch = (timestamp != null ? timestamp.getEpochSecond() : Instant.now().getEpochSecond());
        String hashShort = shortHash(dataBytes);
        String base = "msg-" + msgIdStr + "-seq" + seq + "-" + epoch + "-" + hashShort;
        Path candidate = dir.resolve(base + ext);
        int counter = 1;
        while (Files.exists(candidate) && counter <= 5) {
            candidate = dir.resolve(base + "-" + counter + ext);
            counter++;
        }
        // Last resort if still exists (extremely unlikely) append UUID
        if (Files.exists(candidate)) {
            candidate = dir.resolve(base + "-" + UUID.randomUUID() + ext);
        }
        return candidate;
    }

    private static String shortHash(byte[] dataBytes) {
        if (dataBytes == null || dataBytes.length == 0) return "00000000";
        int sliceLen = Math.min(dataBytes.length, 65536);
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(sliceLen == dataBytes.length ? dataBytes : slice(dataBytes, sliceLen));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private static byte[] slice(byte[] data, int len) {
        byte[] out = new byte[len];
        System.arraycopy(data, 0, out, 0, len);
        return out;
    }
}

