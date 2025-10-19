package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import com.joshfouchey.smsarchive.repository.ContactRepository;
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
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    public MediaService(MessagePartRepository partRepo, ContactRepository contactRepo) {
        this.partRepo = partRepo;
        this.contactRepo = contactRepo;
    }

    // Uses repository methods only; throws if contact not found
    public Page<MessagePart> getImages(Long contactId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        if (contactId != null) {
            if (!contactRepo.existsById(contactId)) {
                throw new EntityNotFoundException("Contact not found: " + contactId);
            }
            return partRepo.findImagesByContactId(contactId, pageable);
        }
        return partRepo.findAllImages(pageable);
    }

    public boolean deleteImage(Long id) {
        Optional<MessagePart> opt = partRepo.findById(id);
        if (opt.isEmpty()) return false;

        MessagePart part = opt.get();
        if (part.getFilePath() != null) {
            try {
                Files.deleteIfExists(Path.of(part.getFilePath()));
            } catch (IOException e) {
                log.error("Failed deleting image file {}", part.getFilePath(), e);
            }
        }
        partRepo.delete(part);
        return true;
    }
}
