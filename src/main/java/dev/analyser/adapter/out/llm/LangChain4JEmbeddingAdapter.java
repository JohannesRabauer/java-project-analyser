package dev.analyser.adapter.out.llm;

import dev.analyser.application.port.out.EmbeddingPort;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Embedding adapter using LangChain4J's EmbeddingModel (Ollama or OpenAI, auto-configured by Quarkus).
 */
@ApplicationScoped
public class LangChain4JEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel model;

    public LangChain4JEmbeddingAdapter(EmbeddingModel model) {
        this.model = model;
    }

    @Override
    public float[] embed(String text) {
        Response<Embedding> response = model.embed(TextSegment.from(text));
        return response.content().vector();
    }
}
