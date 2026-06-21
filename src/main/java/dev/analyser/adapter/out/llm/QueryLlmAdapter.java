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
 * Query LLM adapter — uses the Quarkus-configured ChatModel for on-demand tool calls.
 * In a multi-model setup, this would inject a separate ChatModel qualified for query use.
 * Currently shares the same model — configured via application.properties.
 */
@ApplicationScoped
@Named("queryLlm")
public class QueryLlmAdapter implements LlmPort {

    private final ChatModel model;

    public QueryLlmAdapter(ChatModel model) {
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
