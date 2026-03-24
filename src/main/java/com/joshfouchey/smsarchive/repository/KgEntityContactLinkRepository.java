package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.KgEntityContactLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KgEntityContactLinkRepository extends JpaRepository<KgEntityContactLink, KgEntityContactLink.KgEntityContactLinkId> {

    List<KgEntityContactLink> findByEntityId(Long entityId);

    List<KgEntityContactLink> findByContactId(Long contactId);
}
