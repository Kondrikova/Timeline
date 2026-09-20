package project.timeline.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<OutboxRecord, Long> {

	/**
	 * {@code SKIP LOCKED} позволяет запускать несколько экземпляров сервиса: каждый
	 * забирает свою пачку записей, не блокируясь на чужих.
	 */
	@Query(value = """
			SELECT * FROM outbox
			 WHERE published_at IS NULL
			 ORDER BY id
			 LIMIT :limit
			 FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OutboxRecord> lockUnpublished(@Param("limit") int limit);

	long countByPublishedAtIsNull();

	@Query("SELECT MIN(o.createdAt) FROM OutboxRecord o WHERE o.publishedAt IS NULL")
	Optional<Instant> oldestUnpublishedCreatedAt();
}
