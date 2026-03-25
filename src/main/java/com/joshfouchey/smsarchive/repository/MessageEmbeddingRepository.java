package com.joshfouchey.smsarchive.repository;

import com.joshfouchey.smsarchive.model.MessageEmbedding;
import com.joshfouchey.smsarchive.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageEmbeddingRepository extends JpaRepository<MessageEmbedding, Long> {

    @Query(value = """
            SELECT m.*, (1 - (me.embedding <=> CAST(:queryVector AS vector))) AS similarity
            FROM messages m
            JOIN message_embeddings me ON me.message_id = m.id
            WHERE me.user_id = :userId
            ORDER BY me.embedding <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findSimilarMessages(
            @Param("userId") UUID userId,
            @Param("queryVector") String queryVector,
            @Param("topK") int topK);

    @Query(value = """
            SELECT m.*, (1 - (me.embedding <=> CAST(:queryVector AS vector))) AS similarity
            FROM messages m
            JOIN message_embeddings me ON me.message_id = m.id
            WHERE me.user_id = :userId
              AND m.conversation_id = :conversationId
            ORDER BY me.embedding <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findSimilarInConversation(
            @Param("userId") UUID userId,
            @Param("conversationId") Long conversationId,
            @Param("queryVector") String queryVector,
            @Param("topK") int topK);

    @Query(value = """
            SELECT m.*, (1 - (me.embedding <=> CAST(:queryVector AS vector))) AS similarity
            FROM messages m
            JOIN message_embeddings me ON me.message_id = m.id
            WHERE me.user_id = :userId
              AND m.sender_contact_id = :contactId
            ORDER BY me.embedding <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findSimilarByContact(
            @Param("userId") UUID userId,
            @Param("contactId") Long contactId,
            @Param("queryVector") String queryVector,
            @Param("topK") int topK);

    @Query(value = """
            SELECT m.id FROM messages m
            WHERE m.user_id = :userId
              AND m.body IS NOT NULL
              AND m.body != ''
              AND NOT EXISTS (
                  SELECT 1 FROM message_embeddings me
                  WHERE me.message_id = m.id AND me.model_name = :modelName
              )
            ORDER BY m.id
            """, nativeQuery = true)
    List<Long> findUnembeddedMessageIds(
            @Param("userId") UUID userId,
            @Param("modelName") String modelName);

    long countByUserAndModelName(User user, String modelName);

    boolean existsByMessageId(Long messageId);

    @Modifying
    @Query(value = """
            INSERT INTO message_embeddings (message_id, user_id, embedding, model_name, embedding_text, created_at)
            VALUES (:messageId, :userId, CAST(:embedding AS vector), :modelName, :embeddingText, now())
            ON CONFLICT (message_id, model_name, chunk_index) DO UPDATE
            SET embedding = CAST(:embedding AS vector),
                embedding_text = :embeddingText,
                created_at = now()
            """, nativeQuery = true)
    void upsertEmbedding(
            @Param("messageId") Long messageId,
            @Param("userId") UUID userId,
            @Param("embedding") String embedding,
            @Param("modelName") String modelName,
            @Param("embeddingText") String embeddingText);

    @Modifying
    @Query(value = """
            DELETE FROM message_embeddings WHERE user_id = :userId AND model_name = :modelName
            """, nativeQuery = true)
    void deleteAllByUserAndModel(
            @Param("userId") UUID userId,
            @Param("modelName") String modelName);

    @Modifying
    @Query(value = """
            INSERT INTO message_embeddings (message_id, user_id, embedding, model_name, created_at)
            VALUES (:messageId, :userId, CAST(:embedding AS vector), :modelName, now())
            ON CONFLICT (message_id, model_name, chunk_index) DO NOTHING
            """, nativeQuery = true)
    void insertEmbedding(
            @Param("messageId") Long messageId,
            @Param("userId") UUID userId,
            @Param("embedding") String embedding,
            @Param("modelName") String modelName);
}
