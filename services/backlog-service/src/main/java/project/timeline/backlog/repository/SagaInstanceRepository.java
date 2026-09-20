package project.timeline.backlog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.backlog.domain.SagaInstance;
import project.timeline.backlog.domain.SagaState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {

	List<SagaInstance> findAllByStateAndStartedAtBefore(SagaState state, Instant before);

	long countByStateAndStartedAtBefore(SagaState state, Instant before);
}
