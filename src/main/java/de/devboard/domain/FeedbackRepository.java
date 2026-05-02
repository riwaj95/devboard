package de.devboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
}
