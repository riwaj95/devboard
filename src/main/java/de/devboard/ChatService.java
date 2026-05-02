package de.devboard;

import de.devboard.domain.Chunk;
import de.devboard.domain.Qa;
import de.devboard.domain.QaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            You answer the user's question using ONLY the context below.
            Cite sources with [1], [2], etc., matching the bracketed numbers in the context.
            If the context doesn't contain the answer, say "I don't see that in the docs."
            Do not make up information.
            """;

    private final RetrievalService retrievalService;
    private final LlmClient llmClient;
    private final QaRepository qaRepository;

    public ChatService(RetrievalService retrievalService, LlmClient llmClient, QaRepository qaRepository) {
        this.retrievalService = retrievalService;
        this.llmClient = llmClient;
        this.qaRepository = qaRepository;
    }

    public QaResult ask(String question) {
        List<Chunk> chunks = retrievalService.retrieve(question);

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            context.append("[").append(i + 1).append("] from ").append(c.getSourceName()).append(":\n");
            context.append(c.getContent()).append("\n\n");
        }

        String answer = llmClient.chat(SYSTEM_PROMPT, "Context:\n" + context + "\nQuestion: " + question);
        if (answer == null) {
            answer = "Something went wrong communicating with the model.";
        }

        UUID[] chunkIds = chunks.stream().map(Chunk::getId).toArray(UUID[]::new);
        Qa qa = new Qa(UUID.randomUUID(), question, answer, chunkIds);
        qaRepository.save(qa);

        return new QaResult(qa.getId(), answer, chunks);
    }

    public record QaResult(UUID qaId, String answer, List<Chunk> chunks) {}
}
