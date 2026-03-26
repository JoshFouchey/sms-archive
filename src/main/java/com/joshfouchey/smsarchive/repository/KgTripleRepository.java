package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.KgTriple;
import com.joshfouchey.smsarchive.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KgTripleRepository extends JpaRepository<KgTriple, Long> {

    List<KgTriple> findBySubjectIdOrderByCreatedAtDesc(Long subjectId);

    List<KgTriple> findByObjectIdOrderByCreatedAtDesc(Long objectId);

    @Query("SELECT t FROM KgTriple t WHERE t.subject.id = :entityId OR t.object.id = :entityId ORDER BY t.createdAt DESC")
    List<KgTriple> findByEntityId(@Param("entityId") Long entityId);

    @Query(value = """
            SELECT t.* FROM kg_triples t
            WHERE t.user_id = :userId
              AND t.predicate = :predicate
            ORDER BY t.created_at DESC
            """, nativeQuery = true)
    List<KgTriple> findByUserAndPredicate(
            @Param("userId") java.util.UUID userId,
            @Param("predicate") String predicate);

    @Query(value = """
            SELECT t.* FROM kg_triples t
            WHERE t.user_id = :userId
            ORDER BY t.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<KgTriple> findRecentByUser(
            @Param("userId") java.util.UUID userId,
            @Param("limit") int limit);

    @Modifying
    @Query("UPDATE KgTriple t SET t.subject.id = :newId WHERE t.subject.id = :oldId")
    int updateSubjectEntity(@Param("oldId") Long oldId, @Param("newId") Long newId);

    @Modifying
    @Query("UPDATE KgTriple t SET t.object.id = :newId WHERE t.object.id = :oldId")
    int updateObjectEntity(@Param("oldId") Long oldId, @Param("newId") Long newId);

    long countByUser(User user);

    /**
     * Find by fact hash for exact dedup (same subject+predicate+object).
     */
    @Query("SELECT t FROM KgTriple t WHERE t.user = :user AND t.factHash = :factHash")
    Optional<KgTriple> findByUserAndFactHash(
            @Param("user") User user,
            @Param("factHash") String factHash);

    /**
     * Find active facts for the same subject+predicate — used for soft conflict detection.
     * If Tom lives_in NYC and a new fact says Tom lives_in Chicago, these are in conflict.
     */
    @Query("""
            SELECT t FROM KgTriple t
            WHERE t.user = :user AND t.subject = :subject AND t.predicate = :predicate
              AND t.status = 'ACTIVE'
            """)
    List<KgTriple> findActiveBySubjectAndPredicate(
            @Param("user") User user,
            @Param("subject") com.joshfouchey.smsarchive.model.KgEntity subject,
            @Param("predicate") String predicate);

    /**
     * Get the next conflict cluster ID (max + 1).
     */
    @Query(value = "SELECT COALESCE(MAX(conflict_cluster_id), 0) + 1 FROM kg_triples WHERE user_id = :userId",
            nativeQuery = true)
    Long nextConflictClusterId(@Param("userId") java.util.UUID userId);

    @Query("""
            SELECT t FROM KgTriple t
            WHERE t.user = :user AND t.subject = :subject AND t.predicate = :predicate
              AND (:object IS NULL AND t.object IS NULL OR t.object = :object)
              AND (:objectValue IS NULL AND t.objectValue IS NULL OR t.objectValue = :objectValue)
            """)
    Optional<KgTriple> findByUserAndSubjectAndPredicateAndObjectOrValue(
            @Param("user") User user,
            @Param("subject") com.joshfouchey.smsarchive.model.KgEntity subject,
            @Param("predicate") String predicate,
            @Param("object") com.joshfouchey.smsarchive.model.KgEntity object,
            @Param("objectValue") String objectValue);
}
