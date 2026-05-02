package de.devboard.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chunks")
public class Chunk {

    @Id
    private UUID id;

    @Column(name = "source_path", nullable = false)
    private String sourcePath;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "vector(768)")
    private String embedding;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Chunk() {}

    public Chunk(UUID id, String sourcePath, String sourceName, int chunkIndex, String content, String embedding) {
        this.id = id;
        this.sourcePath = sourcePath;
        this.sourceName = sourceName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
    }

    public UUID getId() { return id; }
    public String getSourcePath() { return sourcePath; }
    public String getSourceName() { return sourceName; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public String getEmbedding() { return embedding; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
