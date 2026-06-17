package com.example.aics.service;

import com.example.aics.config.RagProperties;
import com.example.aics.dto.SourceChunk;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class VectorSearchService {

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate vectorJdbcTemplate;
    private final RagProperties ragProperties;

    public VectorSearchService(EmbeddingModel embeddingModel,
                               JdbcTemplate vectorJdbcTemplate,
                               RagProperties ragProperties) {
        this.embeddingModel = embeddingModel;
        this.vectorJdbcTemplate = vectorJdbcTemplate;
        this.ragProperties = ragProperties;
    }

    public void upsertChunk(Long chunkId, Long knowledgeBaseId, Long documentId, String documentName, String content) {
        float[] embedding = embeddingModel.embed(content);
        vectorJdbcTemplate.update("""
                        insert into rag_chunk_vector
                            (chunk_id, knowledge_base_id, document_id, document_name, content, embedding)
                        values (?, ?, ?, ?, ?, ?::vector)
                        on conflict (chunk_id) do update set
                            knowledge_base_id = excluded.knowledge_base_id,
                            document_id = excluded.document_id,
                            document_name = excluded.document_name,
                            content = excluded.content,
                            embedding = excluded.embedding
                        """,
                chunkId, knowledgeBaseId, documentId, documentName, content, toVectorLiteral(embedding));
    }

    public void deleteByDocumentId(Long documentId) {
        vectorJdbcTemplate.update("delete from rag_chunk_vector where document_id = ?", documentId);
    }

    public List<SourceChunk> search(Long knowledgeBaseId, String question) {
        float[] embedding = embeddingModel.embed(question);
        String vector = toVectorLiteral(embedding);
        return vectorJdbcTemplate.query("""
                        select chunk_id,
                               document_id,
                               document_name,
                               content,
                               1 - (embedding <=> ?::vector) as score
                          from rag_chunk_vector
                         where knowledge_base_id = ?
                         order by embedding <=> ?::vector
                         limit ?
                        """,
                ps -> {
                    ps.setString(1, vector);
                    ps.setLong(2, knowledgeBaseId);
                    ps.setString(3, vector);
                    ps.setInt(4, ragProperties.getTopK());
                },
                (rs, rowNum) -> {
                    SourceChunk source = new SourceChunk();
                    source.setChunkId(rs.getLong("chunk_id"));
                    source.setDocumentId(rs.getLong("document_id"));
                    source.setDocumentName(rs.getString("document_name"));
                    source.setContent(rs.getString("content"));
                    source.setScore(rs.getDouble("score"));
                    return source;
                }).stream()
                .filter(source -> source.getScore() >= ragProperties.getSimilarityThreshold())
                .collect(Collectors.toList());
    }

    private String toVectorLiteral(float[] embedding) {
        String values = new StringBuilder()
                .append("[")
                .append(java.util.stream.IntStream.range(0, embedding.length)
                        .mapToObj(i -> String.format(Locale.US, "%.8f", embedding[i]))
                        .collect(Collectors.joining(",")))
                .append("]")
                .toString();
        return values;
    }
}
