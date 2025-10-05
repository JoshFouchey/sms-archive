package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.MessagePart;
import com.joshfouchey.smsarchive.repository.MessagePartRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class MediaService {
    private final MessagePartRepository partRepo;

    public MediaService(MessagePartRepository partRepo) {
        this.partRepo = partRepo;
    }

    public Page<MessagePart> getImages(String contact, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);

        if (contact == null || contact.isBlank()) {
            return partRepo.findAllImages(pageable);
        }
        return partRepo.findImagesByContact(contact, pageable);
    }



    public boolean deleteImage(Long id) {
        Optional<MessagePart> opt = partRepo.findById(id);
        if (opt.isEmpty()) return false;

        MessagePart part = opt.get();
        if (part.getFilePath() != null) {
            try {
                Files.deleteIfExists(Path.of(part.getFilePath()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        partRepo.delete(part);
        return true;
    }
}

