package de.devboard.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "corpus_state")
public class CorpusState {

    @Id
    private Integer id;

    @Column(name = "source_path", columnDefinition = "TEXT")
    private String sourcePath;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "last_ingested_at")
    private OffsetDateTime lastIngestedAt;

    @Column(name = "last_ingested_by_user_id")
    private UUID lastIngestedByUserId;

    public CorpusState() {}

    public Integer getId() { return id; }
    public String getSourcePath() { return sourcePath; }
    public int getFileCount() { return fileCount; }
    public int getChunkCount() { return chunkCount; }
    public OffsetDateTime getLastIngestedAt() { return lastIngestedAt; }
    public UUID getLastIngestedByUserId() { return lastIngestedByUserId; }

    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public void setLastIngestedAt(OffsetDateTime lastIngestedAt) { this.lastIngestedAt = lastIngestedAt; }
    public void setLastIngestedByUserId(UUID lastIngestedByUserId) { this.lastIngestedByUserId = lastIngestedByUserId; }
}
