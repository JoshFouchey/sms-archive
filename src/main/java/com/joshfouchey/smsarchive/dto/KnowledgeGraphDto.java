package com.joshfouchey.smsarchive.dto;

import java.util.List;

public record KnowledgeGraphDto(
    List<GraphNode> nodes,
    List<GraphEdge> edges
) {
    public record GraphNode(
        String id,
        String label,
        String type,
        Long linkedContactId
    ) {}

    public record GraphEdge(
        String source,
        String target,
        String label,
        Float confidence
    ) {}
}
