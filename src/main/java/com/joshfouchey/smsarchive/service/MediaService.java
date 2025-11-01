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
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    public MediaService(MessagePartRepository partRepo, ContactRepository contactRepo, MessageRepository messageRepository, CurrentUserProvider currentUserProvider) {
        this.partRepo = partRepo;
        this.contactRepo = contactRepo;
        this.messageRepository = messageRepository;
        this.currentUserProvider = currentUserProvider;
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
        if (part.getFilePath() != null) {
            Path originalPath = Path.of(part.getFilePath());
            try { Files.deleteIfExists(originalPath); } catch (IOException e) { log.error("Failed deleting image file {}", part.getFilePath(), e); }
            Path parent = originalPath.getParent();
            if (parent != null) {
                String fileName = originalPath.getFileName().toString();
                int dotIdx = fileName.lastIndexOf('.');
                String stem = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
                Path thumb = parent.resolve(stem + "_thumb.jpg");
                if (Files.exists(thumb)) {
                    try { Files.deleteIfExists(thumb); log.debug("Deleted thumbnail {}", thumb); } catch (IOException ex) { log.warn("Failed deleting thumbnail {}", thumb, ex); }
                }
            }
        }
        if (part.getMessage() != null) { messageRepository.deleteById(part.getMessage().getId()); }
        partRepo.delete(part);
        return true;
    }
}
