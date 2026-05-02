package de.devboard;

import java.util.List;

public interface LlmClient {
    List<float[]> embed(List<String> texts);
    String chat(String systemPrompt, String userMessage);
}
