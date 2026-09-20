package project.timeline.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.planning.replica.RefTaskEstimate;

import java.util.List;
import java.util.UUID;

public interface RefTaskEstimateRepository extends JpaRepository<RefTaskEstimate, UUID> {

	List<RefTaskEstimate> findAllByTaskId(UUID taskId);

	void deleteAllByTaskId(UUID taskId);
}
