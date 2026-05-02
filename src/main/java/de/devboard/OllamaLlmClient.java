package de.devboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OllamaLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmClient.class);
    private static final int EMBED_BATCH_SIZE = 32;

    private final RestClient restClient;
    private final String embedModel;
    private final String chatModel;

    public OllamaLlmClient(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.embed-model}") String embedModel,
            @Value("${ollama.chat-model}") String chatModel) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.embedModel = embedModel;
        this.chatModel = chatModel;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += EMBED_BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + EMBED_BATCH_SIZE, texts.size()));
            EmbedResponse response = restClient.post()
                    .uri("/api/embed")
                    .body(Map.of("model", embedModel, "input", batch))
                    .retrieve()
                    .body(EmbedResponse.class);
            if (response != null && response.embeddings() != null) {
                result.addAll(response.embeddings());
            }
        }
        return result;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        );
        ChatResponse response = restClient.post()
                .uri("/api/chat")
                .body(Map.of(
                        "model", chatModel,
                        "messages", messages,
                        "stream", false,
                        "options", Map.of("temperature", 0.2)
                ))
                .retrieve()
                .body(ChatResponse.class);
        if (response != null && response.message() != null) {
            return response.message().get("content");
        }
        return null;
    }

    record EmbedResponse(List<float[]> embeddings) {}
    record ChatResponse(Map<String, String> message) {}
}
