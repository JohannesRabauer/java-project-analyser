package dev.analyser.adapter.out.persistence;

import static generated.jooq.tables.RagChunks.RAG_CHUNKS;

import dev.analyser.domain.model.RagChunk;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

@ApplicationScoped
public class RagRepository {

    private final DataSource dataSource;

    public RagRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void saveAll(List<RagChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        var dsl = dsl();
        var query = dsl.insertInto(RAG_CHUNKS)
                .columns(RAG_CHUNKS.ID, RAG_CHUNKS.JOB_ID, RAG_CHUNKS.PHASE_ID, RAG_CHUNKS.CONTENT, RAG_CHUNKS.EMBEDDING);

        for (var chunk : chunks) {
            Double[] embeddingDouble = new Double[chunk.embedding().length];
            for (int i = 0; i < chunk.embedding().length; i++) {
                embeddingDouble[i] = chunk.embedding()[i];
            }
            query = query.values(chunk.id(), chunk.jobId(), chunk.phaseId(), chunk.content(), embeddingDouble);
        }

        query.execute();
    }

    public List<SearchResult> search(UUID jobId, double[] queryEmbedding, int limit) {
        var dsl = dsl();
        var records = dsl.selectFrom(RAG_CHUNKS)
                .where(RAG_CHUNKS.JOB_ID.eq(jobId))
                .fetch();

        List<SearchResult> results = new ArrayList<>();
        for (var rec : records) {
            Double[] embRecord = rec.getEmbedding();
            double[] emb = new double[embRecord.length];
            for (int i = 0; i < embRecord.length; i++) {
                emb[i] = embRecord[i];
            }

            double similarity = calculateCosineSimilarity(queryEmbedding, emb);
            results.add(new SearchResult(
                    new RagChunk(rec.getId(), rec.getJobId(), rec.getPhaseId(), rec.getContent(), emb),
                    similarity
            ));
        }

        // Sort by similarity descending
        results.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));

        if (results.size() > limit) {
            return results.subList(0, limit);
        }
        return results;
    }

    private double calculateCosineSimilarity(double[] vectorA, double[] vectorB) {
        if (vectorA.length != vectorB.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private DSLContext dsl() {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public record SearchResult(RagChunk chunk, double similarity) {}
}
