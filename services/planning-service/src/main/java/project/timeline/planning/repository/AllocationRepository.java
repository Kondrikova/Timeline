package project.timeline.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.planning.domain.Allocation;

import java.util.List;
import java.util.UUID;

public interface AllocationRepository extends JpaRepository<Allocation, UUID> {

	List<Allocation> findAllByPlanVersionId(UUID planVersionId);

	List<Allocation> findAllByPlanVersionIdAndTaskId(UUID planVersionId, UUID taskId);

	List<Allocation> findAllByPlanVersionIdAndSprintId(UUID planVersionId, UUID sprintId);

	void deleteAllByTaskId(UUID taskId);
}
