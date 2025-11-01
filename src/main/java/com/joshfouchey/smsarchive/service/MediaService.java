package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import com.joshfouchey.smsarchive.repository.MessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityNotFoundException;

@Service
public class MediaService {
    private final MessagePartRepository partRepo;
    private final ContactRepository contactRepo;
    private final MessageRepository messageRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ThumbnailService thumbnailService; // Added for thumbnail path derivation
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    public MediaService(MessagePartRepository partRepo, ContactRepository contactRepo, MessageRepository messageRepository, CurrentUserProvider currentUserProvider, ThumbnailService thumbnailService) {
        this.partRepo = partRepo;
        this.contactRepo = contactRepo;
        this.messageRepository = messageRepository;
        this.currentUserProvider = currentUserProvider;
        this.thumbnailService = thumbnailService;
    }

    // Uses repository methods only; throws if contact not found
    public Page<MessagePart> getImages(Long contactId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        var user = currentUserProvider.getCurrentUser();
        if (contactId != null) {
            if (!contactRepo.existsById(contactId)) {
                throw new EntityNotFoundException("Contact not found: " + contactId);
            }
            return partRepo.findImagesByContactId(contactId, user, pageable);
        }
        return partRepo.findAllImages(user, pageable);
    }

    public boolean deleteImage(Long id) {
        Optional<MessagePart> opt = partRepo.findById(id);
        if (opt.isEmpty()) return false;

        MessagePart part = opt.get();
        // Delete original file
        if (part.getFilePath() != null) {
            Path originalPath = Path.of(part.getFilePath());
            try {
                Files.deleteIfExists(originalPath);
            } catch (IOException e) {
                log.error("Failed deleting image file {}", part.getFilePath(), e);
            }
            // Attempt to delete associated thumbnail(s)
            Integer seq = part.getSeq();
            Path parent = originalPath.getParent();
            if (seq != null && parent != null) {
                // Standard derived thumbnail path
                try {
                    Path derivedThumb = thumbnailService.deriveThumbnailPath(originalPath, seq);
                    if (Files.exists(derivedThumb)) {
                        try {
                            Files.deleteIfExists(derivedThumb);
                            log.debug("Deleted thumbnail {}", derivedThumb);
                        } catch (IOException ex) {
                            log.warn("Failed deleting thumbnail {}", derivedThumb, ex);
                        }
                    }
                } catch (Exception ex) {
                    // deriveThumbnailPath may throw if original invalid; ignore
                }
                // Delete any variant thumbnails (e.g., part{seq}_thumb_<UUID>.jpg)
                String glob = "part" + seq + "_thumb*.jpg";
                try (var stream = Files.newDirectoryStream(parent, glob)) {
                    for (Path p : stream) {
                        if (Files.isRegularFile(p)) {
                            try {
                                Files.deleteIfExists(p);
                                log.debug("Deleted thumbnail variant {}", p);
                            } catch (IOException ex) {
                                log.warn("Failed deleting thumbnail variant {}", p, ex);
                            }
                        }
                    }
                } catch (IOException dirEx) {
                    log.debug("No thumbnail variants found for seq {} in {}: {}", seq, parent, dirEx.getMessage());
                }
            }
        }

        // Delete the associated message if it exists
        if (part.getMessage() != null) {
            messageRepository.deleteById(part.getMessage().getId());
        }

        partRepo.delete(part);
        return true;
    }
}
