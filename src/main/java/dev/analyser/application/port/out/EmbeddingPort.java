package dev.analyser.application.port.out;

/**
 * Outbound port for embedding vector computation.
 */
public interface EmbeddingPort {

    float[] embed(String text);
}
