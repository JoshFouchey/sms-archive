package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.dto.KgEntityDto;
import com.joshfouchey.smsarchive.dto.KgTripleDto;
import com.joshfouchey.smsarchive.dto.KnowledgeGraphDto;
import com.joshfouchey.smsarchive.dto.KnowledgeGraphDto.GraphEdge;
import com.joshfouchey.smsarchive.dto.KnowledgeGraphDto.GraphNode;
import com.joshfouchey.smsarchive.model.KgEntity;
import com.joshfouchey.smsarchive.model.KgEntityAlias;
import com.joshfouchey.smsarchive.model.KgEntityContactLink;
import com.joshfouchey.smsarchive.model.KgTriple;
import com.joshfouchey.smsarchive.model.User;
import com.joshfouchey.smsarchive.repository.KgEntityAliasRepository;
import com.joshfouchey.smsarchive.repository.KgEntityContactLinkRepository;
import com.joshfouchey.smsarchive.repository.KgEntityRepository;
import com.joshfouchey.smsarchive.repository.KgTripleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;

@Slf4j
@Service
public class KnowledgeGraphService {

    private final KgEntityRepository entityRepository;
    private final KgTripleRepository tripleRepository;
    private final KgEntityAliasRepository aliasRepository;
    private final KgEntityContactLinkRepository contactLinkRepository;

    public KnowledgeGraphService(KgEntityRepository entityRepository,
                                 KgTripleRepository tripleRepository,
                                 KgEntityAliasRepository aliasRepository,
                                 KgEntityContactLinkRepository contactLinkRepository) {
        this.entityRepository = entityRepository;
        this.tripleRepository = tripleRepository;
        this.aliasRepository = aliasRepository;
        this.contactLinkRepository = contactLinkRepository;
    }

    @Cacheable(value = "kgEntities", key = "#user.id + '-' + #type + '-' + #search")
    @Transactional(readOnly = true)
    public List<KgEntityDto> getEntities(User user, String type, String search) {
        List<KgEntity> entities;
        if (search != null && !search.isBlank()) {
            entities = entityRepository.searchByNameOrAlias(user.getId(), search);
            if (type != null && !type.isBlank()) {
                entities = entities.stream()
                        .filter(e -> type.equalsIgnoreCase(e.getEntityType()))
                        .toList();
            }
        } else if (type != null && !type.isBlank()) {
            entities = entityRepository.findByUserAndEntityTypeOrderByCanonicalName(user, type);
        } else {
            entities = entityRepository.findByUserOrderByCanonicalName(user);
        }
        return entities.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public KgEntityDto getEntity(User user, Long entityId) {
        KgEntity entity = entityRepository.findByIdAndUser(entityId, user)
                .orElseThrow(() -> new NoSuchElementException("Entity not found"));
        return toDto(entity);
    }

    @Cacheable(value = "kgEntityFacts", key = "#entityId")
    @Transactional(readOnly = true)
    public List<KgTripleDto> getEntityFacts(User user, Long entityId) {
        entityRepository.findByIdAndUser(entityId, user)
                .orElseThrow(() -> new NoSuchElementException("Entity not found"));
        List<KgTriple> triples = tripleRepository.findByEntityId(entityId);
        return triples.stream().map(this::toTripleDto).toList();
    }

    @Transactional(readOnly = true)
    public List<KgTripleDto> getRecentTriples(User user, int limit) {
        List<KgTriple> triples = tripleRepository.findRecentByUser(user.getId(), limit);
        return triples.stream().map(this::toTripleDto).toList();
    }

    @Transactional(readOnly = true)
    public List<KgTripleDto> getContactFacts(User user, Long contactId) {
        List<KgEntityContactLink> links = contactLinkRepository.findByContactId(contactId);
        if (links.isEmpty()) return List.of();

        List<KgTripleDto> allFacts = new ArrayList<>();
        for (KgEntityContactLink link : links) {
            KgEntity entity = link.getEntity();
            if (!entity.getUser().getId().equals(user.getId())) continue;
            List<KgTriple> triples = tripleRepository.findByEntityId(entity.getId());
            triples.stream().map(this::toTripleDto).forEach(allFacts::add);
        }
        return allFacts;
    }

    @Transactional(readOnly = true)
    public List<KgTripleDto> getTriplesByPredicate(User user, String predicate) {
        List<KgTriple> triples = tripleRepository.findByUserAndPredicate(user.getId(), predicate);
        return triples.stream().map(this::toTripleDto).toList();
    }

    @Cacheable(value = "kgGraph", key = "#user.id + '-' + #centeredEntityId + '-' + #depth + '-' + #maxNodes")
    @Transactional(readOnly = true)
    public KnowledgeGraphDto getGraph(User user, Long centeredEntityId, int depth, int maxNodes) {
        Map<Long, KgEntity> entityMap = new HashMap<>();
        List<KgTriple> graphTriples = new ArrayList<>();

        if (centeredEntityId != null) {
            KgEntity center = entityRepository.findByIdAndUser(centeredEntityId, user)
                    .orElseThrow(() -> new NoSuchElementException("Entity not found"));
            entityMap.put(center.getId(), center);

            Queue<Long> frontier = new LinkedList<>();
            frontier.add(center.getId());
            Set<Long> visited = new HashSet<>();
            visited.add(center.getId());

            for (int d = 0; d < depth && !frontier.isEmpty() && entityMap.size() < maxNodes; d++) {
                List<Long> currentLevel = new ArrayList<>(frontier);
                frontier.clear();
                for (Long eid : currentLevel) {
                    List<KgTriple> triples = tripleRepository.findByEntityId(eid);
                    for (KgTriple t : triples) {
                        graphTriples.add(t);
                        addEntityIfNew(t.getSubject(), entityMap, visited, frontier, maxNodes);
                        if (t.getObject() != null) {
                            addEntityIfNew(t.getObject(), entityMap, visited, frontier, maxNodes);
                        }
                    }
                }
            }
        } else {
            List<KgEntity> allEntities = entityRepository.findByUserOrderByCanonicalName(user);
            for (KgEntity e : allEntities) {
                if (entityMap.size() >= maxNodes) break;
                entityMap.put(e.getId(), e);
            }
            for (Long eid : new ArrayList<>(entityMap.keySet())) {
                graphTriples.addAll(tripleRepository.findByEntityId(eid));
            }
        }

        Map<Long, Long> linkedContactMap = new HashMap<>();
        for (Long eid : entityMap.keySet()) {
            List<KgEntityContactLink> links = contactLinkRepository.findByEntityId(eid);
            if (!links.isEmpty()) {
                linkedContactMap.put(eid, links.getFirst().getContact().getId());
            }
        }

        List<GraphNode> nodes = entityMap.values().stream()
                .map(e -> new GraphNode(
                        e.getId().toString(),
                        e.getCanonicalName(),
                        e.getEntityType(),
                        linkedContactMap.get(e.getId())))
                .toList();

        Set<String> seenEdges = new HashSet<>();
        List<GraphEdge> edges = new ArrayList<>();
        for (KgTriple t : graphTriples) {
            String target = t.getObject() != null ? t.getObject().getId().toString() : t.getObjectValue();
            if (target == null) continue;
            String source = t.getSubject().getId().toString();
            String edgeKey = source + "-" + t.getPredicate() + "-" + target;
            if (!seenEdges.add(edgeKey)) continue;
            if (!entityMap.containsKey(t.getSubject().getId())) continue;
            if (t.getObject() != null && !entityMap.containsKey(t.getObject().getId())) continue;
            edges.add(new GraphEdge(source, target, t.getPredicate(), t.getConfidence()));
        }

        return new KnowledgeGraphDto(nodes, edges);
    }

    @CacheEvict(value = {"kgEntities", "kgEntityFacts", "kgGraph", "kgStats"}, allEntries = true)
    @Transactional
    public KgEntityDto mergeEntities(User user, Long primaryId, Long mergeFromId) {
        KgEntity primary = entityRepository.findByIdAndUser(primaryId, user)
                .orElseThrow(() -> new NoSuchElementException("Primary entity not found"));
        KgEntity mergeFrom = entityRepository.findByIdAndUser(mergeFromId, user)
                .orElseThrow(() -> new NoSuchElementException("Entity to merge not found"));

        if (primaryId.equals(mergeFromId)) {
            throw new IllegalArgumentException("Cannot merge an entity with itself");
        }

        tripleRepository.updateSubjectEntity(mergeFromId, primaryId);
        tripleRepository.updateObjectEntity(mergeFromId, primaryId);

        List<KgEntityAlias> mergeFromAliases = aliasRepository.findByEntityId(mergeFromId);
        for (KgEntityAlias alias : mergeFromAliases) {
            if (aliasRepository.findByEntityIdAndAlias(primaryId, alias.getAlias()).isEmpty()) {
                alias.setEntity(primary);
                aliasRepository.save(alias);
            } else {
                aliasRepository.delete(alias);
            }
        }

        if (aliasRepository.findByEntityIdAndAlias(primaryId, mergeFrom.getCanonicalName()).isEmpty()) {
            KgEntityAlias nameAlias = KgEntityAlias.builder()
                    .entity(primary)
                    .alias(mergeFrom.getCanonicalName())
                    .source("MERGE")
                    .confidence(1.0f)
                    .build();
            aliasRepository.save(nameAlias);
        }

        List<KgEntityContactLink> mergeFromLinks = contactLinkRepository.findByEntityId(mergeFromId);
        for (KgEntityContactLink link : mergeFromLinks) {
            contactLinkRepository.delete(link);
        }

        entityRepository.delete(mergeFrom);

        return toDto(primary);
    }

    @Cacheable(value = "kgStats", key = "#user.id")
    @Transactional(readOnly = true)
    public Map<String, Long> getStats(User user) {
        long entities = entityRepository.countByUser(user);
        long triples = tripleRepository.countByUser(user);
        Map<String, Long> stats = new HashMap<>();
        stats.put("entities", entities);
        stats.put("triples", triples);
        return stats;
    }

    private void addEntityIfNew(KgEntity entity, Map<Long, KgEntity> entityMap,
                                Set<Long> visited, Queue<Long> frontier, int maxNodes) {
        if (entity != null && !visited.contains(entity.getId()) && entityMap.size() < maxNodes) {
            visited.add(entity.getId());
            entityMap.put(entity.getId(), entity);
            frontier.add(entity.getId());
        }
    }

    private KgEntityDto toDto(KgEntity entity) {
        List<String> aliases = aliasRepository.findByEntityId(entity.getId()).stream()
                .map(KgEntityAlias::getAlias)
                .toList();
        Long linkedContactId = contactLinkRepository.findByEntityId(entity.getId()).stream()
                .findFirst()
                .map(link -> link.getContact().getId())
                .orElse(null);
        return new KgEntityDto(
                entity.getId(),
                entity.getCanonicalName(),
                entity.getEntityType(),
                entity.getDescription(),
                entity.getMetadata(),
                aliases,
                linkedContactId,
                entity.getCreatedAt());
    }

    private KgTripleDto toTripleDto(KgTriple triple) {
        return new KgTripleDto(
                triple.getId(),
                triple.getSubject().getId(),
                triple.getSubject().getCanonicalName(),
                triple.getSubject().getEntityType(),
                triple.getPredicate(),
                triple.getObject() != null ? triple.getObject().getId() : null,
                triple.getObject() != null ? triple.getObject().getCanonicalName() : null,
                triple.getObject() != null ? triple.getObject().getEntityType() : null,
                triple.getObjectValue(),
                triple.getConfidence(),
                triple.getSourceMessage() != null ? triple.getSourceMessage().getId() : null,
                triple.getExtractedText(),
                triple.getIsVerified(),
                triple.getIsNegated(),
                triple.getCreatedAt());
    }
}
