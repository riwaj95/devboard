package de.devboard;

import de.devboard.domain.Chunk;
import de.devboard.domain.ChunkRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RetrievalService {

    private static final int RRF_K = 60;
    private static final int TOP_N = 6;

    private final LlmClient llmClient;
    private final ChunkRepository chunkRepository;

    public RetrievalService(LlmClient llmClient, ChunkRepository chunkRepository) {
        this.llmClient = llmClient;
        this.chunkRepository = chunkRepository;
    }

    public List<Chunk> retrieve(String question) {
        float[] queryVec = llmClient.embed(List.of(question)).get(0);
        String qvec = toVectorLiteral(queryVec);

        List<UUID> vectorIds = chunkRepository.findTopByVector(qvec);
        List<UUID> ftsIds    = chunkRepository.findTopByFullText(question);

        Map<UUID, Double> scores = new LinkedHashMap<>();
        addRrf(scores, vectorIds);
        addRrf(scores, ftsIds);

        List<UUID> topIds = scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(TOP_N)
                .map(Map.Entry::getKey)
                .toList();

        Map<UUID, Chunk> byId = new LinkedHashMap<>();
        chunkRepository.findAllById(topIds).forEach(c -> byId.put(c.getId(), c));
        return topIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private void addRrf(Map<UUID, Double> scores, List<UUID> ids) {
        for (int i = 0; i < ids.size(); i++) {
            scores.merge(ids.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
