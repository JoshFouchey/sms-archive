package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.ContactDto;
import com.joshfouchey.smsarchive.mapper.ContactMapper;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class ContactService {

    private final ContactRepository contactRepository;

    public ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable("distinctContacts")
    public List<ContactDto> getAllDistinctContacts() {
        return contactRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((com.joshfouchey.smsarchive.model.Contact c) -> c.getName() == null) // non-null first
                        .thenComparing(c -> c.getName() == null ? "" : c.getName().toLowerCase())
                        .thenComparing(com.joshfouchey.smsarchive.model.Contact::getNormalizedNumber))
                .map(ContactMapper::toDto)
                .toList();
    }
}
