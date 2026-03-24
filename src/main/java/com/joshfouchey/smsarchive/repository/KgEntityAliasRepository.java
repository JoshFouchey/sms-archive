package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.KgEntityAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KgEntityAliasRepository extends JpaRepository<KgEntityAlias, Long> {

    List<KgEntityAlias> findByEntityId(Long entityId);

    Optional<KgEntityAlias> findByEntityIdAndAlias(Long entityId, String alias);
}
