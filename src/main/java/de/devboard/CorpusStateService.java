package de.devboard;

import de.devboard.domain.CorpusState;
import de.devboard.domain.CorpusStateRepository;
import de.devboard.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class CorpusStateService {

    private final CorpusStateRepository corpusStateRepository;
    private final UserRepository userRepository;

    public CorpusStateService(CorpusStateRepository corpusStateRepository, UserRepository userRepository) {
        this.corpusStateRepository = corpusStateRepository;
        this.userRepository = userRepository;
    }

    public Optional<CorpusState> getCorpusState() {
        return corpusStateRepository.findById(1);
    }

    /** Returns the email of the user who last ingested, or null if unknown. */
    public String getLastIngestedByEmail(CorpusState state) {
        if (state.getLastIngestedByUserId() == null) return null;
        return userRepository.findById(state.getLastIngestedByUserId())
                .map(u -> u.getEmail())
                .orElse(null);
    }

    @Transactional
    public void updateAfterIngest(String sourcePath, int fileCount, int chunkCount, UUID userId) {
        CorpusState state = corpusStateRepository.findById(1)
                .orElseThrow(() -> new IllegalStateException("corpus_state seed row missing"));
        state.setSourcePath(sourcePath);
        state.setFileCount(fileCount);
        state.setChunkCount(chunkCount);
        state.setLastIngestedAt(OffsetDateTime.now());
        state.setLastIngestedByUserId(userId);
        corpusStateRepository.save(state);
    }
}
