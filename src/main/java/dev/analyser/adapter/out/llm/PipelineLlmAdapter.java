package dev.analyser.adapter.out.llm;

import dev.analyser.application.port.out.LlmPort;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.util.List;

/**
 * Pipeline LLM adapter — uses the Quarkus-configured ChatModel for bulk analysis.
 */
@ApplicationScoped
@Named("pipelineLlm")
public class PipelineLlmAdapter implements LlmPort {

    private final ChatModel model;

    public PipelineLlmAdapter(ChatModel model) {
        this.model = model;
    }

    @Override
    public String prompt(String systemPrompt, String userPrompt) {
        var messages = List.of(
                (dev.langchain4j.data.message.ChatMessage) SystemMessage.from(systemPrompt),
                (dev.langchain4j.data.message.ChatMessage) UserMessage.from(userPrompt));
        var response = model.chat(ChatRequest.builder().messages(messages).build());
        return response.aiMessage().text();
    }
}
