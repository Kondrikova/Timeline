package project.timeline.backlog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.timeline.backlog.domain.TaskComment;

import java.util.List;
import java.util.UUID;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

	List<TaskComment> findAllByTaskIdOrderByCreatedAtAsc(UUID taskId);

	long countByTaskId(UUID taskId);

	void deleteAllByTaskId(UUID taskId);
}
