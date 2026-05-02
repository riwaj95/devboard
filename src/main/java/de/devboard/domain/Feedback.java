package de.devboard.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    private UUID id;

    @Column(name = "qa_id", nullable = false)
    private UUID qaId;

    @Column(name = "thumbs_up", nullable = false)
    private boolean thumbsUp;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Feedback() {}

    public Feedback(UUID id, UUID qaId, boolean thumbsUp) {
        this.id = id;
        this.qaId = qaId;
        this.thumbsUp = thumbsUp;
    }

    public UUID getId() { return id; }
    public UUID getQaId() { return qaId; }
    public boolean isThumbsUp() { return thumbsUp; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
