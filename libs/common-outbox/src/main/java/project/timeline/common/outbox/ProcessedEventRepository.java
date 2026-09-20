package project.timeline.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {

	boolean existsByEventIdAndConsumer(UUID eventId, String consumer);
}
