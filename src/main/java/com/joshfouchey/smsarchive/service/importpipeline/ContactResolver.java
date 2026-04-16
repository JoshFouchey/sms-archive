package com.joshfouchey.smsarchive.service.importpipeline;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.joshfouchey.smsarchive.model.Contact;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.ContactRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Slf4j
public class ContactResolver {

    private static final String UNKNOWN_NORMALIZED = "__unknown__";
    private static final String UNKNOWN_NUMBER_DISPLAY = "unknown";
    private static final Set<String> GROUP_KEYWORDS = Set.of("group", "team", "vacation", "chat");

    private final ContactRepository contactRepo;
    private final Cache<String, Contact> contactCache;

    public ContactResolver(ContactRepository contactRepo) {
        this.contactRepo = contactRepo;
        this.contactCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
    }

    public Contact resolveContact(User user, String number, String suggestedName) {
        String normalized = normalizeNumber(number);
        String cacheKey = user.getId() + "|" + normalized;
        Contact cached = contactCache.getIfPresent(cacheKey);
        if (cached != null) {
            if (shouldUpdateContactName(cached, suggestedName)) {
                String sanitized = sanitizeContactName(suggestedName);
                cached.setName(sanitized);
                cached = contactRepo.save(cached);
                contactCache.put(cacheKey, cached);
                log.debug("Updated contact {} name", cached.getId());
            }
            return cached;
        }
        Contact contact = contactRepo.findByUserAndNormalizedNumber(user, normalized).orElseGet(() -> {
            try {
                Contact c = new Contact();
                c.setUser(user);
                c.setNumber(number == null ? UNKNOWN_NUMBER_DISPLAY : number);
                c.setNormalizedNumber(normalized);
                String sanitizedName = sanitizeContactName(suggestedName);
                c.setName(sanitizedName != null ? sanitizedName : c.getNumber());
                return contactRepo.save(c);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                return contactRepo.findByUserAndNormalizedNumber(user, normalized)
                        .orElseThrow(() -> new RuntimeException("Failed to find or create contact for: " + normalized));
            }
        });
        if (shouldUpdateContactName(contact, suggestedName)) {
            String sanitized = sanitizeContactName(suggestedName);
            contact.setName(sanitized);
            contact = contactRepo.save(contact);
        }
        contactCache.put(cacheKey, contact);
        return contact;
    }

    public String normalizeNumber(String number) {
        if (number == null || number.isBlank()) {
            return UNKNOWN_NORMALIZED;
        }
        String digits = number.replaceAll("\\D", "");
        if (digits.isEmpty()) return UNKNOWN_NORMALIZED;

        // NANP canonicalization: always 11 digits with leading 1
        if (digits.length() == 10) {
            digits = "1" + digits;
        }
        // Add + prefix to match database standard (E.164 format)
        return "+" + digits;
    }

    public String sanitizeContactName(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        if (trimmed.equalsIgnoreCase(UNKNOWN_NUMBER_DISPLAY) ||
            trimmed.equalsIgnoreCase("(" + UNKNOWN_NUMBER_DISPLAY + ")") ||
            trimmed.matches("^\\(.*" + UNKNOWN_NUMBER_DISPLAY + ".*\\)$")) return null;
        if (isGroupLikeName(trimmed)) return null;
        return trimmed;
    }

    private boolean shouldUpdateContactName(Contact contact, String suggestedName) {
        String sanitizedSuggested = sanitizeContactName(suggestedName);
        if (sanitizedSuggested == null) return false;
        String currentName = contact.getName();
        if (currentName == null || currentName.isBlank()) return true;
        if (currentName.equalsIgnoreCase(UNKNOWN_NUMBER_DISPLAY)) return true;
        String currentNameNormalized = normalizeNumber(currentName);
        return currentNameNormalized.equals(contact.getNormalizedNumber());
    }

    public boolean isGroupLikeName(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.split("\\s+").length < 2) return false;
        for (String kw : GROUP_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }
}
