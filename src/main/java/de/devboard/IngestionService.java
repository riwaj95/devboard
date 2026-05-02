package de.devboard;

import de.devboard.domain.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final long MAX_FILE_BYTES = 200 * 1024;
    private static final int MAX_WORDS_PER_CHUNK = 600;

    private final LlmClient llmClient;
    private final ChunkRepository chunkRepository;
    private final JdbcTemplate jdbc;

    public IngestionService(LlmClient llmClient, ChunkRepository chunkRepository, JdbcTemplate jdbc) {
        this.llmClient = llmClient;
        this.chunkRepository = chunkRepository;
        this.jdbc = jdbc;
    }

    @Transactional
    public IngestResult ingest(String folderPath) {
        Path root = Path.of(folderPath);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + folderPath);
        }

        chunkRepository.deleteAllNative();

        List<String> contents = new ArrayList<>();
        List<String> sourcePaths = new ArrayList<>();
        List<String> sourceNames = new ArrayList<>();
        List<Integer> chunkIndexes = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> mdFiles = walk
                    .filter(p -> p.toString().endsWith(".md"))
                    .filter(Files::isRegularFile)
                    .toList();

            for (Path file : mdFiles) {
                if (Files.size(file) > MAX_FILE_BYTES) {
                    log.warn("Skipping large file: {}", file);
                    continue;
                }
                String text = Files.readString(file);
                List<String> chunks = chunkText(text);
                for (int i = 0; i < chunks.size(); i++) {
                    contents.add(chunks.get(i));
                    sourcePaths.add(file.toAbsolutePath().toString());
                    sourceNames.add(file.getFileName().toString());
                    chunkIndexes.add(i);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read folder: " + e.getMessage(), e);
        }

        if (contents.isEmpty()) {
            return new IngestResult(0, 0);
        }

        List<float[]> embeddings = llmClient.embed(contents);

        Timestamp now = Timestamp.from(Instant.now());
        List<UUID> ids = new ArrayList<>(contents.size());
        for (int i = 0; i < contents.size(); i++) ids.add(UUID.randomUUID());

        jdbc.batchUpdate(
                "INSERT INTO chunks (id, source_path, source_name, chunk_index, content, embedding, created_at) " +
                "VALUES (?, ?, ?, ?, ?, CAST(? AS vector), ?)",
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setObject(1, ids.get(i));
                        ps.setString(2, sourcePaths.get(i));
                        ps.setString(3, sourceNames.get(i));
                        ps.setInt(4, chunkIndexes.get(i));
                        ps.setString(5, contents.get(i));
                        ps.setString(6, toVectorLiteral(embeddings.get(i)));
                        ps.setTimestamp(7, now);
                    }
                    @Override
                    public int getBatchSize() { return contents.size(); }
                }
        );

        long fileCount = sourcePaths.stream().distinct().count();
        return new IngestResult(contents.size(), (int) fileCount);
    }

    List<String> chunkText(String text) {
        String[] paragraphs = text.split("\n\n+");
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentWords = 0;
        String lastSentence = null;

        for (String para : paragraphs) {
            String trimmed = para.strip();
            if (trimmed.isEmpty()) continue;

            int words = countWords(trimmed);
            if (currentWords + words > MAX_WORDS_PER_CHUNK && !current.isEmpty()) {
                String chunkText = String.join("\n\n", current);
                if (!chunkText.isBlank()) chunks.add(chunkText);
                current.clear();
                currentWords = 0;
                if (lastSentence != null) {
                    current.add(lastSentence);
                    currentWords = countWords(lastSentence);
                }
            }
            current.add(trimmed);
            currentWords += words;
            lastSentence = lastSentenceOf(trimmed);
        }

        if (!current.isEmpty()) {
            String chunkText = String.join("\n\n", current);
            if (!chunkText.isBlank()) chunks.add(chunkText);
        }
        return chunks;
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private String lastSentenceOf(String text) {
        String[] sentences = text.split("(?<=[.!?])\\s+");
        return sentences[sentences.length - 1].strip();
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

    public record IngestResult(int chunkCount, int fileCount) {}
}
