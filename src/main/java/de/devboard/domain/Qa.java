package de.devboard.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "qa")
public class Qa {

    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "source_chunk_ids", nullable = false, columnDefinition = "UUID[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private UUID[] sourceChunkIds;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Qa() {}

    public Qa(UUID id, String question, String answer, UUID[] sourceChunkIds) {
        this.id = id;
        this.question = question;
        this.answer = answer;
        this.sourceChunkIds = sourceChunkIds;
    }

    public UUID getId() { return id; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public UUID[] getSourceChunkIds() { return sourceChunkIds; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
