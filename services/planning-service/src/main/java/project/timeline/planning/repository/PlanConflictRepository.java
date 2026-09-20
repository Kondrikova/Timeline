package project.timeline.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.planning.domain.PlanConflict;

import java.util.List;
import java.util.UUID;

public interface PlanConflictRepository extends JpaRepository<PlanConflict, UUID> {

	List<PlanConflict> findAllByPlanVersionIdOrderBySeverityAscTypeAsc(UUID planVersionId);

	void deleteAllByPlanVersionId(UUID planVersionId);
}
