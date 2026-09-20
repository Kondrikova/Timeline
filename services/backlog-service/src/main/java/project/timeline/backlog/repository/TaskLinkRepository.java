package project.timeline.backlog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.timeline.backlog.domain.TaskLink;

import java.util.List;
import java.util.UUID;

public interface TaskLinkRepository extends JpaRepository<TaskLink, UUID> {

	@Query("SELECT l FROM TaskLink l WHERE l.fromTaskId = :taskId OR l.toTaskId = :taskId")
	List<TaskLink> findAllTouching(@Param("taskId") UUID taskId);
}
