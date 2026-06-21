package dev.analyser.application.port.out;

/**
 * Outbound port for LLM calls. Implementations select the model based on context (pipeline vs query).
 */
public interface LlmPort {

    String prompt(String systemPrompt, String userPrompt);
}
