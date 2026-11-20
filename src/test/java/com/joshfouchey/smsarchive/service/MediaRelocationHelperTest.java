package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Message;
import com.joshfouchey.smsarchive.model.MessagePart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaRelocationHelperTest {

    @TempDir Path mediaRoot;

    private MediaRelocationHelper helper() {
        ThumbnailService thumbs = mock(ThumbnailService.class);
        when(thumbs.deriveStemThumbnail(any())).thenAnswer(inv -> {
            Path p = inv.getArgument(0);
            String name = p.getFileName().toString();
            return p.resolveSibling(name.substring(0, name.lastIndexOf('.')) + "_thumb.jpg");
        });
        return new MediaRelocationHelper(thumbs, mediaRoot);
    }

    private Message messageWith(Path... files) {
        Message msg = new Message();
        List<MessagePart> parts = new ArrayList<>();
        for (Path f : files) {
            MessagePart part = new MessagePart();
            part.setFilePath(f.toString());
            parts.add(part);
        }
        msg.setParts(parts);
        return msg;
    }

    private Path touch(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "x");
    }

    @Test
    void discardDeletesTempMediaAndThumbnail() throws Exception {
        Path media = touch(mediaRoot.resolve("_noconversation/msg-temp-seq1-1-abc.jpg"));
        Path thumb = touch(mediaRoot.resolve("_noconversation/msg-temp-seq1-1-abc_thumb.jpg"));
        Message msg = messageWith(media);

        helper().discard(msg);

        assertThat(media).doesNotExist();
        assertThat(thumb).doesNotExist();
        assertThat(msg.getParts().get(0).getFilePath()).isNull();
    }

    @Test
    void discardNeverTouchesMediaOutsideNoConversation() throws Exception {
        Path canonical = touch(mediaRoot.resolve("42/msg-7-seq1-1-abc.jpg"));
        Path thumb = touch(mediaRoot.resolve("42/msg-7-seq1-1-abc_thumb.jpg"));
        Message msg = messageWith(canonical);

        helper().discard(msg);

        assertThat(canonical).exists();
        assertThat(thumb).exists();
        assertThat(msg.getParts().get(0).getFilePath()).isEqualTo(canonical.toString());
    }
}
