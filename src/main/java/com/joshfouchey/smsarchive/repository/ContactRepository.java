package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    // Single lookup
    Optional<Contact> findByNormalizedNumber(String normalizedNumber);

    // Batch lookup (optional helper)
    List<Contact> findByNormalizedNumberIn(Collection<String> normalizedNumbers);
}