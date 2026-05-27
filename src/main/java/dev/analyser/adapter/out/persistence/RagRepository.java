package dev.analyser.adapter.out.persistence;

import dev.analyser.domain.model.RagChunk;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class RagRepository {

    static final int DEFAULT_EMBEDDING_DIMENSION = 384;
    static final double DEFAULT_SIMILARITY_THRESHOLD = 0.75d;

    private static final String INSERT_SQL = """
            INSERT INTO rag_chunks (id, job_id, phase_id, content, embedding)
            VALUES (?, ?, ?, ?, CAST(? AS vector))
            """;

    private static final String SEARCH_SQL = """
            SELECT id,
                   job_id,
                   phase_id,
                   content,
                   embedding::text AS embedding_text,
                   similarity
            FROM (
                SELECT id,
                       job_id,
                       phase_id,
                       content,
                       embedding,
                       1 - (embedding <=> CAST(? AS vector)) AS similarity
                FROM rag_chunks
                WHERE job_id = ?
            ) ranked_chunks
            WHERE similarity >= ?
            ORDER BY similarity DESC, id ASC
            LIMIT ?
            """;

    private final DataSource dataSource;
    private final int embeddingDimension;
    private final double similarityThreshold;

    @Inject
    public RagRepository(
            DataSource dataSource,
            @ConfigProperty(name = "rag.embedding.dimension", defaultValue = "384") int embeddingDimension,
            @ConfigProperty(name = "rag.search.similarity-threshold", defaultValue = "0.75") double similarityThreshold) {
        this.dataSource = dataSource;
        this.embeddingDimension = embeddingDimension;
        this.similarityThreshold = clampSimilarityThreshold(similarityThreshold);
    }

    public RagRepository(DataSource dataSource) {
        this(dataSource, DEFAULT_EMBEDDING_DIMENSION, DEFAULT_SIMILARITY_THRESHOLD);
    }

    public void saveAll(List<RagChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(INSERT_SQL)) {
            for (var chunk : chunks) {
                validateEmbedding(chunk.embedding());
                statement.setObject(1, chunk.id());
                statement.setObject(2, chunk.jobId());
                statement.setInt(3, chunk.phaseId());
                statement.setString(4, chunk.content());
                statement.setString(5, toVectorLiteral(chunk.embedding()));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist RAG chunks", e);
        }
    }

    public List<SearchResult> search(UUID jobId, double[] queryEmbedding, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        validateEmbedding(queryEmbedding);
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        List<SearchResult> results = new ArrayList<>();

        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(SEARCH_SQL)) {
            statement.setString(1, vectorLiteral);
            statement.setObject(2, jobId);
            statement.setDouble(3, similarityThreshold);
            statement.setInt(4, limit);

            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(new SearchResult(
                            new RagChunk(
                                    resultSet.getObject("id", UUID.class),
                                    resultSet.getObject("job_id", UUID.class),
                                    resultSet.getInt("phase_id"),
                                    resultSet.getString("content"),
                                    parseVector(resultSet.getString("embedding_text"))),
                            resultSet.getDouble("similarity")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to search RAG chunks", e);
        }

        return List.copyOf(results);
    }

    private void validateEmbedding(double[] embedding) {
        if (embedding.length != embeddingDimension) {
            throw new IllegalArgumentException("Expected embedding length " + embeddingDimension + " but was " + embedding.length);
        }
    }

    private String toVectorLiteral(double[] embedding) {
        return java.util.Arrays.stream(embedding)
                .mapToObj(Double::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private double[] parseVector(String storedVector) {
        String trimmed = storedVector.trim();
        if (trimmed.length() < 2) {
            return new double[0];
        }

        String body = trimmed.substring(1, trimmed.length() - 1);
        if (body.isBlank()) {
            return new double[0];
        }

        String[] values = body.split(",");
        double[] embedding = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            embedding[i] = Double.parseDouble(values[i]);
        }
        return embedding;
    }

    private double clampSimilarityThreshold(double configuredThreshold) {
        return Math.max(0.0d, Math.min(1.0d, configuredThreshold));
    }

    public record SearchResult(RagChunk chunk, double similarity) {}
}
