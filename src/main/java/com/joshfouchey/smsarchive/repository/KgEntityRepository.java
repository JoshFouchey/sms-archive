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

    /**
     * Find entities with similar names using pg_trgm trigram similarity.
     * Returns entities whose canonical_name is similar to the given name
     * (same entity type, same user). Used for entity disambiguation —
     * e.g., "Tom" vs "Tommy", "John Smith" vs "John S."
     */
    @Query(value = """
            SELECT e.* FROM kg_entities e
            WHERE e.user_id = :userId
              AND e.entity_type = :entityType
              AND e.canonical_name != :name
              AND similarity(e.canonical_name, :name) > :threshold
            ORDER BY similarity(e.canonical_name, :name) DESC
            LIMIT 5
            """, nativeQuery = true)
    List<KgEntity> findSimilarEntities(
            @Param("userId") java.util.UUID userId,
            @Param("name") String name,
            @Param("entityType") String entityType,
            @Param("threshold") float threshold);

    /**
     * Also check aliases for fuzzy match — "Dad" might be aliased to "John Smith".
     */
    @Query(value = """
            SELECT DISTINCT e.* FROM kg_entities e
            JOIN kg_entity_aliases a ON a.entity_id = e.id
            WHERE e.user_id = :userId
              AND e.entity_type = :entityType
              AND (similarity(a.alias, :name) > :threshold
                   OR similarity(e.canonical_name, :name) > :threshold)
            ORDER BY e.canonical_name
            LIMIT 5
            """, nativeQuery = true)
    List<KgEntity> findSimilarByNameOrAlias(
            @Param("userId") java.util.UUID userId,
            @Param("name") String name,
            @Param("entityType") String entityType,
            @Param("threshold") float threshold);
}
