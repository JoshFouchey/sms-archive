package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.KgEntity;
import com.joshfouchey.smsarchive.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KgEntityRepository extends JpaRepository<KgEntity, Long> {

    List<KgEntity> findByUserOrderByCanonicalName(User user);

    List<KgEntity> findByUserAndEntityTypeOrderByCanonicalName(User user, String entityType);

    Optional<KgEntity> findByIdAndUser(Long id, User user);

    Optional<KgEntity> findByUserAndCanonicalNameAndEntityType(User user, String canonicalName, String entityType);

    @Query(value = """
            SELECT e.* FROM kg_entities e
            WHERE e.user_id = :userId
              AND (e.canonical_name ILIKE '%' || :search || '%'
                   OR e.id IN (
                       SELECT a.entity_id FROM kg_entity_aliases a
                       WHERE a.alias ILIKE '%' || :search || '%'
                   ))
            ORDER BY e.canonical_name
            """, nativeQuery = true)
    List<KgEntity> searchByNameOrAlias(@Param("userId") java.util.UUID userId, @Param("search") String search);

    long countByUser(User user);
}
