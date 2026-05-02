package de.devboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<Chunk, UUID> {

    @Query(value = """
            SELECT id FROM chunks
            ORDER BY embedding <=> CAST(:qvec AS vector)
            LIMIT 20
            """, nativeQuery = true)
    List<UUID> findTopByVector(@Param("qvec") String qvec);

    @Query(value = """
            SELECT id FROM chunks
            WHERE to_tsvector('english', content) @@ plainto_tsquery('english', :q)
            LIMIT 20
            """, nativeQuery = true)
    List<UUID> findTopByFullText(@Param("q") String q);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM chunks", nativeQuery = true)
    void deleteAllNative();
}
